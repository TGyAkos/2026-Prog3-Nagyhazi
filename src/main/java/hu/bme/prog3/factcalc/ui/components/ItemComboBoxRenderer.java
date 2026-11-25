package hu.bme.prog3.factcalc.ui.components;

import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.service.IconCacheService;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

public class ItemComboBoxRenderer extends DefaultListCellRenderer {
    private final IconCacheService iconCache;

    /**
     * Creates a list cell renderer that loads item and machine icons via the given cache.
     *
     * @param iconCache the icon cache service used to fetch and scale icons
     */
    public ItemComboBoxRenderer(IconCacheService iconCache) {
        this.iconCache = iconCache;
    }

    /**
     * Configures the renderer label with the item or machine name and its cached icon.
     *
     * @param list         the JList being painted
     * @param value        the cell value (expected to be an {@link Item}, {@link Machine}, or {@code null})
     * @param index        the cell index in the list
     * @param isSelected   {@code true} if the cell is currently selected
     * @param cellHasFocus {@code true} if the cell currently has keyboard focus
     * @return the configured Swing component used to paint the cell
     */
    @Override
    public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
    ) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Machine machine) {
            setText(machine.getName() + " [" + machine.getType() + ", " + machine.getMultiplier() + "x]");
            setIcon(iconCache.getIcon(machine.getImg(), 20, list::repaint));
        } else if (value instanceof Item item) {
            setText(item.getName());
            setIcon(iconCache.getIcon(item.getImg(), 20, list::repaint));
        } else if (value == null) {
            setText("-- Select --");
            setIcon(null);
        }
        return this;
    }
}
