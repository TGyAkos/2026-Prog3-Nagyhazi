package hu.bme.prog3.factcalc.solver;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.FlowEdge;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.RecipeMachineSpec;
import hu.bme.prog3.factcalc.model.SolverNotice;
import hu.bme.prog3.factcalc.model.TimeUnit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecursiveByproductTest {

    @Test
    void partialOffsetAndByproductOutputToggle() {
        Catalog catalog = new Catalog("Partial");
        Item cat = new Item(UUID.randomUUID(), "Catalyst", "");
        Item water = new Item(UUID.randomUUID(), "Water", "");
        Item out = new Item(UUID.randomUUID(), "Solution", "");
        Machine m = new Machine(UUID.randomUUID(), "Chem", "chem", "", 1.0);
        catalog.putItem(cat);
        catalog.putItem(water);
        catalog.putItem(out);
        catalog.putItem(m);

        // Consumes 2 Catalyst + 1 Water -> 1 Solution + 1 Catalyst byproduct
        Recipe r = new Recipe(UUID.randomUUID(), "Mix", 1.0, "", true,
                List.of(RecipeMachineSpec.byType("chem", 1.0)),
                List.of(new ItemAmount(cat.getId(), 2.0), new ItemAmount(water.getId(), 1.0)),
                List.of(new ItemAmount(out.getId(), 1.0)),
                List.of(new ItemAmount(cat.getId(), 1.0)));
        catalog.putRecipe(r);

        ProductionSolver solver = new ProductionSolver();

        // 1. Not recycled: needs 2 Catalyst/s, outputs 1 Catalyst/s as byproduct
        ProductionPlanConfig noRecycle = new ProductionPlanConfig();
        noRecycle.setMode(CalculationMode.TARGET_OUTPUT);
        noRecycle.getInputs().add(new RateEntry(cat.getId(), null, TimeUnit.PER_SECOND));
        noRecycle.getInputs().add(new RateEntry(water.getId(), null, TimeUnit.PER_SECOND));
        noRecycle.getTargets().add(new RateEntry(out.getId(), 1.0, TimeUnit.PER_SECOND));
        noRecycle.getRecycledByproducts().put(cat.getId(), false);

        ProductionPlanResult r1 = solver.solve(catalog, noRecycle, RecipeResolutionCallback.nonInteractiveFirst());
        assertTrue(r1.getRecyclableByproductIds().contains(cat.getId()));
        assertEquals(2.0, r1.getUserInputs().get(0).getRequiredRatePerSecond(), 1e-9);
        assertEquals(1.0, r1.getByproductOutputs().get(0).getRate(), 1e-9);

        // 2. Recycled: needs only 1 Catalyst/s external, 0 byproduct output, 1 recursive feedback edge
        ProductionPlanConfig recycle = new ProductionPlanConfig(noRecycle);
        recycle.getRecycledByproducts().put(cat.getId(), true);

        ProductionPlanResult r2 = solver.solve(catalog, recycle, RecipeResolutionCallback.nonInteractiveFirst());
        assertEquals(1.0, r2.getUserInputs().get(0).getRequiredRatePerSecond(), 1e-9);
        assertTrue(r2.getByproductOutputs().isEmpty());
        assertTrue(r2.getEdges().stream().anyMatch(e -> e.getType() == FlowEdge.EdgeType.RECURSIVE_FEEDBACK && Math.abs(e.getRatePerSecond() - 1.0) < 1e-9));
    }

    @Test
    void selfSustainingLoopProducesInfoNoticeAndZeroExternalInput() {
        Catalog catalog = new Catalog("SelfSustaining");
        Item cat = new Item(UUID.randomUUID(), "Coolant", "");
        Item water = new Item(UUID.randomUUID(), "Water", "");
        Item steam = new Item(UUID.randomUUID(), "Steam", "");
        Machine m = new Machine(UUID.randomUUID(), "Chem", "chem", "", 1.0);
        catalog.putItem(cat);
        catalog.putItem(water);
        catalog.putItem(steam);
        catalog.putItem(m);

        // Consumes 2 Coolant + 5 Water -> 5 Steam + 2 Coolant byproduct
        Recipe r = new Recipe(UUID.randomUUID(), "Cool", 1.0, "", true,
                List.of(RecipeMachineSpec.byType("chem", 1.0)),
                List.of(new ItemAmount(cat.getId(), 2.0), new ItemAmount(water.getId(), 5.0)),
                List.of(new ItemAmount(steam.getId(), 5.0)),
                List.of(new ItemAmount(cat.getId(), 2.0)));
        catalog.putRecipe(r);

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getInputs().add(new RateEntry(cat.getId(), null, TimeUnit.PER_SECOND));
        config.getInputs().add(new RateEntry(water.getId(), null, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(steam.getId(), 5.0, TimeUnit.PER_SECOND));
        config.getRecycledByproducts().put(cat.getId(), true);

        ProductionPlanResult result = new ProductionSolver().solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst());
        assertEquals(0.0, result.getUserInputs().get(0).getRequiredRatePerSecond(), 1e-9);
        assertTrue(result.getNotices().stream().anyMatch(n -> n.getLevel() == SolverNotice.Level.INFO && n.getMessage().contains("Self-sustaining")));
    }

    @Test
    void positiveFeedbackWithoutExternalInputThrowsError() {
        Catalog catalog = new Catalog("PositiveFeedbackError");
        Item cat = new Item(UUID.randomUUID(), "BioMass", "");
        Item out = new Item(UUID.randomUUID(), "Energy", "");
        Machine m = new Machine(UUID.randomUUID(), "Reactor", "reactor", "", 1.0);
        catalog.putItem(cat);
        catalog.putItem(out);
        catalog.putItem(m);

        // Only input is BioMass (1) and byproduct is 2 BioMass -> positive feedback with NO other external input!
        Recipe r = new Recipe(UUID.randomUUID(), "Breed", 1.0, "", true,
                List.of(RecipeMachineSpec.byType("reactor", 1.0)),
                List.of(new ItemAmount(cat.getId(), 1.0)),
                List.of(new ItemAmount(out.getId(), 1.0)),
                List.of(new ItemAmount(cat.getId(), 2.0)));
        catalog.putRecipe(r);

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getInputs().add(new RateEntry(cat.getId(), null, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(out.getId(), 1.0, TimeUnit.PER_SECOND));
        config.getRecycledByproducts().put(cat.getId(), true);

        assertThrows(SolverException.class, () ->
                new ProductionSolver().solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst()));
    }

    @Test
    void surplusByproductWithOtherExternalInputEmitsSurplusAsByproductOutput() {
        Catalog catalog = new Catalog("ValidSurplus");
        Item cat = new Item(UUID.randomUUID(), "BioMass", "");
        Item water = new Item(UUID.randomUUID(), "Water", "");
        Item out = new Item(UUID.randomUUID(), "BioFuel", "");
        Machine m = new Machine(UUID.randomUUID(), "Reactor", "reactor", "", 1.0);
        catalog.putItem(cat);
        catalog.putItem(water);
        catalog.putItem(out);
        catalog.putItem(m);

        // Input: 1 BioMass + 3 Water -> 1 BioFuel + 2 BioMass byproduct (other external input Water exists!)
        Recipe r = new Recipe(UUID.randomUUID(), "Ferment", 1.0, "", true,
                List.of(RecipeMachineSpec.byType("reactor", 1.0)),
                List.of(new ItemAmount(cat.getId(), 1.0), new ItemAmount(water.getId(), 3.0)),
                List.of(new ItemAmount(out.getId(), 1.0)),
                List.of(new ItemAmount(cat.getId(), 2.0)));
        catalog.putRecipe(r);

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getInputs().add(new RateEntry(cat.getId(), null, TimeUnit.PER_SECOND));
        config.getInputs().add(new RateEntry(water.getId(), null, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(out.getId(), 1.0, TimeUnit.PER_SECOND));
        config.getRecycledByproducts().put(cat.getId(), true);

        ProductionPlanResult result = new ProductionSolver().solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst());
        assertEquals(0.0, result.getUserInputs().get(0).getRequiredRatePerSecond(), 1e-9);
        assertEquals(3.0, result.getUserInputs().get(1).getRequiredRatePerSecond(), 1e-9);
        assertEquals(1, result.getByproductOutputs().size());
        assertEquals(cat.getId(), result.getByproductOutputs().get(0).getItemId());
        assertEquals(1.0, result.getByproductOutputs().get(0).getRate(), 1e-9);
    }
}
