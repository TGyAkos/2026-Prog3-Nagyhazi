package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.CalculationMode;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.SavedPlan;
import hu.bme.prog3.factcalc.persistence.PlanRepository;
import hu.bme.prog3.factcalc.service.CatalogService;
import hu.bme.prog3.factcalc.service.IconCacheService;
import hu.bme.prog3.factcalc.solver.ProductionSolver;
import hu.bme.prog3.factcalc.solver.RecipeResolutionCallback;
import hu.bme.prog3.factcalc.solver.SolverException;
import hu.bme.prog3.factcalc.ui.components.ErrorDialogHelper;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Window;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class PlannerPanel extends JPanel {

    private final CatalogService catalogService;
    private final IconCacheService iconCache;
    private final ProductionSolver solver = new ProductionSolver();
    private final PlanRepository planRepository = new PlanRepository();

    private final PlanConfigPanel configPanel;
    private final PlanResultPanel resultPanel;
    private final JProgressBar progressBar = new JProgressBar();

    private ProductionPlanConfig lastSolvedConfig;
    private ProductionPlanResult lastSolvedResult;
    private SwingWorker<ProductionPlanResult, Void> activeWorker;

    /**
     * Creates the main production-line planner workspace pairing a {@link PlanConfigPanel}
     * with a {@link PlanResultPanel} inside a split pane.
     *
     * @param catalogService service providing the active catalog
     * @param iconCache      icon cache service shared across planner components
     */
    public PlannerPanel(CatalogService catalogService, IconCacheService iconCache) {
        this.catalogService = catalogService;
        this.iconCache = iconCache;

        this.configPanel = new PlanConfigPanel(
                catalogService.getCatalog(),
                iconCache,
                this::onDisplayUnitChanged,
                this::calculate,
                this::savePlan,
                this::loadPlan
        );

        this.resultPanel = new PlanResultPanel(
                catalogService.getCatalog(),
                iconCache,
                (recipeId, machineId) -> {
                    configPanel.getMachineSelections().put(recipeId, machineId);
                    calculate();
                },
                (byproductItemId, recycle) -> {
                    configPanel.getRecycledByproducts().put(byproductItemId, recycle);
                    calculate();
                },
                this::openRecipeOverrideForItem
        );

        buildUi();
    }

    /**
     * Forwards display time-unit changes from the configuration panel to the result panel.
     *
     * @param unit the newly selected {@link hu.bme.prog3.factcalc.model.TimeUnit}
     */
    private void onDisplayUnitChanged(hu.bme.prog3.factcalc.model.TimeUnit unit) {
        if (resultPanel != null) {
            resultPanel.setDisplayUnit(unit);
        }
    }

    /**
     * Opens the recipe override dialog focused on the specified item ID.
     *
     * @param itemId the item ID whose producer recipe the user wants to override
     */
    private void openRecipeOverrideForItem(java.util.UUID itemId) {
        configPanel.openOverrideDialog(itemId);
    }

    /**
     * Configures the indeterminate auto-optimization progress bar and the horizontal split pane layout.
     */
    private void buildUi() {
        setLayout(new BorderLayout());

        progressBar.setIndeterminate(true);
        progressBar.setString("Optimizing alternative recipes...");
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, configPanel, resultPanel);
        splitPane.setDividerLocation(420);
        splitPane.setResizeWeight(0.32);

        add(progressBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
    }

    /**
     * Synchronizes the configuration panel with the active catalog and recalculates any displayed
     * production plan non-interactively (or clears stale results if recalculation requires user input or fails).
     */
    public void refreshCatalog() {
        Catalog catalog = catalogService.getCatalog();
        configPanel.refreshCatalog(catalog);
        if (lastSolvedResult != null) {
            recalculateSilently(catalog);
        } else {
            resultPanel.clear();
        }
    }

    /**
     * Re-solves the current configuration against an updated catalog without opening modal dialogs,
     * reusing previously resolved recipe choices where still valid and clearing stale results otherwise.
     *
     * @param catalog the updated active factory catalog
     */
    private void recalculateSilently(Catalog catalog) {
        ProductionPlanConfig config = configPanel.buildConfig();
        if (config.getTargets().isEmpty()
                || (config.getMode() == CalculationMode.MAX_OUTPUT && config.getInputs().isEmpty())) {
            clearResults();
            return;
        }
        if (config.getMode() == CalculationMode.MAX_OUTPUT && config.isAutoMode()) {
            runAutoInBackground(catalog, config, true);
            return;
        }
        Map<UUID, UUID> prevResolved = lastSolvedResult != null
                ? lastSolvedResult.getResolvedRecipes()
                : Map.of();
        RecipeResolutionCallback silentCallback = new RecipeResolutionCallback() {
            @Override
            public Recipe chooseOriginalRecipe(Item item, List<Recipe> originalCandidates) {
                UUID prevId = prevResolved.get(item.getId());
                if (prevId != null) {
                    for (Recipe candidate : originalCandidates) {
                        if (candidate.getId().equals(prevId)) {
                            return candidate;
                        }
                    }
                }
                return null;
            }

            @Override
            public Recipe confirmAlternativeRecipe(Item item, List<Recipe> alternativeCandidates) {
                UUID prevId = prevResolved.get(item.getId());
                if (prevId != null) {
                    for (Recipe candidate : alternativeCandidates) {
                        if (candidate.getId().equals(prevId)) {
                            return candidate;
                        }
                    }
                }
                return null;
            }
        };
        try {
            ProductionPlanResult result = solver.solve(catalog, config, silentCallback);
            this.lastSolvedConfig = config;
            this.lastSolvedResult = result;
            resultPanel.setResult(catalog, result, config.getDisplayUnit());
        } catch (SolverException ex) {
            clearResults();
        }
    }

    /**
     * Clears the last solved plan and empties the result panel.
     */
    public void clearResults() {
        lastSolvedResult = null;
        lastSolvedConfig = null;
        resultPanel.clear();
    }

    /**
     * Builds the current {@link ProductionPlanConfig} and runs {@link ProductionSolver}
     * (synchronously with interactive conflict dialogs in normal mode, or asynchronously via
     * {@link SwingWorker} when {@code MAX_OUTPUT} + auto mode is active).
     */
    public void calculate() {
        Catalog catalog = catalogService.getCatalog();
        ProductionPlanConfig config = configPanel.buildConfig();

        if (config.getMode() == CalculationMode.MAX_OUTPUT && config.isAutoMode()) {
            runAutoInBackground(catalog, config, false);
            return;
        }

        RecipeResolutionCallback callback = new RecipeResolutionCallback() {
            @Override
            public Recipe chooseOriginalRecipe(Item item, List<Recipe> originalCandidates) {
                Window owner = SwingUtilities.getWindowAncestor(PlannerPanel.this);
                RecipeConflictDialog dlg = new RecipeConflictDialog(owner, catalog, item, originalCandidates, iconCache);
                dlg.setVisible(true);
                return dlg.getSelectedRecipe();
            }

            @Override
            public Recipe confirmAlternativeRecipe(Item item, List<Recipe> alternativeCandidates) {
                Window owner = SwingUtilities.getWindowAncestor(PlannerPanel.this);
                AlternativeOnlyDialog dlg = new AlternativeOnlyDialog(owner, catalog, item, alternativeCandidates, iconCache);
                dlg.setVisible(true);
                return dlg.getSelectedRecipe();
            }
        };

        try {
            ProductionPlanResult result = solver.solve(catalog, config, callback);
            this.lastSolvedConfig = config;
            this.lastSolvedResult = result;
            resultPanel.setResult(catalog, result, config.getDisplayUnit());
        } catch (SolverException ex) {
            ErrorDialogHelper.showError(this, "Calculation Error", ex.getMessage());
        }
    }

    /**
     * Executes discrete auto-optimization on a background {@link SwingWorker} while showing
     * an indeterminate progress bar and allowing user cancellation.
     *
     * @param catalog the active factory catalog
     * @param config  the {@code MAX_OUTPUT} + auto-mode plan configuration
     * @param silent  {@code true} when triggered by a background catalog refresh so errors clear stale results silently
     */
    private void runAutoInBackground(Catalog catalog, ProductionPlanConfig config, boolean silent) {
        if (activeWorker != null && !activeWorker.isDone()) {
            activeWorker.cancel(true);
        }
        progressBar.setVisible(true);
        configPanel.setBusy(true, () -> {
            if (activeWorker != null) {
                activeWorker.cancel(true);
            }
        });

        activeWorker = new SwingWorker<>() {
            @Override
            protected ProductionPlanResult doInBackground() {
                return solver.solve(catalog, config, null);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                configPanel.setBusy(false, null);
                try {
                    ProductionPlanResult result = get();
                    lastSolvedConfig = config;
                    lastSolvedResult = result;
                    resultPanel.setResult(catalog, result, config.getDisplayUnit());
                } catch (CancellationException ex) {
                    if (!silent) {
                        ErrorDialogHelper.showInfo(PlannerPanel.this, "Cancelled", "Auto optimization was cancelled.");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    if (silent) {
                        clearResults();
                    } else {
                        Throwable cause = ex.getCause();
                        String msg = cause != null ? cause.getMessage() : ex.getMessage();
                        ErrorDialogHelper.showError(PlannerPanel.this, "Auto Optimization Error", msg);
                    }
                }
            }
        };
        activeWorker.execute();
    }

    /**
     * Prompts for a plan name and destination file, then serializes the most recent
     * {@link ProductionPlanConfig} and {@link ProductionPlanResult} as a {@link SavedPlan} JSON file.
     */
    public void savePlan() {
        if (lastSolvedResult == null || lastSolvedConfig == null) {
            ErrorDialogHelper.showInfo(this, "Nothing to Save", "Please calculate a production line before saving a plan.");
            return;
        }
        String planName = JOptionPane.showInputDialog(this, "Enter a name for this production plan:", "Save Production Plan", JOptionPane.PLAIN_MESSAGE);
        if (planName == null || planName.isBlank()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Factcalc Plan (*.factplan.json)", "json"));
        chooser.setSelectedFile(new File(planName.trim().replaceAll("[^a-zA-Z0-9._-]", "_") + ".factplan.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path path = chooser.getSelectedFile().toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".json")) {
            path = path.resolveSibling(path.getFileName() + ".factplan.json");
        }

        try {
            SavedPlan saved = new SavedPlan(
                    planName.trim(),
                    catalogService.getCatalog().getName(),
                    Instant.now(),
                    lastSolvedConfig,
                    lastSolvedResult
            );
            planRepository.save(saved, path);
            ErrorDialogHelper.showInfo(this, "Plan Saved", "Saved production plan to:\n" + path);
        } catch (Exception ex) {
            ErrorDialogHelper.showError(this, "Save Error", "Failed to save plan: " + ex.getMessage());
        }
    }

    /**
     * Prompts the user to select a saved plan JSON file, validates its references against the active catalog
     * (confirming read-only inspection if mismatches exist), and restores the configuration and result views.
     */
    public void loadPlan() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Factcalc Plan (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            SavedPlan loaded = planRepository.load(path);
            Catalog catalog = catalogService.getCatalog();
            List<String> missing = planRepository.validateAgainstCatalog(loaded, catalog);

            if (!missing.isEmpty()) {
                int choice = JOptionPane.showOptionDialog(
                        this,
                        "The saved plan references items, machines, or recipes not found in the active catalog ('"
                                + catalog.getName() + "'):\n\n"
                                + String.join("\n", missing.subList(0, Math.min(8, missing.size())))
                                + (missing.size() > 8 ? "\n..." : "")
                                + "\n\nOpen saved snapshot in read-only view anyway?",
                        "Catalog Mismatch",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        new String[]{"Inspect Snapshot", "Cancel"},
                        "Inspect Snapshot"
                );
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            if (loaded.getConfig() != null) {
                configPanel.loadConfig(loaded.getConfig());
            }
            this.lastSolvedConfig = loaded.getConfig();
            this.lastSolvedResult = loaded.getResult();
            if (loaded.getResult() != null) {
                resultPanel.setResult(catalog, loaded.getResult(),
                        loaded.getConfig() != null ? loaded.getConfig().getDisplayUnit() : configPanel.getDisplayUnit());
            }
        } catch (Exception ex) {
            ErrorDialogHelper.showError(this, "Load Error", "Failed to load plan: " + ex.getMessage());
        }
    }

    /**
     * Returns the planner's left-hand configuration panel (package-private for testing).
     *
     * @return the {@link PlanConfigPanel}
     */
    PlanConfigPanel getConfigPanel() {
        return configPanel;
    }

    /**
     * Returns the planner's right-hand result panel (package-private for testing).
     *
     * @return the {@link PlanResultPanel}
     */
    PlanResultPanel getResultPanel() {
        return resultPanel;
    }

    /**
     * Returns the most recently solved plan result (package-private for testing).
     *
     * @return the last {@link ProductionPlanResult}, or {@code null} if cleared or unsolved
     */
    ProductionPlanResult getLastSolvedResult() {
        return lastSolvedResult;
    }
}
