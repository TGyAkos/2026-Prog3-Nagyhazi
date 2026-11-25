package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.FlowEdge;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.ProductionNode;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.SolverNotice;
import hu.bme.prog3.factcalc.model.TimeUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Facade orchestrating validation, recipe resolution, linear flow solving,
 * byproduct analysis, and MAX_OUTPUT scaling (plus delegating to AutoOptimizer when autoMode is enabled).
 */
public class ProductionSolver {

    private static final double EPS = LinearSystemSolver.EPS;

    private final RecipeResolver recipeResolver = new RecipeResolver();
    private final ByproductAnalyzer byproductAnalyzer = new ByproductAnalyzer();
    private final AutoOptimizer autoOptimizer = new AutoOptimizer(this);

    /**
     * Validates the configuration and solves the production plan for the given catalog.
     * Delegates to {@link AutoOptimizer} when {@code MAX_OUTPUT} and auto mode are both active.
     *
     * @param catalog  the active factory catalog containing items, machines, and recipes
     * @param config   the production plan configuration (mode, targets, inputs, overrides, recycling)
     * @param callback interactive callback invoked to resolve recipe conflicts or alternatives
     * @return the computed production plan result containing nodes, edges, rates, and notices
     * @throws SolverException if the catalog/configuration is invalid or no valid plan can be solved
     */
    public ProductionPlanResult solve(Catalog catalog, ProductionPlanConfig config, RecipeResolutionCallback callback) {
        if (catalog == null) {
            throw new SolverException("No catalog is loaded.");
        }
        if (config == null) {
            throw new SolverException("Plan configuration is missing.");
        }
        validateConfig(catalog, config);

        if (config.getMode() == CalculationMode.MAX_OUTPUT && config.isAutoMode()) {
            return autoOptimizer.optimize(catalog, config);
        }

        Map<UUID, Double> userInputCapsPerSec = extractUserInputs(catalog, config);
        Map<UUID, Double> targetDemandPerSec = extractTargets(catalog, config);

        RecipeResolver.ResolvedGraph resolved = recipeResolver.resolve(
                catalog,
                config.getMode(),
                targetDemandPerSec.keySet(),
                userInputCapsPerSec.keySet(),
                config.getRecipeOverrides(),
                callback
        );

        return solveResolvedGraph(catalog, config, resolved, userInputCapsPerSec, targetDemandPerSec);
    }

