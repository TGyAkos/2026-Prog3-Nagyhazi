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
import hu.bme.prog3.factcalc.ui.components.ItemComboBoxRenderer;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecipeEditDialog extends JDialog {

    private static final String MODE_TYPE = "Machine Type";
    private static final String MODE_SPECIFIC = "Specific Machine";

    private final Catalog catalog;
    private final CatalogService catalogService;
    private final IconCacheService iconCache;
    private final Recipe existing;

    private final JTextField nameField = new JTextField(26);
    private final JSpinner timeSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.001, 1_000_000.0, 0.5));
    private final JCheckBox originalCheckBox = new JCheckBox("Original Recipe", true);
    private final JTextField imgField = new JTextField(26);
    private final JLabel previewLabel = new JLabel();

    private final MachineSpecTableModel machineModel;
    private final ItemAmountTableModel inputsModel;
    private final ItemAmountTableModel outputsModel;
    private final ItemAmountTableModel byproductsModel;
    private final List<JTable> subTables = new ArrayList<>();
    private final JTabbedPane subTabs = new JTabbedPane();

    private boolean confirmed = false;

    /**
     * Creates a modal dialog for creating a new recipe or editing an existing recipe's metadata,
     * compatible machine specifications, inputs, outputs, and byproducts.
     *
     * @param owner          the parent window for modal centering
     * @param existing       the recipe to edit, or {@code null} when creating a new recipe
     * @param catalogService service used to validate and persist the recipe
     * @param iconCache      icon cache service used for previews and item/machine renderers
     */
    public RecipeEditDialog(
            Window owner,
            Recipe existing,
            CatalogService catalogService,
            IconCacheService iconCache
    ) {
        super(owner, existing == null ? "Add Recipe" : "Edit Recipe", ModalityType.APPLICATION_MODAL);
        this.existing = existing;
        this.catalogService = catalogService;
        this.catalog = catalogService.getCatalog();
        this.iconCache = iconCache;

        this.machineModel = new MachineSpecTableModel();
        this.inputsModel = new ItemAmountTableModel();
        this.outputsModel = new ItemAmountTableModel();
        this.byproductsModel = new ItemAmountTableModel();

        if (existing != null) {
            nameField.setText(existing.getName());
            timeSpinner.setValue(existing.getTime());
            originalCheckBox.setSelected(existing.isOriginal());
            imgField.setText(existing.getImg());
            for (RecipeMachineSpec spec : existing.getMachines()) {
                if (spec.isSpecificMachine()) {
                    Machine m = catalog.findMachine(spec.getId()).orElse(null);
                    machineModel.rows.add(new MachineSpecRow(MODE_SPECIFIC, m, spec.getMultiplier()));
                } else {
                    machineModel.rows.add(new MachineSpecRow(MODE_TYPE, spec.getType(), spec.getMultiplier()));
                }
            }
            for (ItemAmount ia : existing.getInputs()) {
                catalog.findItem(ia.getId()).ifPresent(item -> inputsModel.rows.add(new ItemAmountRow(item, ia.getPiece())));
            }
            for (ItemAmount ia : existing.getOutputs()) {
                catalog.findItem(ia.getId()).ifPresent(item -> outputsModel.rows.add(new ItemAmountRow(item, ia.getPiece())));
            }
            for (ItemAmount ia : existing.getByproducts()) {
                catalog.findItem(ia.getId()).ifPresent(item -> byproductsModel.rows.add(new ItemAmountRow(item, ia.getPiece())));
            }
        }

        buildUi();
        updatePreview();
        updateTabTitles();
        setMinimumSize(new Dimension(680, 520));
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Builds the recipe metadata header, tabbed machine/item-amount tables, and dialog Save/Cancel controls.
     */
    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(nameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel("Production Time (sec):"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(timeSpinner, gbc);
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        form.add(originalCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        form.add(new JLabel("Icon URL / Path:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(imgField, gbc);
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(_ -> chooseFile());
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        form.add(browseBtn, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        form.add(new JLabel("Preview:"), gbc);
        previewLabel.setPreferredSize(new Dimension(48, 48));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createEtchedBorder());
        gbc.gridx = 1;
        form.add(previewLabel, gbc);
        JLabel hint = new JLabel("(Leave blank to inherit primary output's icon)");
        hint.setEnabled(false);
        gbc.gridx = 2;
        form.add(hint, gbc);

        imgField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePreview();
            }
        });

        subTabs.addTab("Machines", createMachinesPanel());
        subTabs.addTab("Inputs", createItemAmountPanel(inputsModel));
        subTabs.addTab("Outputs", createItemAmountPanel(outputsModel));
        subTabs.addTab("Byproducts", createItemAmountPanel(byproductsModel));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(_ -> onSave());
        cancelBtn.addActionListener(_ -> dispose());
        buttons.add(okBtn);
        buttons.add(cancelBtn);
        getRootPane().setDefaultButton(okBtn);

        setLayout(new BorderLayout(8, 8));
        add(form, BorderLayout.NORTH);
        add(subTabs, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    /**
     * Creates the editable table tab for compatible machine types and specific machine specifications.
     *
     * @return the constructed machines sub-panel
     */
    private JPanel createMachinesPanel() {
        JTable table = new JTable(machineModel);
        table.setRowHeight(26);
        subTables.add(table);

        JComboBox<String> modeBox = new JComboBox<>(new String[]{MODE_TYPE, MODE_SPECIFIC});
        table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(modeBox));
        table.getColumnModel().getColumn(1).setCellEditor(new MachineTargetCellEditor());

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("+ Add Machine / Type");
        JButton removeBtn = new JButton("- Remove Selected");
        addBtn.addActionListener(_ -> {
            String defaultType = catalog.getMachineTypes().stream().findFirst().orElse("");
            machineModel.rows.add(new MachineSpecRow(MODE_TYPE, defaultType, 1.0));
            machineModel.fireTableDataChanged();
            updateTabTitles();
        });
        removeBtn.addActionListener(_ -> {
            stopAllEditors();
            int row = table.getSelectedRow();
            if (row >= 0) {
                machineModel.rows.remove(row);
                machineModel.fireTableDataChanged();
                updateTabTitles();
            }
        });
        toolbar.add(addBtn);
        toolbar.add(removeBtn);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Creates an editable item-and-quantity table tab backed by the given table model
     * (reused for inputs, outputs, and byproducts).
     *
     * @param model the table model holding the item-amount rows
     * @return the constructed item-amount sub-panel
     */
    private JPanel createItemAmountPanel(ItemAmountTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(26);
        subTables.add(table);

        JComboBox<Item> itemBox = new JComboBox<>(catalog.getAllItems().toArray(new Item[0]));
        itemBox.setRenderer(new ItemComboBoxRenderer(iconCache));
        table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(itemBox));

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("+ Add Item");
        JButton removeBtn = new JButton("- Remove Selected");
        addBtn.addActionListener(_ -> {
            List<Item> all = catalog.getAllItems();
            if (all.isEmpty()) {
                ErrorDialogHelper.showWarning(this, "No Items", "Please create at least one item or machine first.");
                return;
            }
            model.rows.add(new ItemAmountRow(all.get(0), 1.0));
            model.fireTableDataChanged();
            updateTabTitles();
        });
        removeBtn.addActionListener(_ -> {
            stopAllEditors();
            int row = table.getSelectedRow();
            if (row >= 0) {
                model.rows.remove(row);
                model.fireTableDataChanged();
                updateTabTitles();
            }
        });
        toolbar.add(addBtn);
        toolbar.add(removeBtn);
        panel.add(toolbar, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Updates the sub-tab titles to reflect the current row counts in each table.
     */
    private void updateTabTitles() {
        subTabs.setTitleAt(0, "Usable Machines (" + machineModel.rows.size() + ")");
        subTabs.setTitleAt(1, "Inputs (" + inputsModel.rows.size() + ")");
        subTabs.setTitleAt(2, "Outputs (" + outputsModel.rows.size() + ")");
        subTabs.setTitleAt(3, "Byproducts (" + byproductsModel.rows.size() + ")");
    }

    /**
     * Commits any active inline cell editor across all recipe sub-tables before reading or mutating rows.
     */
    private void stopAllEditors() {
        for (JTable t : subTables) {
            if (t.isEditing() && t.getCellEditor() != null) {
                t.getCellEditor().stopCellEditing();
            }
        }
    }

    /**
     * Opens a file chooser dialog and populates the recipe icon field with the selected file path.
     */
    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imgField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Updates the recipe icon preview label asynchronously from the current icon path/URL text.
     */
    private void updatePreview() {
        String path = imgField.getText().trim();
        previewLabel.setIcon(iconCache.getIcon(path, 40, () ->
                previewLabel.setIcon(iconCache.getIcon(imgField.getText().trim(), 40, null))));
    }

    /**
     * Stops active editors, collects form and table entries, persists the recipe via {@link CatalogService},
     * and closes the dialog on success.
     */
    private void onSave() {
        stopAllEditors();

        List<RecipeMachineSpec> specs = new ArrayList<>();
        for (MachineSpecRow r : machineModel.rows) {
            if (MODE_SPECIFIC.equals(r.mode)) {
                if (r.target instanceof Machine m) {
                    specs.add(RecipeMachineSpec.byId(m.getId(), r.multiplier));
                } else {
                    ErrorDialogHelper.showError(this, "Validation Error", "Please select a specific machine in the Machines table.");
                    return;
                }
            } else {
                String type = r.target == null ? "" : r.target.toString().trim();
                specs.add(RecipeMachineSpec.byType(type, r.multiplier));
            }
        }

        List<ItemAmount> inputs = toItemAmounts(inputsModel);
        List<ItemAmount> outputs = toItemAmounts(outputsModel);
        List<ItemAmount> byproducts = toItemAmounts(byproductsModel);

        String name = nameField.getText().trim();
        double time = ((Number) timeSpinner.getValue()).doubleValue();
        String img = imgField.getText().trim();
        boolean isOriginal = originalCheckBox.isSelected();

        try {
            if (existing == null) {
                catalogService.createRecipe(name, time, img, isOriginal, specs, inputs, outputs, byproducts);
            } else {
                catalogService.updateRecipe(existing.getId(), name, time, img, isOriginal, specs, inputs, outputs, byproducts);
            }
            confirmed = true;
            dispose();
        } catch (CatalogService.ValidationException ex) {
            ErrorDialogHelper.showError(this, "Recipe Validation Error", ex.getMessage());
        }
    }

    /**
     * Converts the rows of an {@link ItemAmountTableModel} into {@link ItemAmount} model instances.
     *
     * @param model the table model containing selected items and cycle quantities
     * @return list of {@link ItemAmount} entries with non-null items
     */
    private List<ItemAmount> toItemAmounts(ItemAmountTableModel model) {
        List<ItemAmount> list = new ArrayList<>();
        for (ItemAmountRow row : model.rows) {
            if (row.item != null) {
                list.add(new ItemAmount(row.item.getId(), row.piece));
            }
        }
        return list;
    }

    /**
     * Returns whether the user saved and closed the recipe dialog successfully.
     *
     * @return {@code true} if saved, {@code false} if cancelled
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    private static final class MachineSpecRow {
        String mode;
        Object target;
        double multiplier;

        /**
         * Creates an editable table row representing a machine type or specific machine specification.
         *
         * @param mode       selection mode ({@code MODE_TYPE} or {@code MODE_SPECIFIC})
         * @param target     the machine type string or {@link Machine} instance
         * @param multiplier recipe-level speed multiplier for this machine specification
         */
        MachineSpecRow(String mode, Object target, double multiplier) {
            this.mode = mode;
            this.target = target;
            this.multiplier = multiplier;
        }
    }

    private final class MachineSpecTableModel extends AbstractTableModel {
        private final String[] cols = {"Selection Mode", "Machine / Type", "Recipe Multiplier"};
        final List<MachineSpecRow> rows = new ArrayList<>();

        /**
         * Returns the number of machine specification rows.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of columns in the machine specification table.
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
         * Marks all cells in the machine specification table as editable.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return always {@code true}
         */
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        /**
         * Returns the column value class used by renderers and editors.
         *
         * @param columnIndex zero-based column index
         * @return {@link Double}{@code .class} for the multiplier column, or {@link Object}{@code .class} otherwise
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Double.class : Object.class;
        }

        /**
         * Returns the display value at the specified row and column.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return the mode string, machine/type label, or multiplier value
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MachineSpecRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.mode;
                case 1 -> row.target instanceof Machine m ? m.getName() + " [" + m.getType() + "]" : row.target;
                case 2 -> row.multiplier;
                default -> null;
            };
        }

        /**
         * Updates the machine specification row after an inline cell edit and resets target defaults
         * when switching between type and specific-machine modes.
         *
         * @param aValue      the newly edited cell value
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         */
        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            MachineSpecRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0 -> {
                    String newMode = String.valueOf(aValue);
                    if (!newMode.equals(row.mode)) {
                        row.mode = newMode;
                        if (MODE_SPECIFIC.equals(newMode)) {
                            row.target = catalog.getMachines().stream().findFirst().orElse(null);
                        } else {
                            row.target = catalog.getMachineTypes().stream().findFirst().orElse("");
                        }
                        fireTableRowsUpdated(rowIndex, rowIndex);
                    }
                }
                case 1 -> row.target = aValue;
                case 2 -> {
                    if (aValue instanceof Number n) {
                        row.multiplier = n.doubleValue();
                    }
                }
            }
        }
    }

    private final class MachineTargetCellEditor extends DefaultCellEditor {
        private final JComboBox<Object> combo;

        /**
         * Creates a table cell editor that adapts between specific {@link Machine} selection
         * and editable machine type strings depending on the row's mode.
         */
        MachineTargetCellEditor() {
            super(new JComboBox<>());
            @SuppressWarnings("unchecked")
            JComboBox<Object> cb = (JComboBox<Object>) getComponent();
            this.combo = cb;
            this.combo.setRenderer(new ItemComboBoxRenderer(iconCache));
        }

        /**
         * Populates the dropdown with either all catalog machines or known machine types for the edited row.
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
            MachineSpecRow specRow = machineModel.rows.get(row);
            DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
            if (MODE_SPECIFIC.equals(specRow.mode)) {
                combo.setEditable(false);
                for (Machine m : catalog.getMachines()) {
                    model.addElement(m);
                }
            } else {
                combo.setEditable(true);
                for (String type : catalog.getMachineTypes()) {
                    model.addElement(type);
                }
            }
            combo.setModel(model);
            combo.setSelectedItem(specRow.target);
            return super.getTableCellEditorComponent(table, specRow.target, isSelected, row, column);
        }
    }

    private static final class ItemAmountRow {
        Item item;
        double piece;

        /**
         * Creates an editable table row pairing an {@link Item} with its per-cycle quantity.
         *
         * @param item  the referenced catalog item
         * @param piece the quantity consumed or produced per recipe cycle
         */
        ItemAmountRow(Item item, double piece) {
            this.item = item;
            this.piece = piece;
        }
    }

    private static final class ItemAmountTableModel extends AbstractTableModel {
        private final String[] cols = {"Item", "Quantity (per cycle)"};
        final List<ItemAmountRow> rows = new ArrayList<>();

        /**
         * Returns the number of item-amount rows in the table.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of columns in the item-amount table.
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
         * Marks all cells in the item-amount table as editable.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return always {@code true}
         */
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        /**
         * Returns the column value class used by renderers and editors.
         *
         * @param columnIndex zero-based column index
         * @return {@link Double}{@code .class} for the quantity column, or {@link Item}{@code .class} otherwise
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 1 ? Double.class : Item.class;
        }

        /**
         * Returns the {@link Item} or cycle quantity at the requested cell.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return the cell value
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ItemAmountRow row = rows.get(rowIndex);
            return columnIndex == 0 ? row.item : row.piece;
        }

        /**
         * Updates the row's {@link Item} or cycle quantity from the edited cell value.
         *
         * @param aValue      the newly edited cell value
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         */
        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            ItemAmountRow row = rows.get(rowIndex);
            if (columnIndex == 0 && aValue instanceof Item item) {
                row.item = item;
            } else if (columnIndex == 1 && aValue instanceof Number n) {
                row.piece = n.doubleValue();
            }
        }
    }
}
