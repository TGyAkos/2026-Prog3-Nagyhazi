package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.RecipeMachineSpec;
import hu.bme.prog3.factcalc.model.TimeUnit;
import hu.bme.prog3.factcalc.persistence.CatalogRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlternativeRecipeAndAutoTest {

    @Test
    void defaultModePromptsWhenMultipleOriginalRecipesExist() {
        Catalog catalog = new Catalog("Conflict");
        Item a = new Item(UUID.randomUUID(), "Raw A", "");
        Item b = new Item(UUID.randomUUID(), "Raw B", "");
        Item out = new Item(UUID.randomUUID(), "Product", "");
        Machine m = new Machine(UUID.randomUUID(), "Assembler", "assembler", "", 1.0);
        catalog.putItem(a);
        catalog.putItem(b);
        catalog.putItem(out);
        catalog.putItem(m);

        Recipe r1 = new Recipe(UUID.randomUUID(), "Orig 1", 1.0, "", true,
                List.of(RecipeMachineSpec.byType("assembler", 1.0)),
                List.of(new ItemAmount(a.getId(), 1.0)),
                List.of(new ItemAmount(out.getId(), 1.0)), List.of());
        Recipe r2 = new Recipe(UUID.randomUUID(), "Orig 2", 1.0, "", true,
                List.of(RecipeMachineSpec.byType("assembler", 1.0)),
                List.of(new ItemAmount(b.getId(), 1.0)),
                List.of(new ItemAmount(out.getId(), 2.0)), List.of());
        catalog.putRecipe(r1);
        catalog.putRecipe(r2);

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getInputs().add(new RateEntry(b.getId(), null, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(out.getId(), 4.0, TimeUnit.PER_SECOND));

        AtomicBoolean prompted = new AtomicBoolean(false);
        RecipeResolutionCallback callback = new RecipeResolutionCallback() {
            @Override
            public Recipe chooseOriginalRecipe(Item item, List<Recipe> originalCandidates) {
                prompted.set(true);
                assertEquals(2, originalCandidates.size());
                return r2;
            }

            @Override
            public Recipe confirmAlternativeRecipe(Item item, List<Recipe> alternativeCandidates) {
                return null;
            }
        };

        ProductionSolver solver = new ProductionSolver();
        ProductionPlanResult result = solver.solve(catalog, config, callback);
        assertTrue(prompted.get());
        assertEquals(r2.getId(), result.getNodes().get(0).getRecipeId());
        assertEquals(2.0, result.getUserInputs().get(0).getRequiredRatePerSecond(), 1e-9);
    }

    @Test
    void defaultModeWarnsAndAllowsAbortOrContinueWhenOnlyAlternativeExists() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID waterId = UUID.fromString("11111111-1111-1111-1111-111111111107");
        UUID coolantId = UUID.fromString("11111111-1111-1111-1111-111111111114");
        UUID steamId = UUID.fromString("11111111-1111-1111-1111-111111111115");

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getInputs().add(new RateEntry(coolantId, null, TimeUnit.PER_SECOND));
        config.getInputs().add(new RateEntry(waterId, null, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(steamId, 5.0, TimeUnit.PER_SECOND));

        ProductionSolver solver = new ProductionSolver();

        // Abort when callback returns null
        assertThrows(SolverException.class, () -> solver.solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst()));

        // Continue when user accepts alternative
        ProductionPlanResult result = solver.solve(catalog, config, new RecipeResolutionCallback() {
            @Override
            public Recipe chooseOriginalRecipe(Item item, List<Recipe> originalCandidates) {
                return originalCandidates.get(0);
            }

            @Override
            public Recipe confirmAlternativeRecipe(Item item, List<Recipe> alternativeCandidates) {
                return alternativeCandidates.get(0);
            }
        });
        assertEquals(1, result.getNodes().size());
        assertEquals(5.0, result.getFinalOutputs().get(0).getRate(), 1e-9);
    }

    @Test
    void autoModeMaximizesOutputAcrossAlternativeRecipes() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID copperOreId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID greenCircuitId = UUID.fromString("11111111-1111-1111-1111-111111111106");
        UUID altCircuitRecipeId = UUID.fromString("33333333-3333-3333-3333-333333333306");

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.MAX_OUTPUT);
        config.setAutoMode(true);
        config.getInputs().add(new RateEntry(ironOreId, 10.0, TimeUnit.PER_SECOND));
        config.getInputs().add(new RateEntry(copperOreId, 15.0, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(greenCircuitId, null, TimeUnit.PER_SECOND));

        // Original recipe yields 10/s (limited by 10 Iron Ore/s at 1:1).
        // Alt recipe yields 2 circuits per 1 Iron + 2 Wire (1 Copper Ore) -> 10 Iron => 20/s (needs 10 Copper <= 15)!
        ProductionSolver solver = new ProductionSolver();
        ProductionPlanResult result = solver.solve(catalog, config, null);

        assertEquals(20.0, result.getFinalOutputs().get(0).getRate(), 1e-9);
        assertTrue(result.getNodes().stream().anyMatch(n -> n.getRecipeId().equals(altCircuitRecipeId)));
    }
}