    /**
     * Solves a pre-resolved recipe dependency graph under either {@code TARGET_OUTPUT} or {@code MAX_OUTPUT} mode.
     * In {@code MAX_OUTPUT} mode, computes unit target consumption and scales up to the tightest user input bottleneck.
     *
     * @param catalog             the active factory catalog
     * @param config              the production plan configuration
     * @param resolved            the resolved acyclic/cyclic recipe graph and leaf item set
     * @param userInputCapsPerSec map of user-supplied item IDs to maximum input rates in items/sec
     * @param targetDemandPerSec  map of target item IDs to target rates in items/sec
     * @return the solved production plan result with bottleneck flags updated in {@code MAX_OUTPUT} mode
     * @throws SolverException if no user input is consumed in {@code MAX_OUTPUT} mode or the linear system fails
     */
    ProductionPlanResult solveResolvedGraph(
            Catalog catalog,
            ProductionPlanConfig config,
            RecipeResolver.ResolvedGraph resolved,
            Map<UUID, Double> userInputCapsPerSec,
            Map<UUID, Double> targetDemandPerSec
    ) {
        if (config.getMode() == CalculationMode.TARGET_OUTPUT) {
            return solveForDemands(catalog, config, resolved, userInputCapsPerSec, targetDemandPerSec);
        } else {
            // MAX_OUTPUT: solve for unit target demand = 1.0 item/sec, then scale by bottleneck
            UUID singleTargetId = targetDemandPerSec.keySet().iterator().next();
            Map<UUID, Double> unitDemand = new LinkedHashMap<>();
            unitDemand.put(singleTargetId, 1.0);

            ProductionPlanResult unitResult = solveForDemands(
                    catalog,
                    config,
                    resolved,
                    userInputCapsPerSec,
                    unitDemand
            );

            // Extract unit external input consumptions for user-supplied inputs
            Map<UUID, Double> unitConsumptions = new LinkedHashMap<>();
            for (ProductionPlanResult.InputSummaryEntry entry : unitResult.getUserInputs()) {
                unitConsumptions.put(entry.getItemId(), entry.getRequiredRatePerSecond());
            }

            double minScale = Double.POSITIVE_INFINITY;
            boolean anyUserInputUsed = false;
            for (Map.Entry<UUID, Double> capEntry : userInputCapsPerSec.entrySet()) {
                UUID inputId = capEntry.getKey();
                double available = capEntry.getValue();
                double unitCons = unitConsumptions.getOrDefault(inputId, 0.0);
                if (unitCons > EPS) {
                    anyUserInputUsed = true;
                    double scale = available / unitCons;
                    if (scale < minScale) {
                        minScale = scale;
                    }
                }
            }

            if (!anyUserInputUsed || !Double.isFinite(minScale)) {
                throw new SolverException("None of the user-provided inputs are used in the production line.");
            }

            Map<UUID, Double> scaledDemands = new LinkedHashMap<>();
            scaledDemands.put(singleTargetId, minScale);

            ProductionPlanResult scaledResult = solveForDemands(
                    catalog,
                    config,
                    resolved,
                    userInputCapsPerSec,
                    scaledDemands
            );

            // Mark bottlenecks in userInputs
            for (ProductionPlanResult.InputSummaryEntry entry : scaledResult.getUserInputs()) {
                if (entry.getSuppliedCapPerSecond() != null && entry.getSuppliedCapPerSecond() > EPS) {
                    if (Math.abs(entry.getRequiredRatePerSecond() - entry.getSuppliedCapPerSecond()) <= 1e-6 * Math.max(1.0, entry.getSuppliedCapPerSecond())) {
                        entry.setBottleneck(true);
                    }
                }
            }
            return scaledResult;
        }
    }

    /**
     * Solves the active linear flow system for fixed target demand rates, filters out zero-rate recipes,
     * analyzes byproduct recycling and surplus, and assembles the {@link ProductionPlanResult}.
     *
     * @param catalog             the active factory catalog
     * @param config              the production plan configuration
     * @param resolved            the resolved recipe dependency graph
     * @param userInputCapsPerSec map of user-supplied item IDs to maximum input caps in items/sec
     * @param targetDemandPerSec  map of target item IDs to required output rates in items/sec
     * @return the solved production plan result
     */
    private ProductionPlanResult solveForDemands(
            Catalog catalog,
            ProductionPlanConfig config,
            RecipeResolver.ResolvedGraph resolved,
            Map<UUID, Double> userInputCapsPerSec,
            Map<UUID, Double> targetDemandPerSec
    ) {
        List<Recipe> workingRecipes = new ArrayList<>(resolved.getActiveRecipes());
        Map<UUID, Recipe> chosenByItem = new LinkedHashMap<>(resolved.getChosenRecipeByItem());
        Set<UUID> recyclableIds = ByproductAnalyzer.findRecyclableByproducts(workingRecipes);

        Map<UUID, Boolean> effectiveRecycled = new LinkedHashMap<>();
        for (UUID bId : recyclableIds) {
            effectiveRecycled.put(bId, Boolean.TRUE.equals(config.getRecycledByproducts().get(bId)));
        }

        // Map each recipe to its primary constrained item
        Map<UUID, UUID> primaryItemForRecipe = new LinkedHashMap<>();
        for (Map.Entry<UUID, Recipe> e : chosenByItem.entrySet()) {
            primaryItemForRecipe.putIfAbsent(e.getValue().getId(), e.getKey());
        }

        Map<UUID, Double> solvedRates = solveActiveSet(
                workingRecipes,
                primaryItemForRecipe,
                targetDemandPerSec,
                effectiveRecycled,
                resolved.getLeafItemIds()
        );

        // Filter out zero-rate recipes (e.g., bypassed by self-sustaining loop or deactivated in active-set)
        List<Recipe> positiveRecipes = new ArrayList<>();
        for (Recipe r : workingRecipes) {
            if (solvedRates.getOrDefault(r.getId(), 0.0) > EPS) {
                positiveRecipes.add(r);
            }
        }

        ByproductAnalyzer.ByproductAnalysisResult byproductAnalysis = byproductAnalyzer.analyze(
                catalog,
                positiveRecipes,
                solvedRates,
                resolved.getLeafItemIds(),
                targetDemandPerSec,
                effectiveRecycled
        );

        return buildResult(
                catalog,
                config,
                positiveRecipes,
                solvedRates,
                chosenByItem,
                userInputCapsPerSec,
                targetDemandPerSec,
                byproductAnalysis
        );
    }

