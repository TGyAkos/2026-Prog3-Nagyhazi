package hu.bme.prog3.factcalc.ui.planner;

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
import hu.bme.prog3.factcalc.service.CatalogService;
import hu.bme.prog3.factcalc.service.IconCacheService;
import org.junit.jupiter.api.Test;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerRefreshTest {

    @Test
    void newItemsMachinesAndRecipesUpdateSelectorsAndRecalculatePlan() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        CatalogService service = new CatalogService(catalog);
        IconCacheService iconCache = new IconCacheService();

        UUID copperOreId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID copperPlateId = UUID.fromString("11111111-1111-1111-1111-111111111104");
        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID smelterMk1Id = UUID.fromString("22222222-2222-2222-2222-222222222201");

        try {
            SwingUtilities.invokeAndWait(() -> {
                PlannerPanel planner = new PlannerPanel(service, iconCache);

                // Initial plan: 60 Copper Plate / min with Copper Ore supplied as unlimited input
                ProductionPlanConfig initialCfg = new ProductionPlanConfig();
                initialCfg.setMode(CalculationMode.TARGET_OUTPUT);
                initialCfg.setDisplayUnit(TimeUnit.PER_MINUTE);
                initialCfg.getTargets().add(new RateEntry(copperPlateId, 60.0, TimeUnit.PER_MINUTE));
                initialCfg.getInputs().add(new RateEntry(copperOreId, null, TimeUnit.PER_MINUTE));
                planner.getConfigPanel().loadConfig(initialCfg);
                planner.calculate();

                ProductionPlanResult initialResult = planner.getLastSolvedResult();
                assertNotNull(initialResult);
                assertEquals(1, initialResult.getNodes().size());
                assertEquals(2.0, initialResult.getNodes().get(0).getExactMachineCount(), 1e-6);

                // Editing an existing machine multiplier immediately recalculates the displayed plan
                service.updateMachine(smelterMk1Id, "Smelter Mk1", "smelter", "", 2.0);
                planner.refreshCatalog();

                ProductionPlanResult afterSmelterEdit = planner.getLastSolvedResult();
                assertNotNull(afterSmelterEdit);
                assertEquals(1.0, afterSmelterEdit.getNodes().get(0).getExactMachineCount(), 1e-6);

                // Add a new item, a new machine, an inputless recipe for the new item, and a recipe making Copper Ore
                Item additive = service.createItem("Special Additive", "");
                Machine specialSmelter = service.createMachine("Special Smelter", "smelter", "", 2.0);
                service.createRecipe(
                        "Extract Additive",
                        1.0,
                        "",
                        true,
                        List.of(RecipeMachineSpec.byType("assembler", 1.0)),
                        List.of(),
                        List.of(new ItemAmount(additive.getId(), 1.0)),
                        List.of()
                );
                service.createRecipe(
                        "Refine Copper Ore",
                        20.0,
                        "",
                        true,
                        List.of(RecipeMachineSpec.byId(specialSmelter.getId(), 1.0)),
                        List.of(new ItemAmount(additive.getId(), 0.5)),
                        List.of(new ItemAmount(copperOreId, 1.0)),
                        List.of(new ItemAmount(additive.getId(), 2.0))
                );

                planner.refreshCatalog();

                // 1. Input table editor must contain the newly added item and machine
                assertTrue(editorComboContainsItem(planner.getConfigPanel().getInputsTable(), additive.getId()));
                assertTrue(editorComboContainsItem(planner.getConfigPanel().getInputsTable(), specialSmelter.getId()));

                // 2. Copper Ore now has a recipe with inputs, so it becomes selectable as target output
                assertTrue(comboContainsItem(planner.getConfigPanel().getSingleTargetCombo().getModel(), copperOreId));
                assertTrue(editorComboContainsItem(planner.getConfigPanel().getTargetsTable(), copperOreId));

                // 3. Special Additive's only recipe has no inputs -> not eligible as target output yet
                assertFalse(comboContainsItem(planner.getConfigPanel().getSingleTargetCombo().getModel(), additive.getId()));

                // 4. Switching to MAX_OUTPUT mode with Copper Ore target and Special Additive input uses new entities
                ProductionPlanConfig maxCfg = new ProductionPlanConfig();
                maxCfg.setMode(CalculationMode.MAX_OUTPUT);
                maxCfg.setDisplayUnit(TimeUnit.PER_MINUTE);
                maxCfg.getTargets().add(new RateEntry(copperOreId, null, TimeUnit.PER_MINUTE));
                maxCfg.getInputs().add(new RateEntry(additive.getId(), 30.0, TimeUnit.PER_MINUTE));
                planner.getConfigPanel().loadConfig(maxCfg);
                planner.calculate();

                ProductionPlanResult maxResult = planner.getLastSolvedResult();
                assertNotNull(maxResult);
                assertEquals(1, maxResult.getNodes().size());
                ProductionNode oreStep = maxResult.getNodes().get(0);
                assertEquals("Refine Copper Ore", oreStep.getRecipeName());
                assertEquals("Special Smelter", oreStep.getMachineName());
                assertEquals(10.0, oreStep.getExactMachineCount(), 1e-6); // 1.0/s * (20.0 / 2.0)

                // 5. Updating the new machine's multiplier and refreshing immediately updates calculation page values
                service.updateMachine(specialSmelter.getId(), "Special Smelter Mk2", "smelter", "", 4.0);
                planner.refreshCatalog();

                ProductionPlanResult afterMachineEdit = planner.getLastSolvedResult();
                assertNotNull(afterMachineEdit);
                ProductionNode updatedOreStep = afterMachineEdit.getNodes().get(0);
                assertEquals("Special Smelter Mk2", updatedOreStep.getMachineName());
                assertEquals(5.0, updatedOreStep.getExactMachineCount(), 1e-6);

                // 6. Adding a recipe with inputs for Special Additive makes it selectable as target output
                service.createRecipe(
                        "Synthesize Additive",
                        2.0,
                        "",
                        true,
                        List.of(RecipeMachineSpec.byType("assembler", 1.0)),
                        List.of(new ItemAmount(ironOreId, 1.0)),
                        List.of(new ItemAmount(additive.getId(), 1.0)),
                        List.of()
                );
                planner.refreshCatalog();

                assertTrue(comboContainsItem(planner.getConfigPanel().getSingleTargetCombo().getModel(), additive.getId()));
                assertTrue(editorComboContainsItem(planner.getConfigPanel().getTargetsTable(), additive.getId()));
            });
        } finally {
            iconCache.shutdown();
        }
    }

    @Test
    void singleTargetSelectionAndReloadedItemReferencesArePreservedAcrossRefresh() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        CatalogService service = new CatalogService(catalog);
        IconCacheService iconCache = new IconCacheService();

        UUID greenCircuitId = UUID.fromString("11111111-1111-1111-1111-111111111106");
        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101");

        try {
            SwingUtilities.invokeAndWait(() -> {
                PlanConfigPanel configPanel = new PlanConfigPanel(
                        service.getCatalog(),
                        iconCache,
                        _ -> {},
                        () -> {},
                        () -> {},
                        () -> {}
                );

                ProductionPlanConfig cfg = new ProductionPlanConfig();
                cfg.setMode(CalculationMode.MAX_OUTPUT);
                cfg.getTargets().add(new RateEntry(greenCircuitId, null, TimeUnit.PER_MINUTE));
                cfg.getInputs().add(new RateEntry(ironOreId, 60.0, TimeUnit.PER_MINUTE));
                configPanel.loadConfig(cfg);

                // Adding a new item & recipe must preserve Green Circuit as the selected single target
                Item newItem = service.createItem("Circuit Housing", "");
                service.createRecipe(
                        "Mold Housing",
                        2.0,
                        "",
                        true,
                        List.of(RecipeMachineSpec.byType("assembler", 1.0)),
                        List.of(new ItemAmount(ironOreId, 1.0)),
                        List.of(new ItemAmount(newItem.getId(), 1.0)),
                        List.of()
                );
                configPanel.refreshCatalog(service.getCatalog());

                Item selected = (Item) configPanel.getSingleTargetCombo().getSelectedItem();
                assertNotNull(selected);
                assertEquals(greenCircuitId, selected.getId());

                // Reloading a fresh Catalog instance with renamed items rebinds table row references
                Catalog reloaded = new Catalog("Reloaded");
                for (Item item : service.getCatalog().getPlainItems()) {
                    Item copy = new Item(item);
                    if (item.getId().equals(ironOreId)) {
                        copy.setName("Pure Iron Ore");
                    }
                    reloaded.putItem(copy);
                }
                for (Machine m : service.getCatalog().getMachines()) {
                    reloaded.putItem(new Machine(m));
                }
                for (Recipe r : service.getCatalog().getRecipes()) {
                    reloaded.putRecipe(new Recipe(r));
                }

                configPanel.refreshCatalog(reloaded);
                Item rowItem = (Item) configPanel.getInputsTable().getValueAt(0, 0);
                assertNotNull(rowItem);
                assertEquals("Pure Iron Ore", rowItem.getName());
            });
        } finally {
            iconCache.shutdown();
        }
    }

    @Test
    void rateCellEditorAcceptsBlankWhenAllowedAndRejectsInvalidRates() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        IconCacheService iconCache = new IconCacheService();
        UUID ironOreId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID ironPlateId = UUID.fromString("11111111-1111-1111-1111-111111111102");

        try {
            SwingUtilities.invokeAndWait(() -> {
                PlanConfigPanel configPanel = new PlanConfigPanel(
                        catalog,
                        iconCache,
                        _ -> {},
                        () -> {},
                        () -> {},
                        () -> {}
                );
                ProductionPlanConfig cfg = new ProductionPlanConfig();
                cfg.setMode(CalculationMode.TARGET_OUTPUT);
                cfg.getTargets().add(new RateEntry(ironPlateId, 60.0, TimeUnit.PER_MINUTE));
                cfg.getInputs().add(new RateEntry(ironOreId, 120.0, TimeUnit.PER_MINUTE));
                configPanel.loadConfig(cfg);

                JTable inputsTable = configPanel.getInputsTable();
                inputsTable.editCellAt(0, 1);
                javax.swing.JTextField inputEditorField =
                        (javax.swing.JTextField) ((DefaultCellEditor) inputsTable.getCellEditor(0, 1)).getComponent();
                inputEditorField.setText("  ");
                assertTrue(inputsTable.getCellEditor().stopCellEditing());
                assertNull(inputsTable.getValueAt(0, 1));

                inputsTable.editCellAt(0, 1);
                inputEditorField.setText("-5");
                assertFalse(inputsTable.getCellEditor().stopCellEditing());
                inputsTable.getCellEditor().cancelCellEditing();

                JTable targetsTable = configPanel.getTargetsTable();
                targetsTable.editCellAt(0, 1);
                javax.swing.JTextField targetEditorField =
                        (javax.swing.JTextField) ((DefaultCellEditor) targetsTable.getCellEditor(0, 1)).getComponent();
                targetEditorField.setText("");
                assertFalse(targetsTable.getCellEditor().stopCellEditing());
                targetsTable.getCellEditor().cancelCellEditing();
            });
        } finally {
            iconCache.shutdown();
        }
    }

    private static boolean editorComboContainsItem(JTable table, UUID itemId) {
        TableCellEditor editor = table.getColumnModel().getColumn(0).getCellEditor();
        if (editor instanceof DefaultCellEditor dce && dce.getComponent() instanceof JComboBox<?> box) {
            return comboContainsItem(box.getModel(), itemId);
        }
        return false;
    }

    private static boolean comboContainsItem(ComboBoxModel<?> model, UUID itemId) {
        for (int i = 0; i < model.getSize(); i++) {
            Object elem = model.getElementAt(i);
            if (elem instanceof Item item && itemId.equals(item.getId())) {
                return true;
            }
        }
        return false;
    }
}
