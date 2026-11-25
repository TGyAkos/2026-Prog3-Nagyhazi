package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.ProductionNode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetOutputSolverTest {

    @Test
    void solvesMultiStepChainWithMachineSelectionAndInputlessRecipe() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID copperOreId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID greenCircuitId = UUID.fromString("11111111-1111-1111-1111-111111111106");
        UUID smeltIronId = UUID.fromString("33333333-3333-3333-3333-333333333302");
        UUID smelterMk2Id = UUID.fromString("22222222-2222-2222-2222-222222222202");

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getInputs().add(new RateEntry(ironOreId, null, TimeUnit.PER_SECOND));
        config.getInputs().add(new RateEntry(copperOreId, null, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(greenCircuitId, 60.0, TimeUnit.PER_MINUTE)); // 1.0/s
        config.getMachineSelections().put(smeltIronId, smelterMk2Id);

        ProductionSolver solver = new ProductionSolver();
        ProductionPlanResult result = solver.solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst());

        // 1 Green Circuit/s -> 1 Iron Plate/s + 3 Copper Wire/s -> 1.5 Copper Plate/s
        // Iron Smelter Mk2 (effTime = 2.0 / 2.0 = 1.0s) -> 1.0 exact machine
        ProductionNode ironNode = result.getNodes().stream()
                .filter(n -> n.getRecipeId().equals(smeltIronId))
                .findFirst().orElseThrow();
        assertEquals(1.0, ironNode.getExactMachineCount(), 1e-9);
        assertEquals(1, ironNode.getCeilMachineCount());
        assertEquals(smelterMk2Id, ironNode.getMachineId());

        // Total inputs: 1.0 Iron Ore/s, 1.5 Copper Ore/s
        double ironConsumed = result.getUserInputs().stream()
                .filter(i -> i.getItemId().equals(ironOreId))
                .findFirst().orElseThrow().getRequiredRatePerSecond();
        double copperConsumed = result.getUserInputs().stream()
                .filter(i -> i.getItemId().equals(copperOreId))
                .findFirst().orElseThrow().getRequiredRatePerSecond();
        assertEquals(1.0, ironConsumed, 1e-9);
        assertEquals(1.5, copperConsumed, 1e-9);
    }

    @Test
    void rejectsIneligibleTargetsWithoutInputsOrRecipes() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID waterId = UUID.fromString("11111111-1111-1111-1111-111111111107"); // only inputless recipe
        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101"); // no recipe at all

        ProductionSolver solver = new ProductionSolver();

        ProductionPlanConfig waterConfig = new ProductionPlanConfig();
        waterConfig.setMode(CalculationMode.TARGET_OUTPUT);
        waterConfig.getTargets().add(new RateEntry(waterId, 10.0, TimeUnit.PER_SECOND));
        assertThrows(SolverException.class, () -> solver.solve(catalog, waterConfig, null));

        ProductionPlanConfig oreConfig = new ProductionPlanConfig();
        oreConfig.setMode(CalculationMode.TARGET_OUTPUT);
        oreConfig.getTargets().add(new RateEntry(ironOreId, 10.0, TimeUnit.PER_SECOND));
        assertThrows(SolverException.class, () -> solver.solve(catalog, oreConfig, null));
    }

    @Test
    void failsInTargetModeWhenLeafRawItemIsNotSupplied() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID greenCircuitId = UUID.fromString("11111111-1111-1111-1111-111111111106");

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getTargets().add(new RateEntry(greenCircuitId, 1.0, TimeUnit.PER_SECOND));
        // Missing Iron Ore and Copper Ore in user inputs

        ProductionSolver solver = new ProductionSolver();
        assertThrows(SolverException.class, () -> solver.solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst()));
    }

    @Test
    void supportsMultipleTargetsAndCoProductRecipes() {
        Catalog catalog = new Catalog("Multi");
        Item oil = new Item(UUID.randomUUID(), "Crude Oil", "");
        Item fuel = new Item(UUID.randomUUID(), "Fuel", "");
        Item plastic = new Item(UUID.randomUUID(), "Plastic", "");
        Machine refinery = new Machine(UUID.randomUUID(), "Refinery", "refinery", "", 1.0);
        catalog.putItem(oil);
        catalog.putItem(fuel);
        catalog.putItem(plastic);
        catalog.putItem(refinery);

        Recipe refine = new Recipe(
                UUID.randomUUID(), "Refine Oil", 2.0, "", true,
                List.of(RecipeMachineSpec.byType("refinery", 1.0)),
                List.of(new ItemAmount(oil.getId(), 10.0)),
                List.of(new ItemAmount(fuel.getId(), 5.0), new ItemAmount(plastic.getId(), 5.0)),
                List.of()
        );
        catalog.putRecipe(refine);

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getInputs().add(new RateEntry(oil.getId(), null, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(fuel.getId(), 10.0, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(plastic.getId(), 15.0, TimeUnit.PER_SECOND));

        ProductionSolver solver = new ProductionSolver();
        ProductionPlanResult result = solver.solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst());

        // Needs 3 cycles/sec to produce 15 Plastic/s -> also outputs 15 Fuel/s (>= 10 demanded)
        assertEquals(3.0, result.getNodes().get(0).getExecutionsPerSecond(), 1e-9);
        assertEquals(30.0, result.getUserInputs().get(0).getRequiredRatePerSecond(), 1e-9);
        assertTrue(result.getFinalOutputs().stream().anyMatch(e -> e.getItemId().equals(fuel.getId()) && e.getRate() >= 15.0 - 1e-9));
    }
}