    /**
     * Iteratively solves the square linear production system {@code A * x = b}, pruning recipes with
     * negative execution rates (surplus from co-products or recycled byproducts) and reassigning
     * constrained items when secondary outputs show deficits.
     *
     * @param initialRecipes       candidate recipes selected by graph resolution
     * @param primaryItemForRecipe map from recipe ID to the primary item whose balance row it governs
     * @param targetDemandPerSec   map of target item IDs to external demand rates in items/sec
     * @param effectiveRecycled    map from byproduct item ID to whether recycling is enabled
     * @param leafItemIds          set of leaf item IDs treated as external inputs rather than internally balanced
     * @return map from active recipe ID to non-negative execution rate in executions/sec
     * @throws SolverException if the active set fails to converge or the linear system is singular
     */
    private Map<UUID, Double> solveActiveSet(
            List<Recipe> initialRecipes,
            Map<UUID, UUID> primaryItemForRecipe,
            Map<UUID, Double> targetDemandPerSec,
            Map<UUID, Boolean> effectiveRecycled,
            Set<UUID> leafItemIds
    ) {
        List<Recipe> active = new ArrayList<>(initialRecipes);
        Map<UUID, UUID> recipeToConstrainedItem = new LinkedHashMap<>(primaryItemForRecipe);
        Map<UUID, Double> finalRates = new LinkedHashMap<>();

        for (int iter = 0; iter < Math.max(10, initialRecipes.size() * 4); iter++) {
            int k = active.size();
            if (k == 0) {
                return finalRates;
            }

            double[][] a = new double[k][k];
            double[] b = new double[k];

            for (int row = 0; row < k; row++) {
                Recipe rowRecipe = active.get(row);
                UUID itemId = recipeToConstrainedItem.get(rowRecipe.getId());
                if (itemId == null && !rowRecipe.getOutputs().isEmpty()) {
                    itemId = rowRecipe.getOutputs().get(0).getId();
                    recipeToConstrainedItem.put(rowRecipe.getId(), itemId);
                }

                b[row] = targetDemandPerSec.getOrDefault(itemId, 0.0);
                boolean recycleItem = Boolean.TRUE.equals(effectiveRecycled.get(itemId));

                for (int col = 0; col < k; col++) {
                    Recipe colRecipe = active.get(col);
                    double coeff = colRecipe.getOutputPiece(itemId) - colRecipe.getInputPiece(itemId);
                    if (recycleItem) {
                        coeff += colRecipe.getByproductPiece(itemId);
                    }
                    a[row][col] = coeff;
                }
            }

            double[] x = LinearSystemSolver.solve(a, b);

            // 1. Check for negative rates (surplus from co-product or recycled byproduct -> deactivate)
            int negativeIdx = -1;
            double mostNegative = -EPS;
            for (int i = 0; i < k; i++) {
                if (x[i] < mostNegative) {
                    mostNegative = x[i];
                    negativeIdx = i;
                }
            }
            if (negativeIdx >= 0) {
                active.remove(negativeIdx);
                continue;
            }

            // 2. Check if any non-leaf item (e.g. secondary output of a multi-output recipe) has a deficit
            Map<UUID, Double> currentRates = new LinkedHashMap<>();
            for (int i = 0; i < k; i++) {
                currentRates.put(active.get(i).getId(), Math.max(0.0, x[i]));
            }

            UUID deficitItem = null;
            Recipe deficitProducer = null;
            for (Recipe r : active) {
                for (ItemAmount out : r.getOutputs()) {
                    UUID itemId = out.getId();
                    if (leafItemIds.contains(itemId)) {
                        continue;
                    }
                    double net = computeNetProduction(itemId, active, currentRates, effectiveRecycled);
                    double required = targetDemandPerSec.getOrDefault(itemId, 0.0);
                    if (net < required - 1e-7) {
                        deficitItem = itemId;
                        deficitProducer = r;
                        break;
                    }
                }
                if (deficitItem != null) {
                    break;
                }
            }

            if (deficitItem != null && deficitProducer != null) {
                recipeToConstrainedItem.put(deficitProducer.getId(), deficitItem);
                continue;
            }

            finalRates = currentRates;
            return finalRates;
        }

        throw new SolverException("Solver failed to converge on an active recipe set.");
    }

