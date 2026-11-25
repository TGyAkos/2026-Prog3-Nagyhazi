package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.ProductionNode;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.TimeUnit;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Table model for solved production steps with interactive Machine selector
 * and Recursive Reuse toggle columns.
 */
public class StepsTableModel extends AbstractTableModel {

    private final String[] cols = {
            "Recipe",
            "Type",
            "Machine",
            "Eff. Time (s)",
            "Machines Needed",
            "Inputs",
            "Outputs",
            "Byproducts",
            "Recursive Reuse"
    };

    private Catalog catalog;
    private ProductionPlanResult result;
    private TimeUnit displayUnit = TimeUnit.PER_MINUTE;
    private List<ProductionNode> nodes = new ArrayList<>();

    private final BiConsumer<UUID, UUID> onMachineChanged;
    private final BiConsumer<UUID, Boolean> onRecycleToggled;

    /**
     * Creates a table model for displaying solved production steps and relaying user edits.
     *
     * @param onMachineChanged callback receiving {@code (recipeId, machineId)} when a machine is changed
     * @param onRecycleToggled callback receiving {@code (byproductItemId, enabled)} when recycling is toggled
     */
    public StepsTableModel(
            BiConsumer<UUID, UUID> onMachineChanged,
            BiConsumer<UUID, Boolean> onRecycleToggled
    ) {
        this.onMachineChanged = onMachineChanged;
        this.onRecycleToggled = onRecycleToggled;
    }

    /**
     * Replaces the displayed catalog, production plan result, and rate unit, and refreshes the table.
     *
     * @param catalog     the active factory catalog
     * @param result      the solved production plan result (may be {@code null} to clear rows)
     * @param displayUnit the time unit used to format flow rates (defaults to {@link TimeUnit#PER_MINUTE} if {@code null})
     */
    public void setData(Catalog catalog, ProductionPlanResult result, TimeUnit displayUnit) {
        this.catalog = catalog;
        this.result = result;
        this.displayUnit = displayUnit == null ? TimeUnit.PER_MINUTE : displayUnit;
        this.nodes = result == null ? new ArrayList<>() : new ArrayList<>(result.getNodes());
        fireTableDataChanged();
    }

    /**
     * Updates the display time unit used to format input, output, and byproduct rates and refreshes the table.
     *
     * @param displayUnit the new time unit (defaults to {@link TimeUnit#PER_MINUTE} if {@code null})
     */
    public void setDisplayUnit(TimeUnit displayUnit) {
        this.displayUnit = displayUnit == null ? TimeUnit.PER_MINUTE : displayUnit;
        fireTableDataChanged();
    }

    /**
     * Returns the {@link ProductionNode} at the given model row index.
     *
     * @param modelRow zero-based model row index
     * @return the corresponding {@link ProductionNode}, or {@code null} if out of bounds
     */
    public ProductionNode getNodeAt(int modelRow) {
        if (modelRow < 0 || modelRow >= nodes.size()) {
            return null;
        }
        return nodes.get(modelRow);
    }

    /**
     * Returns the number of production step rows in the table.
     *
     * @return the row count
     */
    @Override
    public int getRowCount() {
        return nodes.size();
    }

    /**
     * Returns the number of columns in the steps table.
     *
     * @return the column count
     */
    @Override
    public int getColumnCount() {
        return cols.length;
    }

    /**
     * Returns the display header for the specified column index.
     *
     * @param column zero-based column index
     * @return the column title
     */
    @Override
    public String getColumnName(int column) {
        return cols[column];
    }

    /**
     * Returns the value class used for table rendering and editing.
     *
     * @param columnIndex zero-based column index
     * @return {@link Boolean}{@code .class} for the recursive reuse column, or {@link String}{@code .class} otherwise
     */
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 8 -> Boolean.class;
            default -> String.class;
        };
    }

    /**
     * Returns whether the given cell can be edited (Machine column, or Recursive Reuse when a recyclable byproduct exists).
     *
     * @param rowIndex    zero-based model row index
     * @param columnIndex zero-based column index
     * @return {@code true} if the cell is interactive, {@code false} otherwise
     */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        if (columnIndex == 2) {
            return true;
        }
        if (columnIndex == 8) {
            return findFirstRecyclableByproduct(nodes.get(rowIndex)) != null;
        }
        return false;
    }

    /**
     * Returns the formatted cell value or recycling boolean for the specified production step and column.
     *
     * @param rowIndex    zero-based model row index
     * @param columnIndex zero-based column index
     * @return the cell value object
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ProductionNode node = nodes.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> node.getRecipeName();
            case 1 -> node.isOriginalRecipe() ? "Original" : "Alternative";
            case 2 -> node.getMachineName();
            case 3 -> String.format("%.2f", node.getEffectiveTimeSeconds());
            case 4 -> String.format("%.2f (%d pcs)", node.getExactMachineCount(), node.getCeilMachineCount());
            case 5 -> formatRates(node.getInputs());
            case 6 -> formatRates(node.getOutputs());
            case 7 -> formatRates(node.getByproducts());
            case 8 -> {
                UUID bypId = findFirstRecyclableByproduct(node);
                if (bypId == null || result == null) {
                    yield Boolean.FALSE;
                }
                yield Boolean.TRUE.equals(result.getResolvedRecycling().get(bypId));
            }
            default -> "";
        };
    }

    /**
     * Dispatches machine-selection changes or byproduct-recycling toggles to the configured callbacks.
     *
     * @param aValue      the newly committed cell value ({@link Machine} or {@link Boolean})
     * @param rowIndex    zero-based model row index
     * @param columnIndex zero-based column index
     */
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        ProductionNode node = nodes.get(rowIndex);
        if (columnIndex == 2 && aValue instanceof Machine m) {
            onMachineChanged.accept(node.getRecipeId(), m.getId());
        } else if (columnIndex == 8 && aValue instanceof Boolean b) {
            UUID bypId = findFirstRecyclableByproduct(node);
            if (bypId != null) {
                onRecycleToggled.accept(bypId, b);
            }
        }
    }

    /**
     * Finds the first byproduct produced by the given node that is also consumed somewhere in the solved plan.
     *
     * @param node the production step node to inspect
     * @return the item ID of the first recyclable byproduct, or {@code null} if none exists
     */
    public UUID findFirstRecyclableByproduct(ProductionNode node) {
        if (result == null || node == null) {
            return null;
        }
        for (RateEntry byp : node.getByproducts()) {
            if (result.getRecyclableByproductIds().contains(byp.getItemId())) {
                return byp.getItemId();
            }
        }
        return null;
    }

    /**
     * Formats a list of per-second rate entries using the current display time unit and catalog item names.
     *
     * @param entries list of rate entries to format
     * @return comma-separated formatted rates, or {@code "-"} if empty
     */
    private String formatRates(List<RateEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "-";
        }
        return entries.stream()
                .map(e -> {
                    String itemName = catalog != null
                            ? catalog.findItem(e.getItemId()).map(Item::getName).orElse("?")
                            : "?";
                    double val = displayUnit.fromPerSecond(e.getRate());
                    return String.format("%.2f %s %s", val, displayUnit.getLabel(), itemName);
                })
                .collect(Collectors.joining(", "));
    }
}
