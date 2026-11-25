package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.ProductionNode;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.Recipe;

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
 * Discrete optimizer for MAX_OUTPUT + auto mode.
 * Searches across both original and alternative recipes using reachability pruning
 * and lazy backtracking DFS to maximize the single target output rate.
 */
public class AutoOptimizer {

    private static final int DEFAULT_STATE_BUDGET = 50_000;
    private static final double EPS = 1e-9;

    private final ProductionSolver solver;
    private final int stateBudget;

    /**
     * Creates an optimizer backed by the given solver using the default state exploration budget.
     *
     * @param solver the production solver used to evaluate candidate recipe subgraphs
     */
    public AutoOptimizer(ProductionSolver solver) {
        this(solver, DEFAULT_STATE_BUDGET);
    }

    /**
     * Creates an optimizer backed by the given solver with a custom DFS state budget.
     *
     * @param solver      the production solver used to evaluate candidate recipe subgraphs
     * @param stateBudget maximum number of DFS states to visit before stopping search
     */
    public AutoOptimizer(ProductionSolver solver, int stateBudget) {
        this.solver = solver;
        this.stateBudget = stateBudget;
    }

    private static final class SearchState {
        int visitedStates = 0;
        ProductionPlanResult bestResult = null;
        double bestOutputRate = -1.0;
        int bestAltCount = Integer.MAX_VALUE;
        int bestNodeCount = Integer.MAX_VALUE;
        double bestMachineCount = Double.POSITIVE_INFINITY;
    }

    /**
     * Searches over valid recipe combinations and recycling settings to maximize the single target output rate
     * using the supplied inputs.
     *
     * @param catalog the active factory catalog
     * @param config  the plan configuration in {@code MAX_OUTPUT} mode with exactly one target
     * @return the highest-output production plan result found (tie-breaking by fewer alternatives, nodes, and machines)
     * @throws SolverException if preconditions are violated, the target is unreachable, or no valid plan is found
     */
    public ProductionPlanResult optimize(Catalog catalog, ProductionPlanConfig config) {
        if (config.getMode() != CalculationMode.MAX_OUTPUT || config.getTargets().size() != 1) {
            throw new SolverException("Auto mode is only supported for Max Output calculation with a single target item.");
        }

        UUID targetId = config.getTargets().get(0).getItemId();
        Map<UUID, Double> userInputs = solver.extractUserInputs(catalog, config);
        Map<UUID, Double> unitTargetDemand = new LinkedHashMap<>();
        unitTargetDemand.put(targetId, 1.0);

        Set<UUID> craftable = computeCraftableItems(catalog, userInputs.keySet());
        if (!craftable.contains(targetId)) {
            String targetName = catalog.findItem(targetId).map(Item::getName).orElse(targetId.toString());
            throw new SolverException("Target item '" + targetName + "' cannot be crafted with the current catalog.");
        }

        Map<UUID, List<Recipe>> craftableProducers = new HashMap<>();
        for (Recipe r : catalog.getRecipes()) {
            if (catalog.getCompatibleMachines(r).isEmpty()) {
                continue;
            }
            boolean allInputsCraftable = true;
            for (ItemAmount ia : r.getInputs()) {
                if (!craftable.contains(ia.getId())) {
                    allInputsCraftable = false;
                    break;
                }
            }
            if (allInputsCraftable) {
                for (ItemAmount out : r.getOutputs()) {
                    craftableProducers.computeIfAbsent(out.getId(), _ -> new ArrayList<>()).add(r);
                }
            }
        }

        SearchState state = new SearchState();
        List<UUID> neededQueue = new ArrayList<>();
        neededQueue.add(targetId);
        Map<UUID, Recipe> chosen = new LinkedHashMap<>();
        Set<UUID> rawLeaves = new LinkedHashSet<>();

        dfs(
                catalog,
                config,
                targetId,
                userInputs,
                unitTargetDemand,
                craftableProducers,
                neededQueue,
                0,
                chosen,
                rawLeaves,
                state
        );

        if (state.bestResult == null) {
            throw new SolverException(
                    "No valid production line could be found using the supplied inputs in auto mode.");
        }
        return state.bestResult;
    }