    /**
     * Computes the total net production rate of a single item across all active recipes,
     * accounting for main outputs, inputs, and optionally recycled byproducts.
     *
     * @param itemId            the item ID to evaluate
     * @param active            the list of active recipes in the current iteration
     * @param rates             map from recipe ID to execution rate in executions/sec
     * @param effectiveRecycled map from byproduct item ID to whether recycling is enabled
     * @return the net production rate in items/sec (negative if net consumed)
     */
    private double computeNetProduction(
            UUID itemId,
            List<Recipe> active,
            Map<UUID, Double> rates,
            Map<UUID, Boolean> effectiveRecycled
    ) {
        double net = 0.0;
        boolean recycled = Boolean.TRUE.equals(effectiveRecycled.get(itemId));
        for (Recipe r : active) {
            double xr = rates.getOrDefault(r.getId(), 0.0);
            net += (r.getOutputPiece(itemId) - r.getInputPiece(itemId)) * xr;
            if (recycled) {
                net += r.getByproductPiece(itemId) * xr;
            }
        }
        return net;
    }

    /**
     * Builds the complete {@link ProductionPlanResult} from positive-rate recipes, machine assignments,
     * byproduct analysis, input/output summaries, capacity notices, and directed flow edges.
     *
     * @param catalog             the active factory catalog
     * @param config              the production plan configuration
     * @param positiveRecipes     the recipes with positive execution rates
     * @param solvedRates         map from recipe ID to solved execution rate in executions/sec
     * @param chosenByItem        map from item ID to the resolved producer recipe
     * @param userInputCapsPerSec map from user-supplied item ID to configured input cap in items/sec
     * @param targetDemandPerSec  map from target item ID to requested output rate in items/sec
     * @param byproductAnalysis   byproduct recycling, surplus, and external input analysis
     * @return the populated {@link ProductionPlanResult}
     */
    private ProductionPlanResult buildResult(
            Catalog catalog,
            ProductionPlanConfig config,
            List<Recipe> positiveRecipes,
            Map<UUID, Double> solvedRates,
            Map<UUID, Recipe> chosenByItem,
            Map<UUID, Double> userInputCapsPerSec,
            Map<UUID, Double> targetDemandPerSec,
            ByproductAnalyzer.ByproductAnalysisResult byproductAnalysis
    ) {
        ProductionPlanResult result = new ProductionPlanResult();
        result.setMode(config.getMode());
        result.setRecyclableByproductIds(byproductAnalysis.getRecyclableByproductIds());
        result.setResolvedRecycling(byproductAnalysis.getEffectiveRecycling());
        result.getNotices().addAll(byproductAnalysis.getNotices());

        for (Map.Entry<UUID, Recipe> e : chosenByItem.entrySet()) {
            result.getResolvedRecipes().put(e.getKey(), e.getValue().getId());
        }

        Map<UUID, ProductionNode> nodesByRecipeId = new LinkedHashMap<>();
        Map<UUID, ProductionPlanResult.MachineTotal> machineTotalsById = new LinkedHashMap<>();

        for (Recipe recipe : positiveRecipes) {
            double xr = solvedRates.getOrDefault(recipe.getId(), 0.0);
            if (xr <= EPS) {
                continue;
            }
            Machine machine = resolveMachineForRecipe(catalog, config, recipe);
            result.getResolvedMachines().put(recipe.getId(), machine.getId());

            double effTime = recipe.getEffectiveTime(machine);
            double exactMachines = xr * effTime;
            int ceilMachines = ProductionNode.computeCeilMachines(exactMachines);

            ProductionNode node = new ProductionNode();
            node.setRecipeId(recipe.getId());
            node.setRecipeName(recipe.getName());
            node.setOriginalRecipe(recipe.isOriginal());
            node.setMachineId(machine.getId());
            node.setMachineName(machine.getName());
            node.setExecutionsPerSecond(xr);
            node.setEffectiveTimeSeconds(effTime);
            node.setExactMachineCount(exactMachines);
            node.setCeilMachineCount(ceilMachines);

            for (ItemAmount ia : recipe.getInputs()) {
                node.getInputs().add(new RateEntry(ia.getId(), ia.getPiece() * xr, TimeUnit.PER_SECOND));
            }
            for (ItemAmount ia : recipe.getOutputs()) {
                node.getOutputs().add(new RateEntry(ia.getId(), ia.getPiece() * xr, TimeUnit.PER_SECOND));
            }
            for (ItemAmount ia : recipe.getByproducts()) {
                node.getByproducts().add(new RateEntry(ia.getId(), ia.getPiece() * xr, TimeUnit.PER_SECOND));
            }

            result.getNodes().add(node);
            nodesByRecipeId.put(recipe.getId(), node);

            ProductionPlanResult.MachineTotal mt = machineTotalsById.computeIfAbsent(
                    machine.getId(),
                    id -> new ProductionPlanResult.MachineTotal(id, machine.getName(), 0.0, 0)
            );
            mt.setExactCount(mt.getExactCount() + exactMachines);
            mt.setCeilCountSum(mt.getCeilCountSum() + ceilMachines);
        }

        result.getMachineTotals().addAll(machineTotalsById.values());

        // User inputs vs calculated missing inputs
        Map<UUID, Double> externalNet = byproductAnalysis.getNetExternalInputRequired();
        for (Map.Entry<UUID, Double> userEntry : userInputCapsPerSec.entrySet()) {
            UUID itemId = userEntry.getKey();
            Double cap = userEntry.getValue();
            double req = externalNet.getOrDefault(itemId, 0.0);
            String name = catalog.findItem(itemId).map(Item::getName).orElse(itemId.toString());
            result.getUserInputs().add(new ProductionPlanResult.InputSummaryEntry(
                    itemId, name, req, cap, false
            ));
            if (config.getMode() == CalculationMode.TARGET_OUTPUT && cap != null && req > cap + 1e-7) {
                result.getNotices().add(new SolverNotice(
                        SolverNotice.Level.WARNING,
                        String.format("Input '%s' exceeds configured capacity (needs %.3f/s, limit %.3f/s).", name, req, cap)
                ));
            }
        }

        for (Map.Entry<UUID, Double> extEntry : externalNet.entrySet()) {
            UUID itemId = extEntry.getKey();
            double req = extEntry.getValue();
            if (req > EPS && !userInputCapsPerSec.containsKey(itemId)) {
                result.getCalculatedMissingInputs().add(new RateEntry(itemId, req, TimeUnit.PER_SECOND));
            }
        }

        // Final outputs (targets + any co-product main output surplus)
        Map<UUID, Double> totalMainOut = new LinkedHashMap<>();
        Map<UUID, Double> totalIn = new LinkedHashMap<>();
        Map<UUID, Double> totalByp = new LinkedHashMap<>();
        for (ProductionNode node : result.getNodes()) {
            for (RateEntry e : node.getOutputs()) {
                totalMainOut.merge(e.getItemId(), e.getRate(), Double::sum);
            }
            for (RateEntry e : node.getInputs()) {
                totalIn.merge(e.getItemId(), e.getRate(), Double::sum);
            }
            for (RateEntry e : node.getByproducts()) {
                totalByp.merge(e.getItemId(), e.getRate(), Double::sum);
            }
        }

        Set<UUID> outputOrder = new LinkedHashSet<>(targetDemandPerSec.keySet());
        outputOrder.addAll(totalMainOut.keySet());
        for (UUID itemId : outputOrder) {
            double out = totalMainOut.getOrDefault(itemId, 0.0);
            double in = totalIn.getOrDefault(itemId, 0.0);
            double recycledByp = Boolean.TRUE.equals(byproductAnalysis.getEffectiveRecycling().get(itemId))
                    ? totalByp.getOrDefault(itemId, 0.0)
                    : 0.0;
            double netFinal = out + Math.min(in, recycledByp) - in;
            // For targets or co-product main-output surplus
            if (targetDemandPerSec.containsKey(itemId)) {
                double targetRate = targetDemandPerSec.get(itemId);
                double emitted = Math.max(targetRate, netFinal);
                if (emitted > EPS) {
                    result.getFinalOutputs().add(new RateEntry(itemId, emitted, TimeUnit.PER_SECOND));
                }
            } else if (netFinal > EPS) {
                result.getFinalOutputs().add(new RateEntry(itemId, netFinal, TimeUnit.PER_SECOND));
            }
        }

        for (Map.Entry<UUID, Double> bypEntry : byproductAnalysis.getByproductOutputSurplus().entrySet()) {
            if (bypEntry.getValue() > EPS) {
                result.getByproductOutputs().add(new RateEntry(bypEntry.getKey(), bypEntry.getValue(), TimeUnit.PER_SECOND));
            }
        }

        // Build directed flow edges
        buildFlowEdges(catalog, result, byproductAnalysis);

        return result;
    }

