package hu.bme.prog3.factcalc.ui.components;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.ProductionNode;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.service.IconCacheService;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTable;
import java.awt.Component;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Inline JTable cell editor for choosing a compatible Machine for a solved production step.
 */
public class MachineComboBoxEditor extends DefaultCellEditor {
    private final JComboBox<Machine> comboBox;
    private final Supplier<Catalog> catalogSupplier;
    private final IntFunction<ProductionNode> rowNodeLookup;

    /**
     * Creates a table cell editor with a combo box populated dynamically with compatible machines.
     *
     * @param catalogSupplier supplier returning the active catalog
     * @param iconCache       icon cache service for rendering machine icons in the dropdown
     * @param rowNodeLookup   function mapping a model row index to its {@link ProductionNode}
     */
    public MachineComboBoxEditor(
            Supplier<Catalog> catalogSupplier,
            IconCacheService iconCache,
            IntFunction<ProductionNode> rowNodeLookup
    ) {
        super(new JComboBox<Machine>());
        @SuppressWarnings("unchecked")
        JComboBox<Machine> cb = (JComboBox<Machine>) getComponent();
        this.comboBox = cb;
        this.catalogSupplier = catalogSupplier;
        this.rowNodeLookup = rowNodeLookup;
        this.comboBox.setRenderer(new ItemComboBoxRenderer(iconCache));
    }

    /**
     * Populates the combo box with machines compatible with the row's recipe and selects the current machine.
     *
     * @param table      the JTable asking the editor to configure itself
     * @param value      the current cell value
     * @param isSelected {@code true} if the cell is selected
     * @param row        the view row index being edited
     * @param column     the view column index being edited
     * @return the configured combo box editor component
     */
    @Override
    public Component getTableCellEditorComponent(
            JTable table,
            Object value,
            boolean isSelected,
            int row,
            int column
    ) {
        int modelRow = table.convertRowIndexToModel(row);
        ProductionNode node = rowNodeLookup.apply(modelRow);
        DefaultComboBoxModel<Machine> model = new DefaultComboBoxModel<>();
        Catalog catalog = catalogSupplier.get();
        if (catalog != null && node != null) {
            Recipe recipe = catalog.findRecipe(node.getRecipeId()).orElse(null);
            if (recipe != null) {
                List<Machine> compatible = catalog.getCompatibleMachines(recipe);
                Machine selected = null;
                for (Machine m : compatible) {
                    model.addElement(m);
                    if (m.getId().equals(node.getMachineId())) {
                        selected = m;
                    }
                }
                if (selected != null) {
                    model.setSelectedItem(selected);
                }
            }
        }
        comboBox.setModel(model);
        return super.getTableCellEditorComponent(table, model.getSelectedItem(), isSelected, row, column);
    }
}
