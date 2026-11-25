package hu.bme.prog3.factcalc.ui.catalog;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.RecipeMachineSpec;
import hu.bme.prog3.factcalc.service.CatalogService;
import hu.bme.prog3.factcalc.service.IconCacheService;
import hu.bme.prog3.factcalc.ui.components.ErrorDialogHelper;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeManagerPanel extends JPanel {
    private final CatalogService catalogService;
    private final IconCacheService iconCache;
    private final Runnable onCatalogChanged;

    private final RecipeTableModel tableModel = new RecipeTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<RecipeTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JTextField searchField = new JTextField(22);

    /**
     * Creates the recipe management panel with a filterable recipe table and CRUD buttons.
     *
     * @param catalogService   service managing catalog recipes and validation
     * @param iconCache        service used to render recipe icons in the table
     * @param onCatalogChanged callback invoked whenever a recipe is created, updated, or deleted
     */
    public RecipeManagerPanel(CatalogService catalogService, IconCacheService iconCache, Runnable onCatalogChanged) {
        this.catalogService = catalogService;
        this.iconCache = iconCache;
        this.onCatalogChanged = onCatalogChanged;
        buildUi();
        refresh();
    }

    /**
     * Initializes the search bar, CRUD toolbar, sortable recipe table, and interaction listeners.
     */
    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Filter:"));
        searchPanel.add(searchField);
        topBar.add(searchPanel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addBtn = new JButton("Add");
        JButton editBtn = new JButton("Edit");
        JButton deleteBtn = new JButton("Delete");
        addBtn.addActionListener(_ -> onAdd());
        editBtn.addActionListener(_ -> onEdit());
        deleteBtn.addActionListener(_ -> onDelete());
        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(deleteBtn);
        topBar.add(buttons, BorderLayout.EAST);

        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(0).setMaxWidth(48);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    onEdit();
                }
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        add(topBar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Filters the recipe table using a case-insensitive regex across name, type, items, and machines columns.
     */
    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 1, 2, 4, 5, 6, 7));
        }
    }

    /**
     * Reloads the table model from the active catalog's recipe list.
     */
    public void refresh() {
        tableModel.recipes = new ArrayList<>(catalogService.getCatalog().getRecipes());
        tableModel.fireTableDataChanged();
    }

    /**
     * Returns the recipe corresponding to the currently selected table row.
     *
     * @return the selected {@link Recipe}, or {@code null} if no row is selected
     */
    private Recipe getSelectedRecipe() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.recipes.get(modelRow);
    }

    /**
     * Opens the recipe creation dialog after verifying that at least one item and machine exist,
     * then refreshes the table if a recipe was created.
     */
    private void onAdd() {
        if (catalogService.getCatalog().getAllItems().isEmpty() || catalogService.getCatalog().getMachines().isEmpty()) {
            ErrorDialogHelper.showWarning(
                    this,
                    "Prerequisites Missing",
                    "Please create at least one item and one machine before creating a recipe."
            );
            return;
        }
        RecipeEditDialog dlg = new RecipeEditDialog(SwingUtilities.getWindowAncestor(this), null, catalogService, iconCache);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            refresh();
            onCatalogChanged.run();
        }
    }

    /**
     * Opens the recipe edit dialog for the currently selected recipe and refreshes if saved.
     */
    private void onEdit() {
        Recipe selected = getSelectedRecipe();
        if (selected == null) {
            ErrorDialogHelper.showInfo(this, "No Selection", "Please select a recipe to edit.");
            return;
        }
        RecipeEditDialog dlg = new RecipeEditDialog(SwingUtilities.getWindowAncestor(this), selected, catalogService, iconCache);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            refresh();
            onCatalogChanged.run();
        }
    }

    /**
     * Prompts for confirmation and deletes the currently selected recipe from the catalog.
     */
    private void onDelete() {
        Recipe selected = getSelectedRecipe();
        if (selected == null) {
            ErrorDialogHelper.showInfo(this, "No Selection", "Please select a recipe to delete.");
            return;
        }
        if (!ErrorDialogHelper.confirm(this, "Confirm Delete", "Delete recipe '" + selected.getName() + "'?")) {
            return;
        }
        try {
            catalogService.deleteRecipe(selected.getId());
            refresh();
            onCatalogChanged.run();
        } catch (CatalogService.ValidationException ex) {
            ErrorDialogHelper.showError(this, "Validation Error", ex.getMessage());
        }
    }

    /**
     * Formats a list of {@link ItemAmount} entries into a comma-separated summary string.
     *
     * @param list list of item quantities per recipe cycle
     * @return formatted summary string, or {@code "-"} if the list is empty
     */
    private String formatAmounts(List<ItemAmount> list) {
        if (list.isEmpty()) return "-";
        Catalog catalog = catalogService.getCatalog();
        return list.stream()
                .map(ia -> formatQty(ia.getPiece()) + "x " + catalog.findItem(ia.getId()).map(Item::getName).orElse("?"))
                .collect(Collectors.joining(", "));
    }

    /**
     * Formats compatible machine specifications into a readable comma-separated list.
     *
     * @param specs list of recipe machine specifications
     * @return formatted machine names/types with their multipliers
     */
    private String formatMachines(List<RecipeMachineSpec> specs) {
        Catalog catalog = catalogService.getCatalog();
        return specs.stream()
                .map(s -> {
                    if (s.isSpecificMachine()) {
                        String mName = catalog.findMachine(s.getId()).map(Machine::getName).orElse("?");
                        return mName + " (" + s.getMultiplier() + "x)";
                    }
                    return "[" + s.getType() + "] (" + s.getMultiplier() + "x)";
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Formats a numeric quantity without trailing decimals when integral, or with two decimal places otherwise.
     *
     * @param val the quantity value
     * @return formatted numeric string
     */
    private static String formatQty(double val) {
        if (Math.abs(val - Math.rint(val)) < 1e-6) {
            return String.valueOf((long) Math.rint(val));
        }
        return String.format("%.2f", val);
    }

    private final class RecipeTableModel extends AbstractTableModel {
        private final String[] cols = {"Icon", "Name", "Type", "Time (s)", "Inputs", "Outputs", "Byproducts", "Machines"};
        List<Recipe> recipes = new ArrayList<>();

        /**
         * Returns the number of recipe rows in the table.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return recipes.size();
        }

        /**
         * Returns the number of columns in the recipe table.
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
         * Returns the cell value class used for sorting and rendering the specified column.
         *
         * @param columnIndex zero-based column index
         * @return {@link Icon}{@code .class}, {@link Double}{@code .class}, or {@link String}{@code .class}
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Icon.class;
                case 3 -> Double.class;
                default -> String.class;
            };
        }

        /**
         * Returns the formatted cell value at the requested row and column indices.
         *
         * @param rowIndex    zero-based model row index
         * @param columnIndex zero-based column index
         * @return the recipe icon, name, type, cycle time, item summaries, or machines summary
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Recipe r = recipes.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> iconCache.getIcon(r.getImg(), 24, table::repaint);
                case 1 -> r.getName();
                case 2 -> r.isOriginal() ? "Original" : "Alternative";
                case 3 -> r.getTime();
                case 4 -> formatAmounts(r.getInputs());
                case 5 -> formatAmounts(r.getOutputs());
                case 6 -> formatAmounts(r.getByproducts());
                case 7 -> formatMachines(r.getMachines());
                default -> "";
            };
        }
    }
}