    /**
     * Constructs directed {@link FlowEdge} entries tracing item movement between external inputs,
     * intermediate producer/consumer nodes, recycled byproduct feedback loops, byproduct outputs,
     * and final outputs.
     *
     * @param catalog           the active factory catalog for looking up item display names
     * @param result            the production plan result whose nodes and output lists supply flow quantities
     * @param byproductAnalysis byproduct analysis providing effective recycling flags per item
     */
    private void buildFlowEdges(
            Catalog catalog,
            ProductionPlanResult result,
            ByproductAnalyzer.ByproductAnalysisResult byproductAnalysis
    ) {
        Set<UUID> allItemIds = new LinkedHashSet<>();
        for (ProductionNode node : result.getNodes()) {
            node.getInputs().forEach(e -> allItemIds.add(e.getItemId()));
            node.getOutputs().forEach(e -> allItemIds.add(e.getItemId()));
            node.getByproducts().forEach(e -> allItemIds.add(e.getItemId()));
        }

        Map<UUID, Double> finalOutputRemaining = new LinkedHashMap<>();
        for (RateEntry e : result.getFinalOutputs()) {
            finalOutputRemaining.put(e.getItemId(), e.getRate());
        }

        for (UUID itemId : allItemIds) {
            String itemName = catalog.findItem(itemId).map(Item::getName).orElse(itemId.toString());
            boolean recycled = Boolean.TRUE.equals(byproductAnalysis.getEffectiveRecycling().get(itemId));

            // Consumer remaining needs per recipe node
            Map<UUID, Double> consumerNeed = new LinkedHashMap<>();
            for (ProductionNode node : result.getNodes()) {
                for (RateEntry e : node.getInputs()) {
                    if (e.getItemId().equals(itemId) && e.getRate() > EPS) {
                        consumerNeed.merge(node.getRecipeId(), e.getRate(), Double::sum);
                    }
                }
            }

            // 1. Byproducts
            for (ProductionNode producer : result.getNodes()) {
                double bypAmount = producer.getByproducts().stream()
                        .filter(e -> e.getItemId().equals(itemId))
                        .mapToDouble(RateEntry::getRate)
                        .sum();
                if (bypAmount <= EPS) {
                    continue;
                }
                if (recycled) {
                    for (ProductionNode consumer : result.getNodes()) {
                        double need = consumerNeed.getOrDefault(consumer.getRecipeId(), 0.0);
                        if (need > EPS && bypAmount > EPS) {
                            double flow = Math.min(need, bypAmount);
                            result.getEdges().add(new FlowEdge(
                                    producer.getRecipeId(), producer.getRecipeName(),
                                    consumer.getRecipeId(), consumer.getRecipeName(),
                                    itemId, itemName, flow, FlowEdge.EdgeType.RECURSIVE_FEEDBACK
                            ));
                            bypAmount -= flow;
                            consumerNeed.put(consumer.getRecipeId(), need - flow);
                        }
                    }
                }
                if (bypAmount > EPS) {
                    result.getEdges().add(new FlowEdge(
                            producer.getRecipeId(), producer.getRecipeName(),
                            null, "Byproduct Output",
                            itemId, itemName, bypAmount, FlowEdge.EdgeType.BYPRODUCT_OUTPUT
                    ));
                }
            }

            // 2. Main outputs -> intermediate consumers, then final output
            for (ProductionNode producer : result.getNodes()) {
                double outAmount = producer.getOutputs().stream()
                        .filter(e -> e.getItemId().equals(itemId))
                        .mapToDouble(RateEntry::getRate)
                        .sum();
                if (outAmount <= EPS) {
                    continue;
                }
                for (ProductionNode consumer : result.getNodes()) {
                    double need = consumerNeed.getOrDefault(consumer.getRecipeId(), 0.0);
                    if (need > EPS && outAmount > EPS) {
                        double flow = Math.min(need, outAmount);
                        result.getEdges().add(new FlowEdge(
                                producer.getRecipeId(), producer.getRecipeName(),
                                consumer.getRecipeId(), consumer.getRecipeName(),
                                itemId, itemName, flow, FlowEdge.EdgeType.INTERMEDIATE
                        ));
                        outAmount -= flow;
                        consumerNeed.put(consumer.getRecipeId(), need - flow);
                    }
                }
                if (outAmount > EPS) {
                    double targetRem = finalOutputRemaining.getOrDefault(itemId, outAmount);
                    double flow = Math.min(outAmount, targetRem);
                    if (flow > EPS) {
                        result.getEdges().add(new FlowEdge(
                                producer.getRecipeId(), producer.getRecipeName(),
                                null, "Final Output",
                                itemId, itemName, flow, FlowEdge.EdgeType.FINAL_OUTPUT
                        ));
                        finalOutputRemaining.put(itemId, Math.max(0.0, targetRem - flow));
                    }
                }
            }

            // 3. Remaining consumer needs come from External Input
            for (ProductionNode consumer : result.getNodes()) {
                double need = consumerNeed.getOrDefault(consumer.getRecipeId(), 0.0);
                if (need > EPS) {
                    result.getEdges().add(new FlowEdge(
                            null, "External Input",
                            consumer.getRecipeId(), consumer.getRecipeName(),
                            itemId, itemName, need, FlowEdge.EdgeType.EXTERNAL_INPUT
                    ));
                }
            }
        }
    }