    /**
     * Recursively explores valid producer-recipe assignments for each required intermediate item
     * using depth-first search with state-budget pruning.
     *
     * @param catalog            the active factory catalog
     * @param baseConfig         the user's base plan configuration
     * @param targetId           the single target item ID being maximized
     * @param userInputs         map of user-supplied item IDs to available rates in items/sec
     * @param unitTargetDemand   map containing the unit (1.0/s) demand for {@code targetId}
     * @param craftableProducers map from item ID to all craftable recipes that produce it
     * @param neededQueue        ordered list of item IDs whose production needs to be resolved
     * @param index              current cursor position inside {@code neededQueue}
     * @param chosen             map of item IDs to the currently assigned producer recipes
     * @param rawLeaves          set of uncrafted raw leaf items allowed in {@code MAX_OUTPUT} mode
     * @param state              mutable search state tracking visited count and best solution found
     */
    private void dfs(
            Catalog catalog,
            ProductionPlanConfig baseConfig,
            UUID targetId,
            Map<UUID, Double> userInputs,
            Map<UUID, Double> unitTargetDemand,
            Map<UUID, List<Recipe>> craftableProducers,
            List<UUID> neededQueue,
            int index,
            Map<UUID, Recipe> chosen,
            Set<UUID> rawLeaves,
            SearchState state
    ) {
        if (state.visitedStates >= stateBudget || Thread.currentThread().isInterrupted()) {
            return;
        }
        state.visitedStates++;

        // Advance past items already resolved (user inputs, already chosen recipes, or raw leaves)
        while (index < neededQueue.size()) {
            UUID itemId = neededQueue.get(index);
            if ((userInputs.containsKey(itemId) && !itemId.equals(targetId))
                    || chosen.containsKey(itemId)
                    || rawLeaves.contains(itemId)) {
                index++;
            } else {
                break;
            }
        }

        if (index >= neededQueue.size()) {
            evaluateLeafAssignment(catalog, baseConfig, targetId, userInputs, unitTargetDemand, chosen, rawLeaves, state);
            return;
        }

        UUID itemId = neededQueue.get(index);
        List<Recipe> candidates = craftableProducers.getOrDefault(itemId, List.of());

        if (candidates.isEmpty()) {
            if (itemId.equals(targetId)) {
                return;
            }
            // Raw missing item (allowed in MAX_OUTPUT)
            rawLeaves.add(itemId);
            dfs(catalog, baseConfig, targetId, userInputs, unitTargetDemand, craftableProducers,
                    neededQueue, index + 1, chosen, rawLeaves, state);
            rawLeaves.remove(itemId);
            return;
        }

        for (Recipe candidate : candidates) {
            if (state.visitedStates >= stateBudget || Thread.currentThread().isInterrupted()) {
                return;
            }
            // Prune trivial pure self-loop where a recipe needs its own item directly without user input
            if (candidate.consumesInput(itemId) && !userInputs.containsKey(itemId)) {
                continue;
            }

            chosen.put(itemId, candidate);
            int prevSize = neededQueue.size();
            for (ItemAmount input : candidate.getInputs()) {
                neededQueue.add(input.getId());
            }

            dfs(catalog, baseConfig, targetId, userInputs, unitTargetDemand, craftableProducers,
                    neededQueue, index + 1, chosen, rawLeaves, state);

            while (neededQueue.size() > prevSize) {
                neededQueue.remove(neededQueue.size() - 1);
            }
            chosen.remove(itemId);
        }
    }

    /**
     * Constructs a candidate {@link RecipeResolver.ResolvedGraph} from the current DFS assignment,
     * tests byproduct recycling configurations via {@link ProductionSolver}, and updates the best solution
     * when output rate improves or tie-breakers favor the candidate.
     *
     * @param catalog          the active factory catalog
     * @param baseConfig       the user's base plan configuration
     * @param targetId         the single target item ID being maximized
     * @param userInputs       map of user-supplied item IDs to available rates in items/sec
     * @param unitTargetDemand map containing the unit (1.0/s) demand for {@code targetId}
     * @param chosen           map of item IDs to assigned producer recipes
     * @param rawLeaves        set of uncrafted raw leaf items in the current subgraph
     * @param state            mutable search state storing the best result and metrics
     */
    private void evaluateLeafAssignment(
            Catalog catalog,
            ProductionPlanConfig baseConfig,
            UUID targetId,
            Map<UUID, Double> userInputs,
            Map<UUID, Double> unitTargetDemand,
            Map<UUID, Recipe> chosen,
            Set<UUID> rawLeaves,
            SearchState state
    ) {
        Set<Recipe> activeRecipes = new LinkedHashSet<>(chosen.values());
        Set<UUID> leaves = new LinkedHashSet<>(rawLeaves);
        for (UUID uId : userInputs.keySet()) {
            if (!chosen.containsKey(uId)) {
                leaves.add(uId);
            }
        }

        RecipeResolver.ResolvedGraph resolved = new RecipeResolver.ResolvedGraph(chosen, activeRecipes, leaves);
        Set<UUID> recyclable = ByproductAnalyzer.findRecyclableByproducts(new ArrayList<>(activeRecipes));

        // Try recycling all eligible byproducts when valid (or subset if feedback error occurs)
        List<Map<UUID, Boolean>> recyclingConfigs = generateRecyclingCandidates(baseConfig, recyclable);
        for (Map<UUID, Boolean> recycleMap : recyclingConfigs) {
            ProductionPlanConfig candidateConfig = new ProductionPlanConfig(baseConfig);
            candidateConfig.setAutoMode(false);
            candidateConfig.setRecycledByproducts(recycleMap);

            try {
                ProductionPlanResult candidateResult = solver.solveResolvedGraph(
                        catalog,
                        candidateConfig,
                        resolved,
                        userInputs,
                        unitTargetDemand
                );

                double outputRate = candidateResult.getFinalOutputs().stream()
                        .filter(e -> e.getItemId().equals(targetId))
                        .mapToDouble(RateEntry::getRate)
                        .sum();

                int altCount = 0;
                double totalMachines = 0.0;
                for (ProductionNode node : candidateResult.getNodes()) {
                    if (!node.isOriginalRecipe()) {
                        altCount++;
                    }
                    totalMachines += node.getExactMachineCount();
                }
                int nodeCount = candidateResult.getNodes().size();

                boolean isBetter = false;
                if (outputRate > state.bestOutputRate + EPS) {
                    isBetter = true;
                } else if (Math.abs(outputRate - state.bestOutputRate) <= EPS) {
                    if (altCount < state.bestAltCount) {
                        isBetter = true;
                    } else if (altCount == state.bestAltCount) {
                        if (nodeCount < state.bestNodeCount) {
                            isBetter = true;
                        } else if (nodeCount == state.bestNodeCount && totalMachines < state.bestMachineCount - EPS) {
                            isBetter = true;
                        }
                    }
                }

                if (isBetter) {
                    state.bestResult = candidateResult;
                    state.bestOutputRate = outputRate;
                    state.bestAltCount = altCount;
                    state.bestNodeCount = nodeCount;
                    state.bestMachineCount = totalMachines;
                }
            } catch (SolverException ignored) {
                // Invalid recipe combination (e.g. positive feedback or unused user inputs) -> skip
            }
        }
    }

