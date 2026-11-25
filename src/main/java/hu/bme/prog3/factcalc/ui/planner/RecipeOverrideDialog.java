package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.service.IconCacheService;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Visual alternative/preferred recipe selector allowing the user to override
 * which recipe is used for any item with multiple or alternative recipes.
 */
public class RecipeOverrideDialog extends JDialog {

    private static final class RecipeOption {
        final Recipe recipe; // null means Automatic (Default)

        /**
         * Wraps a recipe choice for the override dropdown, where {@code null} denotes automatic selection.
         *
         * @param recipe the chosen recipe, or {@code null} for automatic default resolution
         */
        RecipeOption(Recipe recipe) {
            this.recipe = recipe;
        }

        /**
         * Returns the formatted label indicating automatic selection or original/alternative recipe status.
         *
         * @return the dropdown display string
         */
        @Override
        public String toString() {
            if (recipe == null) {
                return "-- Automatic (Default Original) --";
            }
            return (recipe.isOriginal() ? "[Original] " : "[Alternative] ") + recipe.getName();
        }
    }

    private static final class OverrideRow {
        final Item item;
        final List<Recipe> recipes;
        Recipe selected; // null = automatic

        /**
         * Creates a table row representing an item and its selectable producer recipes.
         *
         * @param item     the producible item
         * @param recipes  all recipes in the catalog that produce {@code item}
         * @param selected currently overridden recipe, or {@code null} for automatic selection
         */
        OverrideRow(Item item, List<Recipe> recipes, Recipe selected) {
            this.item = item;
            this.recipes = recipes;
            this.selected = selected;
        }
    }

    private final Catalog catalog;
    private final IconCacheService iconCache;
    private final OverrideTableModel tableModel = new OverrideTableModel();
    private final JTable table = new JTable(tableModel);
    private boolean confirmed = false;

    /**
     * Creates a modal dialog displaying all items that have multiple or alternative recipes so the user
     * can configure explicit recipe overrides.
     *
     * @param owner            the parent window for modal centering
     * @param catalog          the active factory catalog
     * @param currentOverrides map of item IDs to currently overridden recipe IDs
     * @param focusItemId      optional item ID to pre-select and highlight in the table
     * @param iconCache        icon cache service used to render item and recipe icons
     */
    public RecipeOverrideDialog(
            Window owner,
            Catalog catalog,
            Map<UUID, UUID> currentOverrides,
            UUID focusItemId,
            IconCacheService iconCache
    ) {
        super(owner, "Select Recipes (Original / Alternative)", ModalityType.APPLICATION_MODAL);
        this.catalog = catalog;
        this.iconCache = iconCache;

        for (Item item : catalog.getAllItems()) {
            List<Recipe> producers = catalog.getRecipesProducing(item.getId());
            boolean hasAlt = producers.stream().anyMatch(r -> !r.isOriginal());
            if (producers.size() > 1 || hasAlt || item.getId().equals(focusItemId)) {
                if (!producers.isEmpty()) {
                    UUID ovId = currentOverrides.get(item.getId());
                    Recipe ov = ovId != null ? catalog.findRecipe(ovId).orElse(null) : null;
                    tableModel.rows.add(new OverrideRow(item, producers, ov));
                }
            }
        }

        buildUi(focusItemId);
        setMinimumSize(new Dimension(640, 380));
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Builds the override table, cell editor, optional initial row selection, and Reset/Apply/Cancel buttons.
     *
     * @param focusItemId optional item ID to scroll/select initially, or {@code null}
     */
    private void buildUi(UUID focusItemId) {
        JLabel info = new JLabel("Choose specific original or alternative recipes to override automatic selection:");
        info.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));

        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setMaxWidth(48);
        table.getColumnModel().getColumn(2).setCellEditor(new RecipeCellEditor());

        if (focusItemId != null) {
            for (int i = 0; i < tableModel.rows.size(); i++) {
                if (tableModel.rows.get(i).item.getId().equals(focusItemId)) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearAllBtn = new JButton("Reset All to Automatic");
        JButton okBtn = new JButton("Apply");
        JButton cancelBtn = new JButton("Cancel");

        clearAllBtn.addActionListener(_ -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            for (OverrideRow row : tableModel.rows) {
                row.selected = null;
            }
            tableModel.fireTableDataChanged();
        });
        okBtn.addActionListener(_ -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            confirmed = true;
            dispose();
        });
        cancelBtn.addActionListener(_ -> dispose());

