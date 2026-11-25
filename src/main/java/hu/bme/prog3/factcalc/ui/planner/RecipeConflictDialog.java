package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.service.IconCacheService;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.BoxLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeConflictDialog extends JDialog {
    private Recipe selectedRecipe = null;
    private final List<JRadioButton> radioButtons = new ArrayList<>();
    private final List<Recipe> candidates;

    /**
     * Creates a modal dialog prompting the user to pick one original recipe when multiple
     * original recipes produce the same item.
     *
     * @param owner      the parent window for modal centering
     * @param catalog    the active factory catalog used to resolve item names
     * @param item       the item with conflicting original producer recipes
     * @param candidates list of candidate original recipes producing {@code item}
     * @param iconCache  icon cache service used to display the item icon
     */
    public RecipeConflictDialog(Window owner, Catalog catalog, Item item, List<Recipe> candidates, IconCacheService iconCache) {
        super(owner, "Multiple Original Recipes Found", ModalityType.APPLICATION_MODAL);
        this.candidates = candidates;
        buildUi(catalog, item, iconCache);
        pack();
        setMinimumSize(new Dimension(520, 300));
        setLocationRelativeTo(owner);
    }

    /**
     * Builds the header, radio-button candidate list, and OK/Cancel action buttons.
     *
     * @param catalog   the active factory catalog for formatting recipe inputs/outputs
     * @param item      the item experiencing the conflict
     * @param iconCache icon cache service used to load the header item icon
     */
    private void buildUi(Catalog catalog, Item item, IconCacheService iconCache) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        JLabel iconLabel = new JLabel(iconCache.getIcon(item.getImg(), 28, null));
        JLabel title = new JLabel("Item '" + item.getName() + "' has multiple original recipes. Choose one to use:");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        header.add(iconLabel);
        header.add(title);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < candidates.size(); i++) {
            Recipe r = candidates.get(i);
            String desc = String.format(
                    "<html><b>%s</b> (%.2fs)<br/><small>In: %s &rarr; Out: %s</small></html>",
                    escapeHtml(r.getName()),
                    r.getTime(),
                    escapeHtml(formatAmounts(catalog, r.getInputs())),
                    escapeHtml(formatAmounts(catalog, r.getOutputs()))
            );
            JRadioButton rb = new JRadioButton(desc, i == 0);
            rb.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
            group.add(rb);
            radioButtons.add(rb);
            listPanel.add(rb);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("Use Selected Recipe");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(_ -> {
            for (int i = 0; i < radioButtons.size(); i++) {
                if (radioButtons.get(i).isSelected()) {
                    selectedRecipe = candidates.get(i);
                    break;
                }
            }
            dispose();
        });
        cancelBtn.addActionListener(_ -> {
            selectedRecipe = null;
            dispose();
        });
        buttons.add(okBtn);
        buttons.add(cancelBtn);
        getRootPane().setDefaultButton(okBtn);

        setLayout(new BorderLayout(8, 8));
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(listPanel), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    /**
     * Formats a list of recipe item quantities into a readable comma-separated summary.
     *
     * @param catalog the active factory catalog for resolving item names
     * @param list    list of item quantities per cycle
     * @return formatted summary string, or {@code "None"} if empty
     */
    private String formatAmounts(Catalog catalog, List<ItemAmount> list) {
        if (list.isEmpty()) return "None";
        return list.stream()
                .map(ia -> ia.getPiece() + "x " + catalog.findItem(ia.getId()).map(Item::getName).orElse("?"))
                .collect(Collectors.joining(", "));
    }

    /**
     * Escapes special HTML characters for safe embedding inside Swing HTML label strings.
     *
     * @param s the raw input string
     * @return the HTML-escaped string
     */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Returns the recipe chosen by the user, or {@code null} if the dialog was cancelled.
     *
     * @return the selected {@link Recipe}, or {@code null}
     */
    public Recipe getSelectedRecipe() {
        return selectedRecipe;
    }
}
