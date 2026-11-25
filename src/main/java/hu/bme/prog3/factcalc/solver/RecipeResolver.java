package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Recipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the active recipe graph starting from target items down to leaf items
 * (user inputs, inputless recipes, or in MAX_OUTPUT mode raw missing items).
 */
public class RecipeResolver {

    public static class ResolvedGraph {
        private final Map<UUID, Recipe> chosenRecipeByItem;
        private final Set<Recipe> activeRecipes;
        private final Set<UUID> leafItemIds;

        /**
         * Creates an immutable container holding the resolved recipe selections and leaf items.
         *
         * @param chosenRecipeByItem map from item UUID to the selected producing recipe
         * @param activeRecipes      ordered set of all active recipes in the graph
         * @param leafItemIds        set of leaf item UUIDs supplied externally or uncrafted
         */
        public ResolvedGraph(
                Map<UUID, Recipe> chosenRecipeByItem,
                Set<Recipe> activeRecipes,
                Set<UUID> leafItemIds
        ) {
            this.chosenRecipeByItem = chosenRecipeByItem;
            this.activeRecipes = activeRecipes;
            this.leafItemIds = leafItemIds;
        }

        /**
         * Returns the map of item UUID to chosen producing Recipe.
         *
         * @return map of item UUID to Recipe
         */
        public Map<UUID, Recipe> getChosenRecipeByItem() {
            return chosenRecipeByItem;
        }

        /**
         * Returns all distinct active recipes required by the resolved graph.
         *
         * @return set of active Recipe objects
         */
        public Set<Recipe> getActiveRecipes() {
            return activeRecipes;
        }

        /**
         * Returns the set of terminal leaf item UUIDs.
         *
         * @return set of leaf item UUIDs
         */
        public Set<UUID> getLeafItemIds() {
            return leafItemIds;
        }
    }

    /**
     * Determines whether an item is eligible to be selected as a calculation target
     * (it must have at least one producing recipe with at least one input).
     *
     * @param catalog the catalog containing recipes
     * @param itemId  the item UUID to check
     * @return true if eligible as a target, false otherwise
     */
    public static boolean isEligibleTarget(Catalog catalog, UUID itemId) {
        if (catalog == null || itemId == null) {
            return false;
        }
        for (Recipe recipe : catalog.getRecipesProducing(itemId)) {
            if (!recipe.getInputs().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects all items in the catalog that are eligible to be selected as calculation targets.
     *
     * @param catalog the catalog to filter
     * @return list of eligible target items
     */
    public static List<Item> getEligibleTargetItems(Catalog catalog) {
        List<Item> result = new ArrayList<>();
        if (catalog == null) {
            return result;
        }
        for (Item item : catalog.getAllItems()) {
            if (isEligibleTarget(catalog, item.getId())) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Traverses dependencies from target items down to leaf items, resolving recipe overrides,
     * original-recipe conflicts, and alternative-only confirmations.
     *
     * @param catalog         active catalog
     * @param mode            calculation mode (TARGET_OUTPUT or MAX_OUTPUT)
     * @param targetItemIds   set of target output item UUIDs
     * @param userInputIds    set of user-supplied external input item UUIDs
     * @param recipeOverrides explicit user recipe overrides (item UUID -&gt; recipe UUID)
     * @param callback        interactive callback for conflicts and alternative-only prompts
     * @return a ResolvedGraph describing chosen recipes and terminal leaf items
     * @throws SolverException if an item cannot be produced, has no compatible machine, or the user aborts
     */
    public ResolvedGraph resolve(
            Catalog catalog,
            CalculationMode mode,
            Set<UUID> targetItemIds,
            Set<UUID> userInputIds,
            Map<UUID, UUID> recipeOverrides,
            RecipeResolutionCallback callback
    ) {
        Map<UUID, Recipe> chosenRecipeByItem = new LinkedHashMap<>();
        Set<Recipe> activeRecipes = new LinkedHashSet<>();
        Set<UUID> leafItemIds = new LinkedHashSet<>();
        Set<UUID> visited = new LinkedHashSet<>();
        Deque<UUID> queue = new ArrayDeque<>(targetItemIds);

        while (!queue.isEmpty()) {
            UUID itemId = queue.poll();
            if (!visited.add(itemId)) {
                continue;
            }

            if (userInputIds.contains(itemId) && !targetItemIds.contains(itemId)) {
                leafItemIds.add(itemId);
                continue;
            }

            Item item = catalog.findItem(itemId)
                    .orElseThrow(() -> new SolverException("Item not found in catalog: " + itemId));

            UUID overrideRecipeId = recipeOverrides.get(itemId);
            Recipe chosen = null;

            if (overrideRecipeId != null) {
                Recipe overrideRecipe = catalog.findRecipe(overrideRecipeId)
                        .orElseThrow(() -> new SolverException("Selected recipe override no longer exists for '" + item.getName() + "'."));
                if (!overrideRecipe.producesOutput(itemId)) {
                    throw new SolverException("Overridden recipe '" + overrideRecipe.getName() + "' does not produce '" + item.getName() + "'.");
                }
                chosen = overrideRecipe;
            } else {
                List<Recipe> producers = catalog.getRecipesProducing(itemId);
                if (producers.isEmpty()) {
                    if (mode == CalculationMode.MAX_OUTPUT && !targetItemIds.contains(itemId)) {
                        leafItemIds.add(itemId);
                        continue;
                    }
                    throw new SolverException(
                            "The selected output cannot be produced: '" + item.getName()
                                    + "' has no producing recipe and is not listed among the supplied inputs.");
                }

                List<Recipe> originals = new ArrayList<>();
                List<Recipe> alternatives = new ArrayList<>();
                for (Recipe r : producers) {
                    if (r.isOriginal()) {
                        originals.add(r);
                    } else {
                        alternatives.add(r);
                    }
                }

                if (originals.size() == 1) {
                    chosen = originals.get(0);
                } else if (originals.size() > 1) {
                    if (callback == null) {
                        throw new SolverException("Multiple original recipes exist for '" + item.getName() + "'. Please select one.");
                    }
                    chosen = callback.chooseOriginalRecipe(item, originals);
                    if (chosen == null) {
                        throw new SolverException("Calculation cancelled: no original recipe selected for '" + item.getName() + "'.");
                    }
                } else {
                    if (callback == null) {
                        throw new SolverException(
                                "Item '" + item.getName() + "' can only be produced using alternative recipes.");
                    }
                    chosen = callback.confirmAlternativeRecipe(item, alternatives);
                    if (chosen == null) {
                        throw new SolverException(
                                "Calculation aborted: '" + item.getName() + "' only has alternative recipes.");
                    }
                }
            }

            if (catalog.getCompatibleMachines(chosen).isEmpty()) {
                throw new SolverException("Recipe '" + chosen.getName() + "' has no compatible machines in the catalog.");
            }

            chosenRecipeByItem.put(itemId, chosen);
            activeRecipes.add(chosen);

            for (ItemAmount input : chosen.getInputs()) {
                if (!visited.contains(input.getId())) {
                    queue.add(input.getId());
                }
            }
        }

        return new ResolvedGraph(chosenRecipeByItem, activeRecipes, leafItemIds);
    }
}