    /**
     * Generates candidate byproduct-recycling configurations to test for a recipe subgraph.
     * Enumerates all {@code 2^n} subsets when at most 4 recyclable byproducts exist, or falls back
     * to greedy presets (all enabled, user defaults, all disabled) for larger sets.
     *
     * @param baseConfig the user's base plan configuration containing default recycling preferences
     * @param recyclable set of recyclable byproduct item IDs in the candidate subgraph
     * @return list of candidate recycling toggle maps to evaluate
     */
    private List<Map<UUID, Boolean>> generateRecyclingCandidates(ProductionPlanConfig baseConfig, Set<UUID> recyclable) {
        List<Map<UUID, Boolean>> configs = new ArrayList<>();
        if (recyclable.isEmpty()) {
            configs.add(new LinkedHashMap<>());
            return configs;
        }
        List<UUID> ids = new ArrayList<>(recyclable);
        if (ids.size() <= 4) {
            int combos = 1 << ids.size();
            for (int mask = combos - 1; mask >= 0; mask--) {
                Map<UUID, Boolean> map = new LinkedHashMap<>();
                for (int i = 0; i < ids.size(); i++) {
                    map.put(ids.get(i), (mask & (1 << i)) != 0);
                }
                configs.add(map);
            }
        } else {
            // Greedy: all true, user defaults, and all false
            Map<UUID, Boolean> allTrue = new LinkedHashMap<>();
            Map<UUID, Boolean> allFalse = new LinkedHashMap<>();
            for (UUID id : ids) {
                allTrue.put(id, true);
                allFalse.put(id, false);
            }
            configs.add(allTrue);
            configs.add(new LinkedHashMap<>(baseConfig.getRecycledByproducts()));
            configs.add(allFalse);
        }
        return configs;
    }

    /**
     * Computes all items reachable in a bottom-up pass starting from user inputs,
     * inputless recipes, and raw leaf items with no producing recipes.
     *
     * @param catalog      the active factory catalog
     * @param userInputIds set of item IDs supplied by the user
     * @return set of all reachable/craftable item IDs
     */
    private Set<UUID> computeCraftableItems(Catalog catalog, Set<UUID> userInputIds) {
        Set<UUID> craftable = new HashSet<>(userInputIds);
        // Any item with no producing recipe is a raw material (allowed as missing leaf in MAX_OUTPUT)
        for (Item item : catalog.getAllItems()) {
            if (catalog.getRecipesProducing(item.getId()).isEmpty()) {
                craftable.add(item.getId());
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Recipe r : catalog.getRecipes()) {
                if (catalog.getCompatibleMachines(r).isEmpty()) {
                    continue;
                }
                boolean inputsReady = true;
                for (ItemAmount ia : r.getInputs()) {
                    if (!craftable.contains(ia.getId())) {
                        inputsReady = false;
                        break;
                    }
                }
                if (inputsReady) {
                    for (ItemAmount out : r.getOutputs()) {
                        if (craftable.add(out.getId())) {
                            changed = true;
                        }
                    }
                }
            }
        }
        return craftable;
    }
}
