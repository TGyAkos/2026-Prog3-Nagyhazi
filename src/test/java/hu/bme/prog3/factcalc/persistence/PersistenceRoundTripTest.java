package hu.bme.prog3.factcalc.persistence;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.SavedPlan;
import hu.bme.prog3.factcalc.model.TimeUnit;
import hu.bme.prog3.factcalc.solver.ProductionSolver;
import hu.bme.prog3.factcalc.solver.RecipeResolutionCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void catalogPresetAndSaveLoadRoundTrip() throws Exception {
        CatalogRepository repo = new CatalogRepository();
        Catalog preset = repo.loadDefaultPreset();
        assertFalse(preset.getPlainItems().isEmpty());
        assertFalse(preset.getMachines().isEmpty());
        assertFalse(preset.getRecipes().isEmpty());

        Path outFile = tempDir.resolve("catalog.json");
        repo.save(preset, outFile);
        Catalog reloaded = repo.load(outFile);

        assertEquals(preset.getName(), reloaded.getName());
        assertEquals(preset.getPlainItems().size(), reloaded.getPlainItems().size());
        assertEquals(preset.getMachines().size(), reloaded.getMachines().size());
        assertEquals(preset.getRecipes().size(), reloaded.getRecipes().size());
    }

    @Test
    void savedPlanRoundTripAndCatalogValidation() throws Exception {
        CatalogRepository catalogRepo = new CatalogRepository();
        Catalog catalog = catalogRepo.loadDefaultPreset();

        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID copperOreId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID greenCircuitId = UUID.fromString("11111111-1111-1111-1111-111111111106");

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.setDisplayUnit(TimeUnit.PER_MINUTE);
        config.getInputs().add(new RateEntry(ironOreId, null, TimeUnit.PER_MINUTE));
        config.getInputs().add(new RateEntry(copperOreId, null, TimeUnit.PER_MINUTE));
        config.getTargets().add(new RateEntry(greenCircuitId, 60.0, TimeUnit.PER_MINUTE));

        ProductionSolver solver = new ProductionSolver();
        ProductionPlanResult result = solver.solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst());

        SavedPlan saved = new SavedPlan("Green Circuit Line", catalog.getName(), Instant.now(), config, result);
        PlanRepository planRepo = new PlanRepository();
        Path planPath = tempDir.resolve("test.factplan.json");
        planRepo.save(saved, planPath);

        SavedPlan reloaded = planRepo.load(planPath);
        assertEquals("Green Circuit Line", reloaded.getPlanName());
        assertTrue(planRepo.validateAgainstCatalog(reloaded, catalog).isEmpty());
        assertEquals(result.getNodes().size(), reloaded.getResult().getNodes().size());

        // Validate mismatch against empty catalog
        List<String> missing = planRepo.validateAgainstCatalog(reloaded, new Catalog("Empty"));
        assertFalse(missing.isEmpty());
    }
}