    /**
     * Selects the concrete {@link Machine} for executing a recipe, honoring user overrides if valid
     * or falling back to the first compatible machine in the catalog.
     *
     * @param catalog the active factory catalog
     * @param config  the production plan configuration holding per-recipe machine selections
     * @param recipe  the recipe to assign a machine to
     * @return the chosen compatible {@link Machine}
     * @throws SolverException if no compatible machine exists for the recipe
     */
    private Machine resolveMachineForRecipe(Catalog catalog, ProductionPlanConfig config, Recipe recipe) {
        List<Machine> compatible = catalog.getCompatibleMachines(recipe);
        if (compatible.isEmpty()) {
            throw new SolverException("No compatible machine found for recipe '" + recipe.getName() + "'.");
        }
        UUID selectedId = config.getMachineSelections().get(recipe.getId());
        if (selectedId != null) {
            for (Machine m : compatible) {
                if (m.getId().equals(selectedId)) {
                    return m;
                }
            }
        }
        return compatible.get(0);
    }

    /**
     * Validates plan configuration invariants before solving: non-empty targets, mode-specific target/input
     * multiplicity, positive rates, catalog existence, and disjointness between targets and supplied inputs.
     *
     * @param catalog the active factory catalog
     * @param config  the production plan configuration to validate
     * @throws SolverException if any validation rule is violated
     */
    private void validateConfig(Catalog catalog, ProductionPlanConfig config) {
        if (config.getTargets().isEmpty()) {
            throw new SolverException("Please specify at least one target output item.");
        }
        if (config.getMode() == CalculationMode.MAX_OUTPUT && config.getTargets().size() != 1) {
            throw new SolverException("Max output mode requires exactly one target item.");
        }
        if (config.getMode() == CalculationMode.MAX_OUTPUT && config.getInputs().isEmpty()) {
            throw new SolverException("Max output mode requires at least one supplied input with a positive rate.");
        }

        Set<UUID> seenTargets = new HashSet<>();
        for (RateEntry target : config.getTargets()) {
            if (target.getItemId() == null) {
                throw new SolverException("All target entries must select an item.");
            }
            if (!seenTargets.add(target.getItemId())) {
                String name = catalog.findItem(target.getItemId()).map(Item::getName).orElse(target.getItemId().toString());
                throw new SolverException("Duplicate target item: '" + name + "'.");
            }
            Item item = catalog.findItem(target.getItemId())
                    .orElseThrow(() -> new SolverException("Target item no longer exists in catalog."));
            if (!RecipeResolver.isEligibleTarget(catalog, item.getId())) {
                throw new SolverException(
                        "Item '" + item.getName()
                                + "' cannot be chosen as a target because it has no recipe with inputs.");
            }
            if (config.getMode() == CalculationMode.TARGET_OUTPUT) {
                if (target.getRate() == null || Double.isNaN(target.getRate()) || target.getRate() <= 0.0) {
                    throw new SolverException("Target '" + item.getName() + "' must have a positive rate.");
                }
            }
        }

        Set<UUID> seenInputs = new HashSet<>();
        for (RateEntry input : config.getInputs()) {
            if (input.getItemId() == null) {
                throw new SolverException("All input entries must select an item.");
            }
            Item item = catalog.findItem(input.getItemId())
                    .orElseThrow(() -> new SolverException("Input item no longer exists in catalog."));
            if (!seenInputs.add(input.getItemId())) {
                throw new SolverException("Duplicate input item: '" + item.getName() + "'.");
            }
            if (seenTargets.contains(input.getItemId())) {
                throw new SolverException("Item '" + item.getName() + "' cannot be both a target output and a supplied external input.");
            }
            if (config.getMode() == CalculationMode.MAX_OUTPUT) {
                if (input.getRate() == null || Double.isNaN(input.getRate()) || input.getRate() <= 0.0) {
                    throw new SolverException("In max output mode, input '" + item.getName() + "' must have a positive rate.");
                }
            } else if (input.getRate() != null && (Double.isNaN(input.getRate()) || input.getRate() <= 0.0)) {
                throw new SolverException("Supplied input limit for '" + item.getName() + "' must be positive (or leave blank for unlimited).");
            }
        }
    }

