package hu.bme.prog3.factcalc.ui.catalog;

import hu.bme.prog3.factcalc.model.Machine;
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

public class MachineManagerPanel extends JPanel {
    private final CatalogService catalogService;
    private final IconCacheService iconCache;
    private final Runnable onCatalogChanged;

    private final MachineTableModel tableModel = new MachineTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<MachineTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JTextField searchField = new JTextField(22);

    /**
     * Creates the machine management panel with a filterable table and CRUD controls.
     *
     * @param catalogService   service managing catalog machines and validation
     * @param iconCache        service used to render machine icons in the table
     * @param onCatalogChanged callback invoked whenever a machine is added, updated, or deleted
     */
    public MachineManagerPanel(CatalogService catalogService, IconCacheService iconCache, Runnable onCatalogChanged) {
        this.catalogService = catalogService;
        this.iconCache = iconCache;
        this.onCatalogChanged = onCatalogChanged;
        buildUi();
        refresh();
    }

    /**
     * Initializes the search bar, CRUD toolbar, sortable machine table, and interaction listeners.
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
     * Filters the machine table using a case-insensitive regex against name, type, and ID columns.
     */
    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 1, 2, 4));
        }
    }

    /**
     * Reloads the table model from the active catalog's machine list.
     */
    public void refresh() {
        tableModel.machines = new ArrayList<>(catalogService.getCatalog().getMachines());
        tableModel.fireTableDataChanged();
    }

    /**
     * Returns the machine corresponding to the currently selected table row.
     *
     * @return the selected {@link Machine}, or {@code null} if no row is selected
     */
    private Machine getSelectedMachine() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.machines.get(modelRow);
    }

    /**
     * Opens the machine creation dialog and adds the new machine to the catalog if confirmed.
     */
    private void onAdd() {
        MachineEditDialog dlg = new MachineEditDialog(
                SwingUtilities.getWindowAncestor(this),
                null,
                catalogService.getCatalog().getMachineTypes(),
                iconCache
        );
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            try {
                catalogService.createMachine(dlg.getMachineName(), dlg.getMachineType(), dlg.getMachineImg(), dlg.getMultiplier());
                refresh();
                onCatalogChanged.run();
            } catch (CatalogService.ValidationException ex) {
                ErrorDialogHelper.showError(this, "Validation Error", ex.getMessage());
            }
        }
    }

    /**
     * Opens the machine edit dialog for the currently selected machine and saves updates if confirmed.
     */
    private void onEdit() {
        Machine selected = getSelectedMachine();
        if (selected == null) {
            ErrorDialogHelper.showInfo(this, "No Selection", "Please select a machine to edit.");
            return;
        }
        MachineEditDialog dlg = new MachineEditDialog(
                SwingUtilities.getWindowAncestor(this),
                selected,
                catalogService.getCatalog().getMachineTypes(),
                iconCache
        );
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            try {
                catalogService.updateMachine(selected.getId(), dlg.getMachineName(), dlg.getMachineType(), dlg.getMachineImg(), dlg.getMultiplier());
                refresh();
                onCatalogChanged.run();
            } catch (CatalogService.ValidationException ex) {
                ErrorDialogHelper.showError(this, "Validation Error", ex.getMessage());
            }
        }
    }

    /**
     * Prompts for confirmation and removes the currently selected machine from the catalog.
     */
    private void onDelete() {
        Machine selected = getSelectedMachine();
        if (selected == null) {
            ErrorDialogHelper.showInfo(this, "No Selection", "Please select a machine to delete.");
            return;
        }
        if (!ErrorDialogHelper.confirm(this, "Confirm Delete", "Delete machine '" + selected.getName() + "'?")) {
            return;
        }
        try {
            catalogService.deleteMachine(selected.getId());
            refresh();
            onCatalogChanged.run();
        } catch (CatalogService.ValidationException ex) {
            ErrorDialogHelper.showError(this, "Cannot Delete Machine", ex.getMessage());
        }
    }

    private final class MachineTableModel extends AbstractTableModel {
        private final String[] cols = {"Icon", "Name", "Type", "Multiplier", "ID"};
        List<Machine> machines = new ArrayList<>();

        /**
         * Returns the number of machine rows in the table.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return machines.size();
        }

        /**
         * Returns the number of columns in the machine table.
         *
         * @return the column count
         */
        @Override
        public int getColumnCount() {
            return cols.length;
        }

        /**
         * Returns the header title for the given column index.
         *
         * @param column zero-based column index
         * @return the column name
         */
        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        /**
         * Returns the value class used for sorting and rendering the specified column.
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
         * Returns the cell value at the given row and column indices.
         *
         * @param rowIndex    zero-based model row index
         * @param columnIndex zero-based column index
         * @return the machine icon, name, type, multiplier, or UUID string
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Machine m = machines.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> iconCache.getIcon(m.getImg(), 24, table::repaint);
                case 1 -> m.getName();
                case 2 -> m.getType();
                case 3 -> m.getMultiplier();
                case 4 -> m.getId().toString();
                default -> "";
            };
        }
    }
}
