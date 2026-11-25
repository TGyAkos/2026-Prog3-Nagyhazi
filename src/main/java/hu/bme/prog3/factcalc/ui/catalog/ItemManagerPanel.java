package hu.bme.prog3.factcalc.ui.catalog;

import hu.bme.prog3.factcalc.model.Item;
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

public class ItemManagerPanel extends JPanel {
    private final CatalogService catalogService;
    private final IconCacheService iconCache;
    private final Runnable onCatalogChanged;

    private final ItemTableModel tableModel = new ItemTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<ItemTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JTextField searchField = new JTextField(22);

    /**
     * Creates the item management panel with a filterable table and CRUD action buttons.
     *
     * @param catalogService   service managing catalog items and validation
     * @param iconCache        service used to load item icons for table rows
     * @param onCatalogChanged callback invoked whenever an item is added, updated, or deleted
     */
    public ItemManagerPanel(CatalogService catalogService, IconCacheService iconCache, Runnable onCatalogChanged) {
        this.catalogService = catalogService;
        this.iconCache = iconCache;
        this.onCatalogChanged = onCatalogChanged;
        buildUi();
        refresh();
    }

    /**
     * Initializes the search bar, CRUD toolbar, sortable item table, and double-click/filter listeners.
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
     * Updates the table row filter using a case-insensitive regex against item names and IDs.
     */
    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 1, 2));
        }
    }

    /**
     * Reloads the table model from the active catalog's plain items and notifies listeners.
     */
    public void refresh() {
        tableModel.items = new ArrayList<>(catalogService.getCatalog().getPlainItems());
        tableModel.fireTableDataChanged();
    }

    /**
     * Returns the item corresponding to the currently selected table row, accounting for sorting/filtering.
     *
     * @return the selected {@link Item}, or {@code null} if no row is selected
     */
    private Item getSelectedItem() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.items.get(modelRow);
    }

    /**
     * Opens the item creation dialog and adds the new item to the catalog if confirmed.
     */
    private void onAdd() {
        ItemEditDialog dlg = new ItemEditDialog(SwingUtilities.getWindowAncestor(this), null, iconCache);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            try {
                catalogService.createItem(dlg.getItemName(), dlg.getItemImg());
                refresh();
                onCatalogChanged.run();
            } catch (CatalogService.ValidationException ex) {
                ErrorDialogHelper.showError(this, "Validation Error", ex.getMessage());
            }
        }
    }

    /**
     * Opens the item edit dialog for the currently selected item and updates it if confirmed.
     */
    private void onEdit() {
        Item selected = getSelectedItem();
        if (selected == null) {
            ErrorDialogHelper.showInfo(this, "No Selection", "Please select an item to edit.");
            return;
        }
        ItemEditDialog dlg = new ItemEditDialog(SwingUtilities.getWindowAncestor(this), selected, iconCache);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            try {
                catalogService.updateItem(selected.getId(), dlg.getItemName(), dlg.getItemImg());
                refresh();
                onCatalogChanged.run();
            } catch (CatalogService.ValidationException ex) {
                ErrorDialogHelper.showError(this, "Validation Error", ex.getMessage());
            }
        }
    }

    /**
     * Prompts for confirmation and deletes the currently selected item from the catalog.
     */
    private void onDelete() {
        Item selected = getSelectedItem();
        if (selected == null) {
            ErrorDialogHelper.showInfo(this, "No Selection", "Please select an item to delete.");
            return;
        }
        if (!ErrorDialogHelper.confirm(this, "Confirm Delete", "Delete item '" + selected.getName() + "'?")) {
            return;
        }
        try {
            catalogService.deleteItem(selected.getId());
            refresh();
            onCatalogChanged.run();
        } catch (CatalogService.ValidationException ex) {
            ErrorDialogHelper.showError(this, "Cannot Delete Item", ex.getMessage());
        }
    }

    private final class ItemTableModel extends AbstractTableModel {
        private final String[] cols = {"Icon", "Name", "ID"};
        List<Item> items = new ArrayList<>();

        /**
         * Returns the number of item rows in the table.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return items.size();
        }

        /**
         * Returns the number of columns in the item table.
         *
         * @return the column count
         */
        @Override
        public int getColumnCount() {
            return cols.length;
        }

        /**
         * Returns the display header for the specified column.
         *
         * @param column zero-based column index
         * @return the column title
         */
        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        /**
         * Returns the cell value class used by the table renderer for the given column.
         *
         * @param columnIndex zero-based column index
         * @return {@link Icon}{@code .class} for the icon column, or {@link String}{@code .class} otherwise
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Icon.class : String.class;
        }

        /**
         * Returns the display value (icon, name, or UUID string) at the requested table cell.
         *
         * @param rowIndex    zero-based model row index
         * @param columnIndex zero-based column index
         * @return the cell value object
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Item item = items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> iconCache.getIcon(item.getImg(), 24, table::repaint);
                case 1 -> item.getName();
                case 2 -> item.getId().toString();
                default -> "";
            };
        }
    }
}
