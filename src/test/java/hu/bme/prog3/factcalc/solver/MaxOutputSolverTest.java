package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.TimeUnit;
import hu.bme.prog3.factcalc.persistence.CatalogRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxOutputSolverTest {

    @Test
    void computesMaxOutputWithBottleneckAndCalculatesMissingRequiredInputs() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID copperOreId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID greenCircuitId = UUID.fromString("11111111-1111-1111-1111-111111111106");

        // Supply only Iron Ore (10/s) and omit Copper Ore -> Copper Ore should be auto-calculated!
        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.MAX_OUTPUT);
        config.getInputs().add(new RateEntry(ironOreId, 10.0, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(greenCircuitId, null, TimeUnit.PER_SECOND));

        ProductionSolver solver = new ProductionSolver();
        ProductionPlanResult result = solver.solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst());

        // Original Green Circuit uses 1 Iron Plate (1 Iron Ore) + 3 Copper Wire (1.5 Copper Ore)
        assertEquals(10.0, result.getFinalOutputs().get(0).getRate(), 1e-9);
        assertTrue(result.getUserInputs().get(0).isBottleneck());
        assertEquals(1, result.getCalculatedMissingInputs().size());
        assertEquals(copperOreId, result.getCalculatedMissingInputs().get(0).getItemId());
        assertEquals(15.0, result.getCalculatedMissingInputs().get(0).getRate(), 1e-9);
    }

    @Test
    void identifiesBottleneckAmongMultipleUserInputs() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID copperOreId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID greenCircuitId = UUID.fromString("11111111-1111-1111-1111-111111111106");

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.MAX_OUTPUT);
        config.getInputs().add(new RateEntry(ironOreId, 20.0, TimeUnit.PER_SECOND));
        config.getInputs().add(new RateEntry(copperOreId, 15.0, TimeUnit.PER_SECOND)); // bottleneck: 15/1.5 = 10/s
        config.getTargets().add(new RateEntry(greenCircuitId, null, TimeUnit.PER_SECOND));

        ProductionSolver solver = new ProductionSolver();
        ProductionPlanResult result = solver.solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst());

        assertEquals(10.0, result.getFinalOutputs().get(0).getRate(), 1e-9);
        assertFalse(result.getUserInputs().get(0).isBottleneck());
        assertTrue(result.getUserInputs().get(1).isBottleneck());
    }

    @Test
    void throwsErrorWhenNoneOfTheUserInputsAreUsed() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID woodId = UUID.fromString("11111111-1111-1111-1111-111111111108");
        UUID ironGearId = UUID.fromString("11111111-1111-1111-1111-111111111113");

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.MAX_OUTPUT);
        config.getInputs().add(new RateEntry(woodId, 10.0, TimeUnit.PER_SECOND)); // unused for Iron Gear!
        config.getTargets().add(new RateEntry(ironGearId, null, TimeUnit.PER_SECOND));

        ProductionSolver solver = new ProductionSolver();
        assertThrows(SolverException.class, () -> solver.solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst()));
    }
}
