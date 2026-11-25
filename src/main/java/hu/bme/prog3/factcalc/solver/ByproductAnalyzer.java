package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.SolverNotice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Analyzes recyclable byproducts in a solved active recipe set:
 * detects partial offsets, self-sustaining loops, positive feedback errors,
 * and valid surplus byproduct outputs when other external inputs exist.
 */
public class ByproductAnalyzer {

    private static final double EPS = LinearSystemSolver.EPS;

    public static class ByproductAnalysisResult {
        private final Set<UUID> recyclableByproductIds;
        private final Map<UUID, Boolean> effectiveRecycling;
        private final Map<UUID, Double> netExternalInputRequired;
        private final Map<UUID, Double> byproductOutputSurplus;
        private final List<SolverNotice> notices;

        /**
         * Constructs a ByproductAnalysisResult holding external input balances, surpluses, and notices.
         *
         * @param recyclableByproductIds   set of byproduct item IDs eligible for recursive reuse
         * @param effectiveRecycling       effective recycling state per recyclable byproduct ID
         * @param netExternalInputRequired net external input rates required in items per second
         * @param byproductOutputSurplus   byproduct output rates emitted in items per second
         * @param notices                  informational or warning notices generated during analysis
         */
        public ByproductAnalysisResult(
                Set<UUID> recyclableByproductIds,
                Map<UUID, Boolean> effectiveRecycling,
                Map<UUID, Double> netExternalInputRequired,
                Map<UUID, Double> byproductOutputSurplus,
                List<SolverNotice> notices
        ) {
            this.recyclableByproductIds = recyclableByproductIds;
            this.effectiveRecycling = effectiveRecycling;
            this.netExternalInputRequired = netExternalInputRequired;
            this.byproductOutputSurplus = byproductOutputSurplus;
            this.notices = notices;
        }

        /**
         * Returns all byproduct item IDs eligible for recycling in the active recipe set.
         *
         * @return set of recyclable byproduct item UUIDs
         */
        public Set<UUID> getRecyclableByproductIds() {
            return recyclableByproductIds;
        }

        /**
         * Returns the effective recycling boolean map per recyclable byproduct item ID.
         *
         * @return map of item UUID to recycling flag
         */
        public Map<UUID, Boolean> getEffectiveRecycling() {
            return effectiveRecycling;
        }

        /**
         * Returns the net external input required for each item in items per second.
         *
         * @return map of item UUID to net external input rate
         */
        public Map<UUID, Double> getNetExternalInputRequired() {
            return netExternalInputRequired;
        }

        /**
         * Returns the byproduct output surplus rate for each emitted byproduct in items per second.
         *
         * @return map of item UUID to byproduct surplus rate
         */
        public Map<UUID, Double> getByproductOutputSurplus() {
            return byproductOutputSurplus;
        }

        /**
         * Returns all notices produced during byproduct analysis.
         *
         * @return list of SolverNotice objects
         */
        public List<SolverNotice> getNotices() {
            return notices;
        }
    }

    /**
     * Identifies all byproduct item IDs produced by any active recipe that are also consumed
     * as an input by at least one active recipe.
     *
     * @param activeRecipes list of active recipes in the graph
     * @return set of item UUIDs that qualify as recyclable byproducts
     */
    public static Set<UUID> findRecyclableByproducts(List<Recipe> activeRecipes) {
        Set<UUID> consumed = new HashSet<>();
        Set<UUID> byproducts = new LinkedHashSet<>();
        for (Recipe r : activeRecipes) {
            for (ItemAmount in : r.getInputs()) {
                consumed.add(in.getId());
            }
            for (ItemAmount byp : r.getByproducts()) {
                byproducts.add(byp.getId());
            }
        }
        Set<UUID> recyclable = new LinkedHashSet<>();
        for (UUID id : byproducts) {
            if (consumed.contains(id)) {
                recyclable.add(id);
            }
        }
        return recyclable;
    }

