package hu.bme.prog3.factcalc.ui;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.TimeUnit;
import hu.bme.prog3.factcalc.persistence.CatalogRepository;
import hu.bme.prog3.factcalc.solver.ProductionSolver;
import hu.bme.prog3.factcalc.solver.RecipeResolutionCallback;
import hu.bme.prog3.factcalc.ui.planner.DependencyTreeModel;
import hu.bme.prog3.factcalc.ui.planner.EdgesTableModel;
import hu.bme.prog3.factcalc.ui.planner.StepsTableModel;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.GraphicsEnvironment;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiModelsTest {

    @Test
    void dependencyTreeStepsAndEdgesModelsPopulateAndConvertUnits() throws Exception {
        Catalog catalog = new CatalogRepository().loadDefaultPreset();
        UUID woodId = UUID.fromString("11111111-1111-1111-1111-111111111108");
        UUID planksId = UUID.fromString("11111111-1111-1111-1111-111111111109");

        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(CalculationMode.TARGET_OUTPUT);
        config.getInputs().add(new RateEntry(woodId, null, TimeUnit.PER_SECOND));
        config.getTargets().add(new RateEntry(planksId, 4.0, TimeUnit.PER_SECOND));

        ProductionPlanResult result = new ProductionSolver().solve(catalog, config, RecipeResolutionCallback.nonInteractiveFirst());

        DefaultTreeModel treeModel = DependencyTreeModel.build(catalog, result, TimeUnit.PER_MINUTE);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        assertTrue(root.getChildCount() >= 1);

        StepsTableModel stepsModel = new StepsTableModel((_, _) -> {}, (_, _) -> {});
        stepsModel.setData(catalog, result, TimeUnit.PER_SECOND);
        assertEquals(1, stepsModel.getRowCount());
        assertFalse(stepsModel.isCellEditable(0, 8)); // Sawdust is not consumed by any active recipe here

        stepsModel.setDisplayUnit(TimeUnit.PER_HOUR);
        assertTrue(stepsModel.getValueAt(0, 6).toString().contains("item/hr"));

        EdgesTableModel edgesModel = new EdgesTableModel();
        edgesModel.setData(result, TimeUnit.PER_MINUTE);
        assertEquals(3, edgesModel.getRowCount()); // External Wood -> Recipe -> Final Planks + Byproduct Sawdust
    }

    @Test
    void mainFrameInitializesCleanlyOnEdtWhenDisplayAvailable() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            assertEquals("Factcalc - Untitled Catalog", frame.getTitle());
            frame.dispose();
        });
    }
}