    /**
     * Converts configured user-supplied inputs into a map keyed by item ID with rates normalized to items/sec
     * (or {@code null} for unlimited entries in {@code TARGET_OUTPUT} mode).
     *
     * @param catalog the active factory catalog
     * @param config  the production plan configuration containing user input entries
     * @return ordered map from item ID to input rate cap in items/sec (or {@code null} if unlimited)
     */
    Map<UUID, Double> extractUserInputs(Catalog catalog, ProductionPlanConfig config) {
        Map<UUID, Double> map = new LinkedHashMap<>();
        for (RateEntry e : config.getInputs()) {
            map.put(e.getItemId(), e.getRatePerSecond());
        }
        return map;
    }

    /**
     * Converts configured target outputs into a map keyed by item ID with rates normalized to items/sec
     * (using {@code 1.0} items/sec as the unit placeholder in {@code MAX_OUTPUT} mode).
     *
     * @param catalog the active factory catalog
     * @param config  the production plan configuration containing target entries
     * @return ordered map from target item ID to demand rate in items/sec
     */
    Map<UUID, Double> extractTargets(Catalog catalog, ProductionPlanConfig config) {
        Map<UUID, Double> map = new LinkedHashMap<>();
        for (RateEntry e : config.getTargets()) {
            double rate = config.getMode() == CalculationMode.MAX_OUTPUT ? 1.0 : e.getRatePerSecond();
            map.put(e.getItemId(), rate);
        }
        return map;
    }
}