    /**
     * Analyzes byproduct flows across solved recipe execution rates, detecting self-sustaining loops,
     * partial offsets, invalid positive feedback without external inputs, and surplus byproduct outputs.
     *
     * @param catalog            active catalog for item name lookup
     * @param activeRecipes      active recipes with positive execution rates
     * @param recipeRates        solved execution rates in cycles per second keyed by recipe UUID
     * @param leafItemIds        terminal leaf item UUIDs
     * @param targetDemandByItem external target demands in items per second
     * @param requestedRecycling user-requested recursive reuse toggles keyed by byproduct item UUID
     * @return ByproductAnalysisResult containing external requirements, surpluses, and notices
     * @throws SolverException if a recycled byproduct exhibits positive feedback without any external input
     */
    public ByproductAnalysisResult analyze(
            Catalog catalog,
            List<Recipe> activeRecipes,
            Map<UUID, Double> recipeRates,
            Set<UUID> leafItemIds,
            Map<UUID, Double> targetDemandByItem,
            Map<UUID, Boolean> requestedRecycling
    ) {
        Set<UUID> recyclableIds = findRecyclableByproducts(activeRecipes);
        Map<UUID, Boolean> effectiveRecycling = new LinkedHashMap<>();
        for (UUID id : recyclableIds) {
            effectiveRecycling.put(id, Boolean.TRUE.equals(requestedRecycling.get(id)));
        }

        Map<UUID, Double> totalIn = new LinkedHashMap<>();
        Map<UUID, Double> totalOut = new LinkedHashMap<>();
        Map<UUID, Double> totalByp = new LinkedHashMap<>();
        Set<UUID> allItemsInGraph = new LinkedHashSet<>();

        for (Recipe r : activeRecipes) {
            double xr = recipeRates.getOrDefault(r.getId(), 0.0);
            if (xr <= EPS) {
                continue;
            }
            for (ItemAmount ia : r.getInputs()) {
                allItemsInGraph.add(ia.getId());
                totalIn.merge(ia.getId(), ia.getPiece() * xr, Double::sum);
            }
            for (ItemAmount ia : r.getOutputs()) {
                allItemsInGraph.add(ia.getId());
                totalOut.merge(ia.getId(), ia.getPiece() * xr, Double::sum);
            }
            for (ItemAmount ia : r.getByproducts()) {
                allItemsInGraph.add(ia.getId());
                totalByp.merge(ia.getId(), ia.getPiece() * xr, Double::sum);
            }
        }

        Map<UUID, Double> netExternalInput = new LinkedHashMap<>();
        Map<UUID, Double> byproductSurplus = new LinkedHashMap<>();
        List<SolverNotice> notices = new ArrayList<>();

        for (UUID itemId : allItemsInGraph) {
            double inRate = totalIn.getOrDefault(itemId, 0.0);
            double outRate = totalOut.getOrDefault(itemId, 0.0);
            double bypRate = totalByp.getOrDefault(itemId, 0.0);
            double targetRate = targetDemandByItem.getOrDefault(itemId, 0.0);
            boolean recycled = Boolean.TRUE.equals(effectiveRecycling.get(itemId));

            if (recycled) {
                double delta = inRate + targetRate - outRate - bypRate;
                if (delta > EPS) {
                    netExternalInput.put(itemId, delta);
                } else {
                    netExternalInput.put(itemId, 0.0);
                }
            } else {
                double neededFromExternal = inRate + targetRate - outRate;
                if (neededFromExternal > EPS) {
                    netExternalInput.put(itemId, neededFromExternal);
                }
                if (bypRate > EPS) {
                    byproductSurplus.put(itemId, bypRate);
                }
            }
        }

        for (UUID itemId : recyclableIds) {
            if (!Boolean.TRUE.equals(effectiveRecycling.get(itemId))) {
                continue;
            }
            double inRate = totalIn.getOrDefault(itemId, 0.0);
            double outRate = totalOut.getOrDefault(itemId, 0.0);
            double bypRate = totalByp.getOrDefault(itemId, 0.0);
            double targetRate = targetDemandByItem.getOrDefault(itemId, 0.0);
            double delta = inRate + targetRate - outRate - bypRate;
            String itemName = catalog.findItem(itemId).map(Item::getName).orElse(itemId.toString());

            if (Math.abs(delta) <= EPS && bypRate > EPS) {
                notices.add(new SolverNotice(
                        SolverNotice.Level.INFO,
                        "Self-sustaining byproduct loop for '" + itemName + "' (requires no external steady-state input after startup)."
                ));
            } else if (delta < -EPS) {
                if (!cycleHasExternalOrBaseInput(itemId, activeRecipes, recipeRates, netExternalInput, effectiveRecycling)) {
                    throw new SolverException(
                            "Positive feedback detected for '" + itemName
                                    + "' without any external input (the loop produces more '" + itemName + "' than it consumes).");
                }
                double surplus = -delta;
                byproductSurplus.put(itemId, surplus);
                notices.add(new SolverNotice(
                        SolverNotice.Level.INFO,
                        "Self-sustaining byproduct loop for '" + itemName + "' with surplus routed to byproduct output."
                ));
            }
        }

        return new ByproductAnalysisResult(
                recyclableIds,
                effectiveRecycling,
                netExternalInput,
                byproductSurplus,
                notices
        );
    }