        buttons.add(clearAllBtn);
        buttons.add(okBtn);
        buttons.add(cancelBtn);
        getRootPane().setDefaultButton(okBtn);

        setLayout(new BorderLayout(8, 8));
        add(info, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    /**
     * Returns whether the user confirmed applying recipe overrides.
     *
     * @return {@code true} if Apply was clicked, {@code false} if cancelled
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Builds and returns the map of item IDs to explicitly chosen producer recipe IDs.
     *
     * @return map containing only items with non-automatic overrides selected
     */
    public Map<UUID, UUID> getOverrides() {
        Map<UUID, UUID> map = new LinkedHashMap<>();
        for (OverrideRow row : tableModel.rows) {
            if (row.selected != null) {
                map.put(row.item.getId(), row.selected.getId());
            }
        }
        return map;
    }

    private final class OverrideTableModel extends AbstractTableModel {
        private final String[] cols = {"Icon", "Item", "Selected Recipe"};
        final List<OverrideRow> rows = new ArrayList<>();

        /**
         * Returns the number of overridable item rows.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of columns in the override table.
         *
         * @return the column count
         */
        @Override
        public int getColumnCount() {
            return cols.length;
        }

        /**
         * Returns the display header for the given column index.
         *
         * @param column zero-based column index
         * @return the column title
         */
        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        /**
         * Allows editing only in the Selected Recipe column.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return {@code true} for column 2, {@code false} otherwise
         */
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2;
        }

        /**
         * Returns the cell value class used by the table renderer.
         *
         * @param columnIndex zero-based column index
         * @return {@link Icon}{@code .class} for the icon column, or {@link String}{@code .class} otherwise
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Icon.class : String.class;
        }

        /**
         * Returns the item icon, item name, or selected recipe label for the requested cell.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return the cell value object
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            OverrideRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> iconCache.getIcon(row.item.getImg(), 24, table::repaint);
                case 1 -> row.item.getName();
                case 2 -> new RecipeOption(row.selected).toString();
                default -> "";
            };
        }

        /**
         * Updates the selected recipe override for a row when edited via {@link RecipeCellEditor}.
         *
         * @param aValue      the chosen {@link RecipeOption}
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         */
        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 2 && aValue instanceof RecipeOption opt) {
                rows.get(rowIndex).selected = opt.recipe;
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }

    private final class RecipeCellEditor extends DefaultCellEditor {
        private final JComboBox<RecipeOption> combo;

        /**
         * Creates a combo-box cell editor configured with recipe icon rendering.
         */
        RecipeCellEditor() {
            super(new JComboBox<RecipeOption>());
            @SuppressWarnings("unchecked")
            JComboBox<RecipeOption> cb = (JComboBox<RecipeOption>) getComponent();
            this.combo = cb;
            this.combo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof RecipeOption opt && opt.recipe != null) {
                        setIcon(iconCache.getIcon(opt.recipe.getImg(), 18, list::repaint));
                    } else {
                        setIcon(null);
                    }
                    return this;
                }
            });
        }

        /**
         * Populates the dropdown with the automatic option plus all recipes producing the row's item.
         *
         * @param table      the JTable requesting editing
         * @param value      the current cell value
         * @param isSelected {@code true} if the cell is selected
         * @param row        zero-based view row index
         * @param column     zero-based view column index
         * @return the configured combo box editor component
         */
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            OverrideRow ovRow = tableModel.rows.get(row);
            DefaultComboBoxModel<RecipeOption> model = new DefaultComboBoxModel<>();
            RecipeOption autoOpt = new RecipeOption(null);
            model.addElement(autoOpt);
            RecipeOption currentOpt = autoOpt;
            for (Recipe r : ovRow.recipes) {
                RecipeOption opt = new RecipeOption(r);
                model.addElement(opt);
                if (ovRow.selected != null && ovRow.selected.getId().equals(r.getId())) {
                    currentOpt = opt;
                }
            }
            combo.setModel(model);
            combo.setSelectedItem(currentOpt);
            return super.getTableCellEditorComponent(table, currentOpt, isSelected, row, column);
        }
    }
}
