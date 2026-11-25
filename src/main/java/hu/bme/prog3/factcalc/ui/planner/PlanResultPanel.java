package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ProductionNode;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.SolverNotice;
import hu.bme.prog3.factcalc.model.TimeUnit;
import hu.bme.prog3.factcalc.service.IconCacheService;
import hu.bme.prog3.factcalc.ui.components.ErrorDialogHelper;
import hu.bme.prog3.factcalc.ui.components.MachineComboBoxEditor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PlanResultPanel extends JPanel {

    private Catalog catalog;
    private ProductionPlanResult currentResult;
    private TimeUnit displayUnit = TimeUnit.PER_MINUTE;
    private final IconCacheService iconCache;
    private final Consumer<UUID> onChangeRecipeRequested;

    private final JTextArea noticeBanner = new JTextArea(2, 40);
    private final JScrollPane noticeScroll = new JScrollPane(noticeBanner);

    private final JTree dependencyTree = new JTree();
    private final StepsTableModel stepsModel;
    private final JTable stepsTable;
    private final EdgesTableModel edgesModel = new EdgesTableModel();
    private final JTable edgesTable = new JTable(edgesModel);

    private final UserInputsSummaryModel userInputsModel = new UserInputsSummaryModel();
    private final RateSummaryModel missingInputsModel = new RateSummaryModel("Calculated Missing Inputs");
    private final RateSummaryModel outputsSummaryModel = new RateSummaryModel("Final Outputs & Byproducts");
    private final MachineTotalsModel machineTotalsModel = new MachineTotalsModel();

    /**
     * Creates the right-hand planner result panel containing the notice banner and tabbed views
     * for the dependency tree, production steps, connections, and aggregated summaries.
     *
     * @param catalog                 initial active factory catalog
     * @param iconCache               icon cache service for rendering tree and editor icons
     * @param onMachineChanged        callback receiving {@code (recipeId, machineId)} when a step's machine changes
     * @param onRecycleToggled        callback receiving {@code (byproductItemId, enabled)} when byproduct reuse is toggled
     * @param onChangeRecipeRequested callback receiving an item ID when the user requests changing a step's recipe
     */
    public PlanResultPanel(
            Catalog catalog,
            IconCacheService iconCache,
            BiConsumer<UUID, UUID> onMachineChanged,
            BiConsumer<UUID, Boolean> onRecycleToggled,
            Consumer<UUID> onChangeRecipeRequested
    ) {
        this.catalog = catalog;
        this.iconCache = iconCache;
        this.onChangeRecipeRequested = onChangeRecipeRequested;
        this.stepsModel = new StepsTableModel(onMachineChanged, onRecycleToggled);
        this.stepsTable = new JTable(stepsModel);

        buildUi();
        clear();
    }

    /**
     * Constructs the notice banner and the four result tabs (Dependency Tree, Production Steps,
     * Connections, and Summary).
     */
    private void buildUi() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        noticeBanner.setEditable(false);
        noticeBanner.setLineWrap(true);
        noticeBanner.setWrapStyleWord(true);
        noticeBanner.setBackground(new Color(245, 248, 252));
        noticeScroll.setBorder(BorderFactory.createTitledBorder("Notices"));
        noticeScroll.setVisible(false);

        JTabbedPane tabs = new JTabbedPane();

        // Tab 1: Dependency Tree
        JPanel treePanel = new JPanel(new BorderLayout(4, 4));
        dependencyTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(
                    JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode node
                        && node.getUserObject() instanceof DependencyTreeModel.TreeNodeData data) {
                    setText(data.getLabel());
                    if (data.getIconPath() != null && !data.getIconPath().isEmpty()) {
                        setIcon(iconCache.getIcon(data.getIconPath(), 18, tree::repaint));
                    }
                    if (data.isRecursiveTag() && !sel) {
                        setForeground(new Color(0, 102, 153));
                    }
                }
                return this;
            }
        });
        JPanel treeTools = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton expandAllBtn = new JButton("Expand All");
        JButton collapseAllBtn = new JButton("Collapse All");
        expandAllBtn.addActionListener(_ -> expandAllTreeNodes());
        collapseAllBtn.addActionListener(_ -> collapseAllTreeNodes());
        treeTools.add(expandAllBtn);
        treeTools.add(collapseAllBtn);
        treePanel.add(treeTools, BorderLayout.NORTH);
        treePanel.add(new JScrollPane(dependencyTree), BorderLayout.CENTER);
        tabs.addTab("Dependency Tree", treePanel);

        // Tab 2: Production Steps
        JPanel stepsPanel = new JPanel(new BorderLayout(4, 4));
        stepsTable.setRowHeight(26);
        stepsTable.getColumnModel().getColumn(2).setCellEditor(
                new MachineComboBoxEditor(() -> catalog, iconCache, stepsModel::getNodeAt)
        );
        JPanel stepsToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton changeRecipeBtn = new JButton("Change Recipe...");
        changeRecipeBtn.addActionListener(_ -> onChangeSelectedStepRecipe());
        stepsToolbar.add(changeRecipeBtn);
        stepsToolbar.add(new JLabel("Tip: Click 'Machine' or 'Recursive Reuse' directly in the table to adjust and recalculate."));
        stepsPanel.add(stepsToolbar, BorderLayout.NORTH);
        stepsPanel.add(new JScrollPane(stepsTable), BorderLayout.CENTER);
        tabs.addTab("Production Steps", stepsPanel);

        // Tab 3: Connections (Graph Edges)
        edgesTable.setRowHeight(24);
        tabs.addTab("Connections", new JScrollPane(edgesTable));

        // Tab 4: Summary
        JPanel summaryGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        summaryGrid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        summaryGrid.add(wrapSummaryTable("User-Supplied Inputs", new JTable(userInputsModel)));
        summaryGrid.add(wrapSummaryTable("Calculated Missing Inputs", new JTable(missingInputsModel)));
        summaryGrid.add(wrapSummaryTable("Final Outputs & Byproducts", new JTable(outputsSummaryModel)));
        summaryGrid.add(wrapSummaryTable("Aggregated Machine Requirements", new JTable(machineTotalsModel)));
        tabs.addTab("Summary", summaryGrid);

        add(noticeScroll, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * Wraps a summary {@link JTable} inside a titled bordered scroll panel.
     *
     * @param title the border title for the summary panel
     * @param table the summary table to wrap
     * @return the constructed {@link JPanel}
     */
    private JPanel wrapSummaryTable(String title, JTable table) {
        table.setRowHeight(22);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    /**
     * Triggers the recipe-override callback for the primary output of the currently selected production step.
     */
    private void onChangeSelectedStepRecipe() {
        int viewRow = stepsTable.getSelectedRow();
        if (viewRow < 0) {
            ErrorDialogHelper.showInfo(this, "No Selection", "Please select a production step first.");
            return;
        }
        ProductionNode node = stepsModel.getNodeAt(stepsTable.convertRowIndexToModel(viewRow));
        if (node == null || catalog == null) {
            return;
        }
        Recipe recipe = catalog.findRecipe(node.getRecipeId()).orElse(null);
        if (recipe != null && !recipe.getOutputs().isEmpty()) {
            onChangeRecipeRequested.accept(recipe.getOutputs().get(0).getId());
        }
    }

    /**
     * Updates the displayed production plan result and refreshes all result tabs.
     *
     * @param catalog     the active factory catalog
     * @param result      the solved {@link ProductionPlanResult} to display (or {@code null} to clear)
     * @param displayUnit the time unit used to format displayed rates
     */
    public void setResult(Catalog catalog, ProductionPlanResult result, TimeUnit displayUnit) {
        this.catalog = catalog;
        this.currentResult = result;
        this.displayUnit = displayUnit == null ? TimeUnit.PER_MINUTE : displayUnit;
        refreshViews();
    }

    /**
     * Updates the rate display time unit and refreshes all result views accordingly.
     *
     * @param displayUnit the new time unit (defaults to {@link TimeUnit#PER_MINUTE} if {@code null})
     */
    public void setDisplayUnit(TimeUnit displayUnit) {
        this.displayUnit = displayUnit == null ? TimeUnit.PER_MINUTE : displayUnit;
        refreshViews();
    }

    /**
     * Clears the current result and resets all result views to an empty state.
     */
    public void clear() {
        this.currentResult = null;
        refreshViews();
    }

    /**
     * Synchronizes the notice banner, dependency tree, steps table, connections table,
     * and summary tables with {@code currentResult} and {@code displayUnit}.
     */
    private void refreshViews() {
        if (stepsTable.isEditing() && stepsTable.getCellEditor() != null) {
            stepsTable.getCellEditor().cancelCellEditing();
        }
        if (currentResult == null) {
            noticeScroll.setVisible(false);
            dependencyTree.setModel(DependencyTreeModel.build(catalog, null, displayUnit));
            stepsModel.setData(catalog, null, displayUnit);
            edgesModel.setData(null, displayUnit);
            userInputsModel.setData(List.of(), displayUnit);
            missingInputsModel.setData(catalog, List.of(), List.of(), displayUnit);
            outputsSummaryModel.setData(catalog, List.of(), List.of(), displayUnit);
            machineTotalsModel.setData(List.of());
            revalidate();
            repaint();
            return;
        }

        List<SolverNotice> notices = currentResult.getNotices();
        if (notices.isEmpty()) {
            noticeScroll.setVisible(false);
        } else {
            StringBuilder sb = new StringBuilder();
            boolean hasWarnOrErr = false;
            for (SolverNotice n : notices) {
                if (n.getLevel() != SolverNotice.Level.INFO) {
                    hasWarnOrErr = true;
                }
                if (!sb.isEmpty()) sb.append('\n');
                sb.append("[").append(n.getLevel()).append("] ").append(n.getMessage());
            }
            noticeBanner.setText(sb.toString());
            noticeBanner.setBackground(hasWarnOrErr ? new Color(255, 245, 225) : new Color(236, 246, 255));
            noticeScroll.setVisible(true);
        }

        dependencyTree.setModel(DependencyTreeModel.build(catalog, currentResult, displayUnit));
        expandAllTreeNodes();
        stepsModel.setData(catalog, currentResult, displayUnit);
        edgesModel.setData(currentResult, displayUnit);
        userInputsModel.setData(currentResult.getUserInputs(), displayUnit);
        missingInputsModel.setData(catalog, currentResult.getCalculatedMissingInputs(), List.of(), displayUnit);
        outputsSummaryModel.setData(catalog, currentResult.getFinalOutputs(), currentResult.getByproductOutputs(), displayUnit);
        machineTotalsModel.setData(currentResult.getMachineTotals());

        revalidate();
        repaint();
    }

    /**
     * Expands all nodes in the dependency tree view.
     */
    private void expandAllTreeNodes() {
        for (int i = 0; i < dependencyTree.getRowCount(); i++) {
            dependencyTree.expandRow(i);
        }
    }

    /**
     * Collapses all descendant branches in the dependency tree view while leaving the root visible.
     */
    private void collapseAllTreeNodes() {
        for (int i = dependencyTree.getRowCount() - 1; i >= 1; i--) {
            dependencyTree.collapseRow(i);
        }
    }

    private static final class UserInputsSummaryModel extends AbstractTableModel {
        private final String[] cols = {"Item", "Required", "Supplied Limit", "Status"};
        private List<ProductionPlanResult.InputSummaryEntry> rows = new ArrayList<>();
        private TimeUnit unit = TimeUnit.PER_MINUTE;

        /**
         * Updates the user-input summary rows and display unit, then refreshes the table.
         *
         * @param rows list of input summary entries (or {@code null} for empty)
         * @param unit display time unit for formatting rates
         */
        void setData(List<ProductionPlanResult.InputSummaryEntry> rows, TimeUnit unit) {
            this.rows = rows == null ? List.of() : new ArrayList<>(rows);
            this.unit = unit;
            fireTableDataChanged();
        }

        /**
         * Returns the number of user input summary rows.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of columns in the user-inputs summary table.
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
         * Returns the item name, required rate, cap limit, or bottleneck/deficit status for the requested cell.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return the cell display string
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ProductionPlanResult.InputSummaryEntry e = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> e.getItemName();
                case 1 -> DependencyTreeModel.formatRate(e.getRequiredRatePerSecond(), unit);
                case 2 -> e.getSuppliedCapPerSecond() == null
                        ? "Unlimited"
                        : DependencyTreeModel.formatRate(e.getSuppliedCapPerSecond(), unit);
                case 3 -> {
                    if (e.isBottleneck()) yield "Bottleneck";
                    if (e.getSuppliedCapPerSecond() != null && e.getRequiredRatePerSecond() > e.getSuppliedCapPerSecond() + 1e-7) {
                        yield "Deficit";
                    }
                    yield "OK";
                }
                default -> "";
            };
        }
    }

    private static final class RateSummaryModel extends AbstractTableModel {
        private final String[] cols = {"Item", "Kind", "Rate"};
        private final List<Object[]> rows = new ArrayList<>();

        /**
         * Constructs a rate summary table model.
         *
         * @param ignoredTitle descriptive title reserved for caller clarity
         */
        RateSummaryModel(String ignoredTitle) {
        }

        /**
         * Populates the table with primary/required rate entries followed by byproduct entries.
         *
         * @param catalog    the active factory catalog for resolving item names
         * @param primary    primary or calculated-missing rate entries
         * @param byproducts byproduct rate entries
         * @param unit       display time unit for formatting rates
         */
        void setData(Catalog catalog, List<RateEntry> primary, List<RateEntry> byproducts, TimeUnit unit) {
            rows.clear();
            for (RateEntry e : primary) {
                String name = catalog != null ? catalog.findItem(e.getItemId()).map(Item::getName).orElse("?") : "?";
                rows.add(new Object[]{name, "Main / Required", DependencyTreeModel.formatRate(e.getRate(), unit)});
            }
            for (RateEntry e : byproducts) {
                String name = catalog != null ? catalog.findItem(e.getItemId()).map(Item::getName).orElse("?") : "?";
                rows.add(new Object[]{name, "Byproduct", DependencyTreeModel.formatRate(e.getRate(), unit)});
            }
            fireTableDataChanged();
        }

        /**
         * Returns the number of rate summary rows.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of columns in the rate summary table.
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
         * Returns the cell value at the requested row and column indices.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return the item name, entry kind, or formatted rate string
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return rows.get(rowIndex)[columnIndex];
        }
    }

    private static final class MachineTotalsModel extends AbstractTableModel {
        private final String[] cols = {"Machine", "Exact Count", "Total Needed (Rounded)"};
        private List<ProductionPlanResult.MachineTotal> rows = new ArrayList<>();

        /**
         * Replaces the aggregated machine total rows and refreshes the table.
         *
         * @param rows list of {@link ProductionPlanResult.MachineTotal} entries (or {@code null} for empty)
         */
        void setData(List<ProductionPlanResult.MachineTotal> rows) {
            this.rows = rows == null ? List.of() : new ArrayList<>(rows);
            fireTableDataChanged();
        }

        /**
         * Returns the number of aggregated machine rows.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of columns in the machine totals table.
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
         * Returns the machine name, exact count, or rounded machine count string at the given cell.
         *
         * @param rowIndex    zero-based row index
         * @param columnIndex zero-based column index
         * @return the formatted cell string
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ProductionPlanResult.MachineTotal mt = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> mt.getMachineName();
                case 1 -> String.format("%.2f", mt.getExactCount());
                case 2 -> mt.getCeilCountSum() + " pcs";
                default -> "";
            };
        }
    }
}
