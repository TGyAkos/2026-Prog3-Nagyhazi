package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.TimeUnit;
import hu.bme.prog3.factcalc.service.IconCacheService;
import hu.bme.prog3.factcalc.solver.RecipeResolver;
import hu.bme.prog3.factcalc.ui.components.ErrorDialogHelper;
import hu.bme.prog3.factcalc.ui.components.ItemComboBoxRenderer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class PlanConfigPanel extends JPanel {

    private Catalog catalog;
    private final IconCacheService iconCache;
    private final Consumer<TimeUnit> onDisplayUnitChanged;
    private final Runnable onCalculateRequested;
    private final Runnable onSavePlanRequested;
    private final Runnable onLoadPlanRequested;

    private final JRadioButton targetModeRadio = new JRadioButton("Production Line from Target Output", true);
    private final JRadioButton maxModeRadio = new JRadioButton("Max Output from Inputs");
    private final JComboBox<TimeUnit> displayUnitCombo = new JComboBox<>(TimeUnit.values());

    private final CardLayout targetCardLayout = new CardLayout();
    private final JPanel targetCards = new JPanel(targetCardLayout);

    private final RateTableModel targetsModel = new RateTableModel(false);
    private final JTable targetsTable = new JTable(targetsModel);
    private final JComboBox<Item> singleTargetCombo = new JComboBox<>();
    private final JCheckBox autoModeCheckBox = new JCheckBox("Auto (optimize across alternative recipes)");

    private final RateTableModel inputsModel = new RateTableModel(true);
    private final JTable inputsTable = new JTable(inputsModel);

    private final JLabel overridesSummaryLabel = new JLabel("Overrides: 0");
    private final JButton calculateBtn = new JButton("Calculate");
    private final JButton cancelAutoBtn = new JButton("Cancel");

    private Map<UUID, UUID> recipeOverrides = new LinkedHashMap<>();
    private Map<UUID, UUID> machineSelections = new LinkedHashMap<>();
    private Map<UUID, Boolean> recycledByproducts = new LinkedHashMap<>();

    /**
     * Creates the left-hand planner configuration panel for choosing calculation mode, targets,
     * supplied inputs, rate display unit, recipe overrides, and plan persistence actions.
     *
     * @param catalog              initial active factory catalog
     * @param iconCache            icon cache service for rendering item dropdowns
     * @param onDisplayUnitChanged callback notified when the display time unit changes
     * @param onCalculateRequested callback invoked when the user clicks Calculate or applies an inline override
     * @param onSavePlanRequested  callback invoked when the user clicks Save Plan
     * @param onLoadPlanRequested  callback invoked when the user clicks Load Plan
     */
    public PlanConfigPanel(Catalog catalog, IconCacheService iconCache, Consumer<TimeUnit> onDisplayUnitChanged, Runnable onCalculateRequested, Runnable onSavePlanRequested, Runnable onLoadPlanRequested) {
        this.catalog = catalog;
        this.iconCache = iconCache;
        this.onDisplayUnitChanged = onDisplayUnitChanged;
        this.onCalculateRequested = onCalculateRequested;
        this.onSavePlanRequested = onSavePlanRequested;
        this.onLoadPlanRequested = onLoadPlanRequested;

        displayUnitCombo.setSelectedItem(TimeUnit.PER_MINUTE);
        buildUi();
        refreshCatalog(catalog);
    }

    /**
     * Assembles the mode selector, target/input sections, recipe override controls, and action buttons.
     */
    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(410, 620));

        JPanel topSettings = new JPanel(new GridBagLayout());
        topSettings.setBorder(BorderFactory.createTitledBorder("Calculation Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(targetModeRadio);
        modeGroup.add(maxModeRadio);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        topSettings.add(targetModeRadio, gbc);
        gbc.gridy = 1;
        topSettings.add(maxModeRadio, gbc);

        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        topSettings.add(new JLabel("Display Unit:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        topSettings.add(displayUnitCombo, gbc);

        targetModeRadio.addActionListener(_ -> onModeChanged());
        maxModeRadio.addActionListener(_ -> onModeChanged());
        displayUnitCombo.addActionListener(_ -> onDisplayUnitChanged.accept(getDisplayUnit()));

        // Targets section
        JPanel targetsSection = new JPanel(new BorderLayout(4, 4));
        targetsSection.setBorder(BorderFactory.createTitledBorder("Target Outputs"));

        // Card 1: Multi-target table
        JPanel multiTargetPanel = new JPanel(new BorderLayout(4, 4));
        targetsTable.setRowHeight(24);
        targetsTable.setFillsViewportHeight(true);
        targetsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        targetsTable.setToolTipText("Click an item cell to change it. Only items with at least one recipe that has inputs can be targets.");
        targetsTable.getColumnModel().getColumn(0).setCellRenderer(new ItemTableCellRenderer());
        targetsTable.getColumnModel().getColumn(1).setCellEditor(new RateCellEditor(false));
        multiTargetPanel.add(new JScrollPane(targetsTable), BorderLayout.CENTER);
        JPanel targetBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addTargetBtn = new JButton("+ Add Target");
        JButton removeTargetBtn = new JButton("- Remove");
        addTargetBtn.addActionListener(_ -> onAddTarget());
        removeTargetBtn.addActionListener(_ -> removeSelectedRow(targetsTable, targetsModel));
        targetBtns.add(addTargetBtn);
        targetBtns.add(removeTargetBtn);
        multiTargetPanel.add(targetBtns, BorderLayout.SOUTH);

        // Card 2: Single target + auto checkbox
        JPanel singleTargetPanel = new JPanel(new GridBagLayout());
        GridBagConstraints sg = new GridBagConstraints();
        sg.insets = new Insets(4, 4, 4, 4);
        sg.anchor = GridBagConstraints.WEST;
        sg.fill = GridBagConstraints.HORIZONTAL;
        sg.gridx = 0;
        sg.gridy = 0;
        sg.weightx = 0.0;
        singleTargetPanel.add(new JLabel("Target Item:"), sg);
        sg.gridx = 1;
        sg.weightx = 1.0;
        singleTargetCombo.setRenderer(new ItemComboBoxRenderer(iconCache));
        singleTargetCombo.setToolTipText("Only items with at least one recipe that has inputs can be selected as a target.");
        singleTargetPanel.add(singleTargetCombo, sg);
        sg.gridx = 0;
        sg.gridy = 1;
        sg.gridwidth = 2;
        singleTargetPanel.add(autoModeCheckBox, sg);

        targetCards.add(multiTargetPanel, "MULTI");
        targetCards.add(singleTargetPanel, "SINGLE");
        targetsSection.add(targetCards, BorderLayout.CENTER);
        targetsSection.setPreferredSize(new Dimension(380, 165));

        // Inputs section
        JPanel inputsSection = new JPanel(new BorderLayout(4, 4));
        inputsSection.setBorder(BorderFactory.createTitledBorder("Supplied Inputs (leave Rate blank for unlimited in Target mode)"));
        inputsTable.setRowHeight(24);
        inputsTable.setFillsViewportHeight(true);
        inputsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        inputsTable.setToolTipText("Click an item cell to choose any catalog item or machine as a supplied input.");
        inputsTable.getColumnModel().getColumn(0).setCellRenderer(new ItemTableCellRenderer());
        inputsTable.getColumnModel().getColumn(1).setCellEditor(new RateCellEditor(true));
        inputsSection.add(new JScrollPane(inputsTable), BorderLayout.CENTER);

        JPanel inputBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addInputBtn = new JButton("+ Add Input");
        JButton removeInputBtn = new JButton("- Remove");
        addInputBtn.addActionListener(_ -> onAddInput());
        removeInputBtn.addActionListener(_ -> removeSelectedRow(inputsTable, inputsModel));
        inputBtns.add(addInputBtn);
        inputBtns.add(removeInputBtn);
        inputsSection.add(inputBtns, BorderLayout.SOUTH);

        // Center stack holding targets + inputs
        JPanel centerStack = new JPanel(new GridBagLayout());
        GridBagConstraints cg = new GridBagConstraints();
        cg.gridx = 0;
        cg.gridy = 0;
        cg.weightx = 1.0;
        cg.weighty = 0.45;
        cg.fill = GridBagConstraints.BOTH;
        centerStack.add(targetsSection, cg);
        cg.gridy = 1;
        cg.weighty = 0.55;
        centerStack.add(inputsSection, cg);

        // Bottom controls
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 6));

        JPanel recipeOptsPanel = new JPanel(new BorderLayout(4, 2));
        recipeOptsPanel.setBorder(BorderFactory.createTitledBorder("Recipe & Plan Controls"));
        JPanel recipeOptsButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton selectAltBtn = new JButton("Select Alternative Recipes...");
        JButton resetBtn = new JButton("Reset Selections");
        selectAltBtn.addActionListener(_ -> openOverrideDialog(null));
        resetBtn.addActionListener(_ -> resetSelections());
        recipeOptsButtons.add(selectAltBtn);
        recipeOptsButtons.add(resetBtn);
        overridesSummaryLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        recipeOptsPanel.add(recipeOptsButtons, BorderLayout.NORTH);
        recipeOptsPanel.add(overridesSummaryLabel, BorderLayout.SOUTH);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        calculateBtn.setFont(calculateBtn.getFont().deriveFont(Font.BOLD));
        cancelAutoBtn.setVisible(false);
        JButton savePlanBtn = new JButton("Save Plan...");
        JButton loadPlanBtn = new JButton("Load Plan...");
        calculateBtn.addActionListener(_ -> {
            stopEditing();
            onCalculateRequested.run();
        });
        savePlanBtn.addActionListener(_ -> {
            stopEditing();
            onSavePlanRequested.run();
        });
        loadPlanBtn.addActionListener(_ -> {
            stopEditing();
            onLoadPlanRequested.run();
        });

        actionRow.add(loadPlanBtn);
        actionRow.add(savePlanBtn);
        actionRow.add(cancelAutoBtn);
        actionRow.add(calculateBtn);

        bottomPanel.add(recipeOptsPanel, BorderLayout.NORTH);
        bottomPanel.add(actionRow, BorderLayout.SOUTH);

        add(topSettings, BorderLayout.NORTH);
        add(centerStack, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        onModeChanged();
    }

    /**
     * Switches the target-output card between multi-target table mode and single-target max-output mode,
     * disabling and unchecking auto optimization when not in {@code MAX_OUTPUT} mode.
     */
    private void onModeChanged() {
        boolean isMax = maxModeRadio.isSelected();
        targetCardLayout.show(targetCards, isMax ? "SINGLE" : "MULTI");
        autoModeCheckBox.setEnabled(isMax);
        if (!isMax) {
            autoModeCheckBox.setSelected(false);
        }
    }

    /**
     * Refreshes item dropdowns and table cell editors from the updated catalog, preserves valid
     * selections with fresh catalog instances, and prunes any deleted or incompatible references.
     *
     * @param newCatalog the updated active {@link Catalog}
     */
    public void refreshCatalog(Catalog newCatalog) {
        this.catalog = newCatalog;
        stopEditing();

        Item previousSingleTarget = (Item) singleTargetCombo.getSelectedItem();
        UUID previousSingleId = previousSingleTarget != null ? previousSingleTarget.getId() : null;

        List<Item> eligibleTargets = RecipeResolver.getEligibleTargetItems(catalog);
        List<Item> allItems = catalog != null ? catalog.getAllItems() : List.of();

        // Update single target combo while preserving prior valid selection
        DefaultComboBoxModel<Item> singleModel = new DefaultComboBoxModel<>();
        Item restoredSingle = null;
        for (Item item : eligibleTargets) {
            singleModel.addElement(item);
            if (previousSingleId != null && previousSingleId.equals(item.getId())) {
                restoredSingle = item;
            }
        }
        singleTargetCombo.setModel(singleModel);
        if (restoredSingle != null) {
            singleTargetCombo.setSelectedItem(restoredSingle);
        }

        // Update table cell editors
        JComboBox<Item> targetItemBox = new JComboBox<>(eligibleTargets.toArray(new Item[0]));
        targetItemBox.setRenderer(new ItemComboBoxRenderer(iconCache));
        DefaultCellEditor targetEditor = new DefaultCellEditor(targetItemBox);
        targetEditor.setClickCountToStart(1);
        targetsTable.getColumnModel().getColumn(0).setCellEditor(targetEditor);

        JComboBox<Item> inputItemBox = new JComboBox<>(allItems.toArray(new Item[0]));
        inputItemBox.setRenderer(new ItemComboBoxRenderer(iconCache));
        DefaultCellEditor inputEditor = new DefaultCellEditor(inputItemBox);
        inputEditor.setClickCountToStart(1);
        inputsTable.getColumnModel().getColumn(0).setCellEditor(inputEditor);

        JComboBox<TimeUnit> unitBox1 = new JComboBox<>(TimeUnit.values());
        JComboBox<TimeUnit> unitBox2 = new JComboBox<>(TimeUnit.values());
        targetsTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(unitBox1));
        inputsTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(unitBox2));

        // Rebind row item references to fresh catalog instances and prune deleted/ineligible items
        for (RateRow r : targetsModel.rows) {
            if (r.item != null && catalog != null) {
                r.item = catalog.findItem(r.item.getId()).orElse(null);
            }
        }
        for (RateRow r : inputsModel.rows) {
            if (r.item != null && catalog != null) {
                r.item = catalog.findItem(r.item.getId()).orElse(null);
            }
        }
        targetsModel.rows.removeIf(r -> r.item == null || catalog == null || !RecipeResolver.isEligibleTarget(catalog, r.item.getId()));
        inputsModel.rows.removeIf(r -> r.item == null || catalog == null);
        targetsModel.fireTableDataChanged();
        inputsModel.fireTableDataChanged();

        if (catalog != null) {
            recipeOverrides.entrySet().removeIf(e -> catalog.findItem(e.getKey()).isEmpty() || catalog.findRecipe(e.getValue()).filter(r -> r.producesOutput(e.getKey())).isEmpty());
            machineSelections.entrySet().removeIf(e -> {
                var recipeOpt = catalog.findRecipe(e.getKey());
                var machineOpt = catalog.findMachine(e.getValue());
                return recipeOpt.isEmpty() || machineOpt.isEmpty() || !recipeOpt.get().isCompatibleWith(machineOpt.get());
            });
            recycledByproducts.entrySet().removeIf(e -> catalog.findItem(e.getKey()).isEmpty());
        } else {
            recipeOverrides.clear();
            machineSelections.clear();
            recycledByproducts.clear();
        }
        updateOverridesSummary();
    }

    /**
     * Appends a new target output row to the target table if eligible craftable items exist,
     * picking the first eligible item not yet listed when available.
     */
    private void onAddTarget() {
        stopEditing();
        List<Item> eligible = RecipeResolver.getEligibleTargetItems(catalog);
        if (eligible.isEmpty()) {
            ErrorDialogHelper.showWarning(this, "No Eligible Targets", "No craftable items exist (targets must have at least one recipe with inputs).");
            return;
        }
        Item chosen = pickNextItem(eligible, targetsModel);
        targetsModel.rows.add(new RateRow(chosen, 60.0, getDisplayUnit()));
        int newRow = targetsModel.rows.size() - 1;
        targetsModel.fireTableDataChanged();
        targetsTable.setRowSelectionInterval(newRow, newRow);
        targetsTable.scrollRectToVisible(targetsTable.getCellRect(newRow, 0, true));
    }

    /**
     * Appends a new supplied input row to the input table if the catalog contains at least one item,
     * picking the first catalog item not yet listed when available.
     */
    private void onAddInput() {
        stopEditing();
        List<Item> all = catalog != null ? catalog.getAllItems() : List.of();
        if (all.isEmpty()) {
            ErrorDialogHelper.showWarning(this, "No Items", "Please create or load items in the catalog first.");
            return;
        }
        Double defaultRate = maxModeRadio.isSelected() ? 60.0 : null;
        Item chosen = pickNextItem(all, inputsModel);
        inputsModel.rows.add(new RateRow(chosen, defaultRate, getDisplayUnit()));
        int newRow = inputsModel.rows.size() - 1;
        inputsModel.fireTableDataChanged();
        inputsTable.setRowSelectionInterval(newRow, newRow);
        inputsTable.scrollRectToVisible(inputsTable.getCellRect(newRow, 0, true));
    }

    /**
     * Selects the first candidate item not already present in the given table model,
     * falling back to the first candidate if all are already present.
     *
     * @param candidates ordered list of selectable items
     * @param model      the table model whose existing rows should be avoided when possible
     * @return the chosen default {@link Item}
     */
    private static Item pickNextItem(List<Item> candidates, RateTableModel model) {
        Set<UUID> existingIds = new HashSet<>();
        for (RateRow row : model.rows) {
            if (row.item != null) {
                existingIds.add(row.item.getId());
            }
        }
        for (Item candidate : candidates) {
            if (!existingIds.contains(candidate.getId())) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    /**
     * Removes the currently selected row from the specified rate table and fires a table update.
     *
     * @param table the target or input {@link JTable}
     * @param model the backing {@link RateTableModel}
     */
    private void removeSelectedRow(JTable table, RateTableModel model) {
        stopEditing();
        int row = table.getSelectedRow();
        if (row >= 0) {
            model.rows.remove(row);
            model.fireTableDataChanged();
        }
    }

    /**
     * Opens the modal {@link RecipeOverrideDialog} to configure preferred original or alternative recipes,
     * triggering recalculation immediately when opened for a specific step item.
     *
     * @param focusItemId optional item ID to focus initially in the dialog, or {@code null}
     */
    public void openOverrideDialog(UUID focusItemId) {
        if (catalog == null || catalog.getRecipes().isEmpty()) {
            ErrorDialogHelper.showInfo(this, "No Recipes", "The active catalog has no recipes.");
            return;
        }
        RecipeOverrideDialog dlg = new RecipeOverrideDialog(SwingUtilities.getWindowAncestor(this), catalog, recipeOverrides, focusItemId, iconCache);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            recipeOverrides = new LinkedHashMap<>(dlg.getOverrides());
            updateOverridesSummary();
            if (focusItemId != null) {
                onCalculateRequested.run();
            }
        }
    }

    /**
     * Clears all stored recipe overrides, machine selections, and byproduct recycling toggles.
     */
    public void resetSelections() {
        recipeOverrides.clear();
        machineSelections.clear();
        recycledByproducts.clear();
        updateOverridesSummary();
    }

    /**
     * Updates the summary label displaying the number of active recipe overrides.
     */
    private void updateOverridesSummary() {
        overridesSummaryLabel.setText("Overrides: " + recipeOverrides.size());
    }

    /**
     * Commits any active cell editing in both the targets and inputs tables.
     */
    public void stopEditing() {
        if (targetsTable.isEditing() && targetsTable.getCellEditor() != null) {
            targetsTable.getCellEditor().stopCellEditing();
        }
        if (inputsTable.isEditing() && inputsTable.getCellEditor() != null) {
            inputsTable.getCellEditor().stopCellEditing();
        }
    }

    /**
     * Returns the currently selected display {@link TimeUnit}.
     *
     * @return the selected time unit, defaulting to {@link TimeUnit#PER_MINUTE}
     */
    public TimeUnit getDisplayUnit() {
        TimeUnit u = (TimeUnit) displayUnitCombo.getSelectedItem();
        return u == null ? TimeUnit.PER_MINUTE : u;
    }

    /**
     * Returns the mutable map of per-recipe machine selections.
     *
     * @return map from recipe ID to selected machine ID
     */
    public Map<UUID, UUID> getMachineSelections() {
        return machineSelections;
    }

    /**
     * Returns the mutable map of per-byproduct recycling flags.
     *
     * @return map from byproduct item ID to recycling enabled state
     */
    public Map<UUID, Boolean> getRecycledByproducts() {
        return recycledByproducts;
    }

    /**
     * Returns the single-target combo box used in {@code MAX_OUTPUT} mode (package-private for testing).
     *
     * @return the single target {@link JComboBox}
     */
    JComboBox<Item> getSingleTargetCombo() {
        return singleTargetCombo;
    }

    /**
     * Returns the multi-target output table (package-private for testing).
     *
     * @return the targets {@link JTable}
     */
    JTable getTargetsTable() {
        return targetsTable;
    }

    /**
     * Returns the supplied inputs table (package-private for testing).
     *
     * @return the inputs {@link JTable}
     */
    JTable getInputsTable() {
        return inputsTable;
    }

    /**
     * Collects all UI selections, target/input rows, recipe overrides, machine selections, and
     * recycling flags into a {@link ProductionPlanConfig} snapshot.
     *
     * @return the constructed {@link ProductionPlanConfig}
     */
    public ProductionPlanConfig buildConfig() {
        stopEditing();
        ProductionPlanConfig config = new ProductionPlanConfig();
        config.setMode(maxModeRadio.isSelected() ? CalculationMode.MAX_OUTPUT : CalculationMode.TARGET_OUTPUT);
        config.setDisplayUnit(getDisplayUnit());
        config.setAutoMode(maxModeRadio.isSelected() && autoModeCheckBox.isSelected());

        if (config.getMode() == CalculationMode.TARGET_OUTPUT) {
            for (RateRow row : targetsModel.rows) {
                if (row.item != null) {
                    config.getTargets().add(new RateEntry(row.item.getId(), row.rate, row.unit));
                }
            }
        } else {
            Item selectedTarget = (Item) singleTargetCombo.getSelectedItem();
            if (selectedTarget != null) {
                config.getTargets().add(new RateEntry(selectedTarget.getId(), null, getDisplayUnit()));
            }
        }

        for (RateRow row : inputsModel.rows) {
            if (row.item != null) {
                config.getInputs().add(new RateEntry(row.item.getId(), row.rate, row.unit));
            }
        }

        config.setRecipeOverrides(recipeOverrides);
        config.setMachineSelections(machineSelections);
        config.setRecycledByproducts(recycledByproducts);
        return config;
    }

    /**
     * Restores UI controls, target/input rows, and override maps from a saved {@link ProductionPlanConfig}.
     *
     * @param config the configuration snapshot to load (ignored if {@code null})
     */
    public void loadConfig(ProductionPlanConfig config) {
        if (config == null) return;
        stopEditing();

        if (config.getMode() == CalculationMode.MAX_OUTPUT) {
            maxModeRadio.setSelected(true);
        } else {
            targetModeRadio.setSelected(true);
        }
        onModeChanged();
        displayUnitCombo.setSelectedItem(config.getDisplayUnit());
        autoModeCheckBox.setSelected(config.isAutoMode());

        targetsModel.rows.clear();
        if (config.getMode() == CalculationMode.TARGET_OUTPUT) {
            for (RateEntry e : config.getTargets()) {
                catalog.findItem(e.getItemId()).ifPresent(item -> targetsModel.rows.add(new RateRow(item, e.getRate(), e.getUnit())));
            }
        } else if (!config.getTargets().isEmpty()) {
            UUID tId = config.getTargets().get(0).getItemId();
            catalog.findItem(tId).ifPresent(singleTargetCombo::setSelectedItem);
        }
        targetsModel.fireTableDataChanged();

        inputsModel.rows.clear();
        for (RateEntry e : config.getInputs()) {
            catalog.findItem(e.getItemId()).ifPresent(item -> inputsModel.rows.add(new RateRow(item, e.getRate(), e.getUnit())));
        }
        inputsModel.fireTableDataChanged();

        recipeOverrides = new LinkedHashMap<>(config.getRecipeOverrides());
        machineSelections = new LinkedHashMap<>(config.getMachineSelections());
        recycledByproducts = new LinkedHashMap<>(config.getRecycledByproducts());
        updateOverridesSummary();
    }

    /**
     * Toggles UI busy state during background auto-optimization and binds the Cancel button action.
     *
     * @param busy         {@code true} to disable Calculate and show Cancel, {@code false} to restore idle state
     * @param onCancelAuto callback invoked if the user clicks Cancel while busy (may be {@code null})
     */
    public void setBusy(boolean busy, Runnable onCancelAuto) {
        calculateBtn.setEnabled(!busy);
        cancelAutoBtn.setVisible(busy);
        for (var l : cancelAutoBtn.getActionListeners()) {
            cancelAutoBtn.removeActionListener(l);
        }
        if (busy && onCancelAuto != null) {
            cancelAutoBtn.addActionListener(_ -> onCancelAuto.run());
        }
    }

    private static final class RateRow {
        Item item;
        Double rate;
        TimeUnit unit;

        /**
         * Creates an editable table row pairing an {@link Item}, an optional numeric rate, and a {@link TimeUnit}.
         *
         * @param item the selected catalog item
         * @param rate the item rate (may be {@code null} for unlimited inputs)
         * @param unit the rate time unit (defaults to {@link TimeUnit#PER_MINUTE} if {@code null})
         */
        RateRow(Item item, Double rate, TimeUnit unit) {
            this.item = item;
            this.rate = rate;
            this.unit = unit == null ? TimeUnit.PER_MINUTE : unit;
        }
    }

    private static final class RateTableModel extends AbstractTableModel {
        private final String[] cols;
        final List<RateRow> rows = new ArrayList<>();

        /**
         * Creates a rate table model configured either for optional input limits or required target rates.
         *
         * @param allowBlankRate {@code true} for supplied inputs (optional cap), {@code false} for target outputs
         */
        RateTableModel(boolean allowBlankRate) {
            this.cols = allowBlankRate ? new String[]{"Item", "Rate (optional limit)", "Unit"} : new String[]{"Item", "Target Rate", "Unit"};
        }

        /**
         * Returns the number of item-rate rows in the table.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of columns in the rate table.
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
         * Marks all cells in the rate table as editable.
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
         * @return {@link Item}{@code .class}, {@link Double}{@code .class}, {@link TimeUnit}{@code .class}, or {@link Object}{@code .class}
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Item.class;
                case 1 -> Double.class;
                case 2 -> TimeUnit.class;
                default -> Object.class;
            };
        }

        /**
         * Returns the item, rate value, or time unit at the requested cell.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return the cell value object
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RateRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.item;
                case 1 -> r.rate;
                case 2 -> r.unit;
                default -> null;
            };
        }

        /**
         * Updates the row's item, numeric rate (clearing to {@code null} on blank input), or time unit.
         *
         * @param aValue      the newly edited cell value
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         */
        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            RateRow r = rows.get(rowIndex);
            switch (columnIndex) {
                case 0 -> {
                    if (aValue instanceof Item item) {
                        r.item = item;
                    }
                }
                case 1 -> {
                    if (aValue == null || aValue.toString().isBlank()) {
                        r.rate = null;
                    } else if (aValue instanceof Number n) {
                        r.rate = n.doubleValue();
                    } else {
                        try {
                            r.rate = Double.parseDouble(aValue.toString().trim().replace(',', '.'));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                case 2 -> {
                    if (aValue instanceof TimeUnit u) {
                        r.unit = u;
                    }
                }
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    private final class ItemTableCellRenderer extends DefaultTableCellRenderer {
        /**
         * Renders an {@link Item} table cell with its cached icon and a subtle dropdown indicator.
         *
         * @param table      the JTable being painted
         * @param value      the cell value ({@link Item} or {@code null})
         * @param isSelected {@code true} if the cell is selected
         * @param hasFocus   {@code true} if the cell has focus
         * @param row        zero-based view row index
         * @param column     zero-based view column index
         * @return the configured cell renderer component
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof Item item) {
                setText(item.getName() + "  \u25BE");
                setIcon(iconCache.getIcon(item.getImg(), 18, table::repaint));
            } else {
                setText("-- Select --  \u25BE");
                setIcon(null);
            }
            return this;
        }
    }

    private static final class RateCellEditor extends DefaultCellEditor {
        private final boolean allowBlank;
        private Double parsedValue;

        /**
         * Creates a numeric rate cell editor that optionally accepts blank text as unlimited ({@code null}).
         *
         * @param allowBlank {@code true} to allow clearing the cell to {@code null}, {@code false} to require a positive number
         */
        RateCellEditor(boolean allowBlank) {
            super(new JTextField());
            this.allowBlank = allowBlank;
            ((JTextField) getComponent()).setHorizontalAlignment(SwingConstants.RIGHT);
        }

        /**
         * Initializes the text field editor with the current numeric rate or blank text.
         *
         * @param table      the JTable requesting editing
         * @param value      the current cell value
         * @param isSelected {@code true} if the cell is selected
         * @param row        zero-based view row index
         * @param column     zero-based view column index
         * @return the configured text field editor component
         */
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            parsedValue = value instanceof Number n ? n.doubleValue() : null;
            JTextField tf = (JTextField) getComponent();
            tf.setText(parsedValue == null ? "" : String.valueOf(parsedValue));
            tf.selectAll();
            return tf;
        }

        /**
         * Validates and parses the entered rate text before committing the cell edit.
         *
         * @return {@code true} if the value is blank (when allowed) or a positive finite number
         */
        @Override
        public boolean stopCellEditing() {
            String text = ((JTextField) getComponent()).getText().trim();
            if (text.isEmpty()) {
                if (!allowBlank) {
                    return false;
                }
                parsedValue = null;
                return super.stopCellEditing();
            }
            try {
                double val = Double.parseDouble(text.replace(',', '.'));
                if (!Double.isFinite(val) || val <= 0.0) {
                    return false;
                }
                parsedValue = val;
                return super.stopCellEditing();
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        /**
         * Returns the parsed {@link Double} rate value or {@code null} when left blank.
         *
         * @return the committed rate value
         */
        @Override
        public Object getCellEditorValue() {
            return parsedValue;
        }
    }
}
