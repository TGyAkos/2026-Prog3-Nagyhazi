package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.Recipe;

import java.util.List;

/**
 * Interactive callback invoked when the solver encounters multiple original recipes
 * or an item that can only be produced using alternative recipes.
 */
public interface RecipeResolutionCallback {

    /**
     * Called when an item has more than one original recipe so the user can pick one.
     *
     * @param item               the item requiring recipe disambiguation
     * @param originalCandidates list of original recipes producing the item
     * @return the chosen recipe, or null to abort calculation
     */
    Recipe chooseOriginalRecipe(Item item, List<Recipe> originalCandidates);

    /**
     * Called in default mode when an item has zero original recipes and at least one alternative recipe.
     *
     * @param item                  the item requiring an alternative recipe
     * @param alternativeCandidates list of alternative recipes producing the item
     * @return the chosen alternative recipe to continue, or null to abort calculation
     */
    Recipe confirmAlternativeRecipe(Item item, List<Recipe> alternativeCandidates);

    /**
     * Creates a non-interactive callback suitable for automated tests: picks the first original
     * recipe on conflict and aborts (returns null) when only alternative recipes exist.
     *
     * @return non-interactive RecipeResolutionCallback instance
     */
    static RecipeResolutionCallback nonInteractiveFirst() {
        return new RecipeResolutionCallback() {
            @Override
            public Recipe chooseOriginalRecipe(Item item, List<Recipe> originalCandidates) {
                return originalCandidates.isEmpty() ? null : originalCandidates.get(0);
            }

            @Override
            public Recipe confirmAlternativeRecipe(Item item, List<Recipe> alternativeCandidates) {
                return null;
            }
        };
    }
}
