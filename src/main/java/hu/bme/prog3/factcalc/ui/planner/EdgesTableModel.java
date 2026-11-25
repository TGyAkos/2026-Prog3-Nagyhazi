package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.FlowEdge;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.TimeUnit;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class EdgesTableModel extends AbstractTableModel {
    private final String[] cols = {"Source", "Item", "Rate", "Target", "Connection Type"};
    private List<FlowEdge> edges = new ArrayList<>();
    private TimeUnit displayUnit = TimeUnit.PER_MINUTE;

    /**
     * Replaces the edge list from the given {@link ProductionPlanResult} and updates the rate display unit.
     *
     * @param result      the solved production plan result (may be {@code null} to clear the table)
     * @param displayUnit the time unit used to format flow rates (defaults to {@link TimeUnit#PER_MINUTE} if {@code null})
     */
    public void setData(ProductionPlanResult result, TimeUnit displayUnit) {
        this.edges = result == null ? new ArrayList<>() : new ArrayList<>(result.getEdges());
        this.displayUnit = displayUnit == null ? TimeUnit.PER_MINUTE : displayUnit;
        fireTableDataChanged();
    }

    /**
     * Updates the display time unit used to format edge flow rates and refreshes the table.
     *
     * @param displayUnit the new time unit (defaults to {@link TimeUnit#PER_MINUTE} if {@code null})
     */
    public void setDisplayUnit(TimeUnit displayUnit) {
        this.displayUnit = displayUnit == null ? TimeUnit.PER_MINUTE : displayUnit;
        fireTableDataChanged();
    }

    /**
     * Returns the number of flow edge rows in the table.
     *
     * @return the row count
     */
    @Override
    public int getRowCount() {
        return edges.size();
    }

    /**
     * Returns the number of columns in the edges table.
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
     * Returns the source, item, formatted rate, target, or connection type label at the specified cell.
     *
     * @param rowIndex    zero-based model row index
     * @param columnIndex zero-based column index
     * @return the cell display value
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FlowEdge edge = edges.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> edge.getSourceLabel();
            case 1 -> edge.getItemName();
            case 2 -> DependencyTreeModel.formatRate(edge.getRatePerSecond(), displayUnit);
            case 3 -> edge.getTargetLabel();
            case 4 -> edge.getType() != null ? edge.getType().getLabel() : "";
            default -> "";
        };
    }
}
