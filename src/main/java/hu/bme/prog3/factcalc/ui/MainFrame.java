package hu.bme.prog3.factcalc.ui;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.persistence.CatalogRepository;
import hu.bme.prog3.factcalc.service.CatalogService;
import hu.bme.prog3.factcalc.service.IconCacheService;
import hu.bme.prog3.factcalc.ui.catalog.ItemManagerPanel;
import hu.bme.prog3.factcalc.ui.catalog.MachineManagerPanel;
import hu.bme.prog3.factcalc.ui.catalog.RecipeManagerPanel;
import hu.bme.prog3.factcalc.ui.components.ErrorDialogHelper;
import hu.bme.prog3.factcalc.ui.planner.PlannerPanel;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class MainFrame extends JFrame {

    private final CatalogService catalogService;
    private final CatalogRepository catalogRepository = new CatalogRepository();
    private final IconCacheService iconCache = new IconCacheService();

    private final PlannerPanel plannerPanel;
    private final ItemManagerPanel itemManagerPanel;
    private final MachineManagerPanel machineManagerPanel;
    private final RecipeManagerPanel recipeManagerPanel;
    private final JLabel statusBar = new JLabel();

    private Path currentCatalogPath = null;
    private boolean dirty = false;

    /**
     * Constructs the main application window with an empty initial catalog, initializes all workspace tabs,
     * and updates the status bar.
     */
    public MainFrame() {
        super("Factcalc");

        // Start with an empty catalog (default preset can be loaded via View -> Load Default Catalog)
        Catalog initialCatalog = new Catalog("Untitled Catalog");
        this.catalogService = new CatalogService(initialCatalog);

        this.plannerPanel = new PlannerPanel(catalogService, iconCache);
        this.itemManagerPanel = new ItemManagerPanel(catalogService, iconCache, this::onCatalogModified);
        this.machineManagerPanel = new MachineManagerPanel(catalogService, iconCache, this::onCatalogModified);
        this.recipeManagerPanel = new RecipeManagerPanel(catalogService, iconCache, this::onCatalogModified);

        buildUi();
        updateStatusBar();
    }

    /**
     * Assembles the main window layout, tabbed workspace, menu bar, status bar, and window close listener.
     */
    private void buildUi() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });

        setJMenuBar(createMenuBar());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Production Line Planner", plannerPanel);
        tabs.addTab("Items", itemManagerPanel);
        tabs.addTab("Machines", machineManagerPanel);
        tabs.addTab("Recipes", recipeManagerPanel);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        statusPanel.add(statusBar, BorderLayout.WEST);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        setMinimumSize(new Dimension(1080, 700));
        setSize(1240, 780);
        setLocationRelativeTo(null);
    }

    /**
     * Creates and configures the main menu bar with File (catalog/plan/exit) and View (preset loading) menus.
     *
     * @return the constructed {@link JMenuBar}
     */
    private JMenuBar createMenuBar() {
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem newCatalogItem = new JMenuItem("New Catalog");
        newCatalogItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask));
        newCatalogItem.addActionListener(_ -> newCatalog());

        JMenuItem openCatalogItem = new JMenuItem("Open Catalog...");
        openCatalogItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcutMask));
        openCatalogItem.addActionListener(_ -> openCatalog());

        JMenuItem saveCatalogItem = new JMenuItem("Save Catalog");
        saveCatalogItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask));
        saveCatalogItem.addActionListener(_ -> saveCatalog(false));

        JMenuItem saveCatalogAsItem = new JMenuItem("Save Catalog As...");
        saveCatalogAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask | KeyEvent.SHIFT_DOWN_MASK));
        saveCatalogAsItem.addActionListener(_ -> saveCatalog(true));

        JMenuItem openPlanItem = new JMenuItem("Open Production Line...");
        openPlanItem.addActionListener(_ -> plannerPanel.loadPlan());

        JMenuItem savePlanItem = new JMenuItem("Save Production Line...");
        savePlanItem.addActionListener(_ -> plannerPanel.savePlan());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(_ -> exitApplication());

        fileMenu.add(newCatalogItem);
        fileMenu.add(openCatalogItem);
        fileMenu.add(saveCatalogItem);
        fileMenu.add(saveCatalogAsItem);
        fileMenu.addSeparator();
        fileMenu.add(openPlanItem);
        fileMenu.add(savePlanItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        JMenuItem loadDefaultItem = new JMenuItem("Load Default Catalog");
        loadDefaultItem.addActionListener(_ -> loadDefaultCatalog());
        viewMenu.add(loadDefaultItem);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        return menuBar;
    }

    /**
     * Prompts for a new catalog name after confirming discard of unsaved changes, and initializes an empty catalog.
     */
    private void newCatalog() {
        if (!confirmDiscardChanges()) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Enter catalog name:", "New Catalog", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        Catalog catalog = new Catalog(name.isBlank() ? "Untitled Catalog" : name.trim());
        replaceCatalog(catalog, null, false);
    }

    /**
     * Opens a JSON catalog file selected by the user after confirming discard of any unsaved changes.
     */
    private void openCatalog() {
        if (!confirmDiscardChanges()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Catalog JSON (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            Catalog loaded = catalogRepository.load(path);
            replaceCatalog(loaded, path, false);
        } catch (IOException ex) {
            ErrorDialogHelper.showError(this, "Open Catalog Error", ex.getMessage());
        }
    }

    /**
     * Saves the active catalog to its current path or prompts for a destination file if {@code saveAs} is true
     * or no path is yet associated.
     *
     * @param saveAs {@code true} to always prompt for a new file path, {@code false} to reuse the current path
     */
    private void saveCatalog(boolean saveAs) {
        Path targetPath = currentCatalogPath;
        if (saveAs || targetPath == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Catalog JSON (*.json)", "json"));
            chooser.setSelectedFile(new File(
                    catalogService.getCatalog().getName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".json"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            targetPath = chooser.getSelectedFile().toPath();
            if (!targetPath.getFileName().toString().toLowerCase().endsWith(".json")) {
                targetPath = targetPath.resolveSibling(targetPath.getFileName() + ".json");
            }
        }
        try {
            catalogRepository.save(catalogService.getCatalog(), targetPath);
            currentCatalogPath = targetPath;
            dirty = false;
            updateStatusBar();
            ErrorDialogHelper.showInfo(this, "Catalog Saved", "Catalog saved to:\n" + targetPath);
        } catch (IOException ex) {
            ErrorDialogHelper.showError(this, "Save Catalog Error", ex.getMessage());
        }
    }

    /**
     * Loads the bundled default factory preset catalog after confirming discard of unsaved changes.
     */
    private void loadDefaultCatalog() {
        if (!confirmDiscardChanges()) {
            return;
        }
        try {
            Catalog preset = catalogRepository.loadDefaultPreset();
            replaceCatalog(preset, null, false);
        } catch (IOException ex) {
            ErrorDialogHelper.showError(this, "Preset Load Error", ex.getMessage());
        }
    }

    /**
     * Replaces the active catalog across the service and all workspace panels, clears stale planner results,
     * and refreshes the status bar.
     *
     * @param newCatalog the new catalog instance to activate
     * @param path       the file path associated with the catalog, or {@code null} if unsaved/preset
     * @param isDirty    whether the catalog should initially be marked as having unsaved changes
     */
    private void replaceCatalog(Catalog newCatalog, Path path, boolean isDirty) {
        catalogService.setCatalog(newCatalog);
        this.currentCatalogPath = path;
        this.dirty = isDirty;
        itemManagerPanel.refresh();
        machineManagerPanel.refresh();
        recipeManagerPanel.refresh();
        plannerPanel.clearResults();
        plannerPanel.refreshCatalog();
        updateStatusBar();
    }

    /**
     * Marks the active catalog as dirty and refreshes all manager and planner views after an edit.
     */
    private void onCatalogModified() {
        this.dirty = true;
        itemManagerPanel.refresh();
        machineManagerPanel.refresh();
        recipeManagerPanel.refresh();
        plannerPanel.refreshCatalog();
        updateStatusBar();
    }

    /**
     * Checks whether the catalog has unsaved changes and, if so, asks the user to confirm discarding them.
     *
     * @return {@code true} if there are no unsaved changes or the user confirmed discarding them
     */
    private boolean confirmDiscardChanges() {
        if (!dirty) {
            return true;
        }
        return ErrorDialogHelper.confirm(
                this,
                "Unsaved Changes",
                "The current catalog has unsaved changes. Discard them and continue?"
        );
    }

    /**
     * Updates the window title and bottom status bar with the current catalog name, dirty flag, and entity counts.
     */
    private void updateStatusBar() {
        Catalog c = catalogService.getCatalog();
        String dirtyMark = dirty ? " *" : "";
        setTitle("Factcalc - " + c.getName() + dirtyMark);
        statusBar.setText(String.format(
                "Catalog: %s%s   |   Items: %d   |   Machines: %d   |   Recipes: %d",
                c.getName(),
                dirty ? " (Modified)" : "",
                c.getPlainItems().size(),
                c.getMachines().size(),
                c.getRecipes().size()
        ));
    }

    /**
     * Confirms discarding any unsaved changes, shuts down the icon cache executor, and closes the main frame.
     */
    private void exitApplication() {
        if (!confirmDiscardChanges()) {
            return;
        }
        iconCache.shutdown();
        dispose();
    }
}
