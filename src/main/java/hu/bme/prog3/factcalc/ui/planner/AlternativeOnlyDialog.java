package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.service.IconCacheService;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AlternativeOnlyDialog extends JDialog {
    private Recipe selectedRecipe = null;
    private final List<JRadioButton> radioButtons = new ArrayList<>();
    private final List<Recipe> alternatives;

    /**
     * Creates a modal dialog asking the user to pick an alternative recipe (or abort planning)
     * when an item has no original recipe in default mode.
     *
     * @param owner        the parent window for modal centering
     * @param catalog      the active factory catalog for resolving item names
     * @param item         the item that lacks an original recipe
     * @param alternatives list of alternative recipes that produce {@code item}
     * @param iconCache    icon cache service used to display the item icon
     */
    public AlternativeOnlyDialog(Window owner, Catalog catalog, Item item, List<Recipe> alternatives, IconCacheService iconCache) {
        super(owner, "Alternative Recipe Required", ModalityType.APPLICATION_MODAL);
        this.alternatives = alternatives;
        buildUi(catalog, item, iconCache);
        pack();
        setMinimumSize(new Dimension(540, 320));
        setLocationRelativeTo(owner);
    }

    /**
     * Builds the warning header, alternative-recipe radio list, and Continue/Abort buttons.
     *
     * @param catalog   the active factory catalog for formatting recipe inputs/outputs
     * @param item      the item requiring an alternative recipe
     * @param iconCache icon cache service for loading the header item icon
     */
    private void buildUi(Catalog catalog, Item item, IconCacheService iconCache) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        JLabel iconLabel = new JLabel(iconCache.getIcon(item.getImg(), 28, null));
        JLabel title = new JLabel(
                "<html><b>'" + escapeHtml(item.getName()) + "' has no original recipe.</b><br/>"
                        + "In default mode, alternative recipes are disabled. Choose an alternative recipe to continue, or abort planning.</html>");
        title.setFont(title.getFont().deriveFont(Font.PLAIN));
        header.add(iconLabel);
        header.add(title);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < alternatives.size(); i++) {
            Recipe r = alternatives.get(i);
            String desc = String.format(
                    "<html><b>[Alternative] %s</b> (%.2fs)<br/><small>In: %s &rarr; Out: %s</small></html>",
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
        JButton continueBtn = new JButton("Continue with Alternative");
        JButton abortBtn = new JButton("Abort Planning");
        continueBtn.addActionListener(_ -> {
            for (int i = 0; i < radioButtons.size(); i++) {
                if (radioButtons.get(i).isSelected()) {
                    selectedRecipe = alternatives.get(i);
                    break;
                }
            }
            dispose();
        });
        abortBtn.addActionListener(_ -> {
            selectedRecipe = null;
            dispose();
        });
        buttons.add(continueBtn);
        buttons.add(abortBtn);
        getRootPane().setDefaultButton(continueBtn);

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
     * Escapes special HTML characters for safe embedding inside Swing HTML labels.
     *
     * @param s the raw input string
     * @return the HTML-escaped string
     */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Returns the alternative recipe selected by the user, or {@code null} if planning was aborted.
     *
     * @return the selected alternative {@link Recipe}, or {@code null}
     */
    public Recipe getSelectedRecipe() {
        return selectedRecipe;
    }
}