    /**
     * Checks via backwards BFS whether the recursive cycle generating surplus item {@code surplusItemId}
     * consumes at least one positive external input or inputless extraction recipe.
     *
     * @param surplusItemId      item UUID exhibiting a net surplus in a recycled loop
     * @param activeRecipes      active recipes in the graph
     * @param recipeRates        solved recipe execution rates
     * @param netExternalInput   net external input rates per item
     * @param effectiveRecycling effective recycling flags per item
     * @return true if the cycle depends on an external or inputless source, false if it is pure positive feedback
     */
    private boolean cycleHasExternalOrBaseInput(
            UUID surplusItemId,
            List<Recipe> activeRecipes,
            Map<UUID, Double> recipeRates,
            Map<UUID, Double> netExternalInput,
            Map<UUID, Boolean> effectiveRecycling
    ) {
        List<Recipe> positiveRecipes = new ArrayList<>();
        for (Recipe r : activeRecipes) {
            if (recipeRates.getOrDefault(r.getId(), 0.0) > EPS) {
                positiveRecipes.add(r);
            }
        }

        Map<UUID, List<Recipe>> producersByItem = new HashMap<>();
        for (Recipe r : positiveRecipes) {
            for (ItemAmount ia : r.getOutputs()) {
                producersByItem.computeIfAbsent(ia.getId(), _ -> new ArrayList<>()).add(r);
            }
            for (ItemAmount ia : r.getByproducts()) {
                if (Boolean.TRUE.equals(effectiveRecycling.get(ia.getId()))) {
                    producersByItem.computeIfAbsent(ia.getId(), _ -> new ArrayList<>()).add(r);
                }
            }
        }

        Set<UUID> visitedRecipes = new HashSet<>();
        Deque<Recipe> queue = new ArrayDeque<>();
        for (Recipe r : positiveRecipes) {
            if (r.producesByproduct(surplusItemId)) {
                if (visitedRecipes.add(r.getId())) {
                    queue.add(r);
                }
            }
        }

        while (!queue.isEmpty()) {
            Recipe current = queue.poll();
            if (current.getInputs().isEmpty()) {
                return true;
            }
            for (ItemAmount input : current.getInputs()) {
                UUID inputId = input.getId();
                if (netExternalInput.getOrDefault(inputId, 0.0) > EPS) {
                    return true;
                }
                for (Recipe upstream : producersByItem.getOrDefault(inputId, List.of())) {
                    if (visitedRecipes.add(upstream.getId())) {
                        queue.add(upstream);
                    }
                }
            }
        }
        return false;
    }
}
