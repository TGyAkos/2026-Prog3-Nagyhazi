package hu.bme.prog3.factcalc.ui.catalog;

import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.service.IconCacheService;
import hu.bme.prog3.factcalc.ui.components.ErrorDialogHelper;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

public class ItemEditDialog extends JDialog {
    private final JTextField nameField = new JTextField(25);
    private final JTextField imgField = new JTextField(25);
    private final JLabel previewLabel = new JLabel();
    private final IconCacheService iconCache;
    private boolean confirmed = false;

    /**
     * Creates a modal dialog for creating a new item or editing an existing item.
     *
     * @param owner     the parent window for modal centering
     * @param existing  the item to edit, or {@code null} when creating a new item
     * @param iconCache the icon cache service used for live icon previews
     */
    public ItemEditDialog(Window owner, Item existing, IconCacheService iconCache) {
        super(owner, existing == null ? "Add Item" : "Edit Item", ModalityType.APPLICATION_MODAL);
        this.iconCache = iconCache;

        if (existing != null) {
            nameField.setText(existing.getName());
            imgField.setText(existing.getImg());
        }

        buildUi();
        updatePreview();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * Builds the form fields, icon preview area, document listeners, and dialog action buttons.
     */
    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Name:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(nameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
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
        gbc.gridy = 2;
        form.add(new JLabel("Preview:"), gbc);

        previewLabel.setPreferredSize(new Dimension(56, 56));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createEtchedBorder());
        gbc.gridx = 1;
        form.add(previewLabel, gbc);

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

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(_ -> onSave());
        cancelBtn.addActionListener(_ -> dispose());
        buttons.add(okBtn);
        buttons.add(cancelBtn);
        getRootPane().setDefaultButton(okBtn);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    /**
     * Opens a file chooser dialog and populates the icon field with the selected file's path.
     */
    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imgField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Refreshes the icon preview label asynchronously from the current icon path/URL field.
     */
    private void updatePreview() {
        String path = imgField.getText().trim();
        previewLabel.setIcon(iconCache.getIcon(path, 48, () ->
                previewLabel.setIcon(iconCache.getIcon(imgField.getText().trim(), 48, null))));
    }

    /**
     * Validates the form inputs, marks the dialog as confirmed, and closes it on success.
     */
    private void onSave() {
        if (nameField.getText().trim().isEmpty()) {
            ErrorDialogHelper.showError(this, "Validation Error", "Item name must not be blank.");
            return;
        }
        confirmed = true;
        dispose();
    }

    /**
     * Returns whether the user confirmed saving changes in the dialog.
     *
     * @return {@code true} if the user clicked Save with valid input, {@code false} otherwise
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Returns the trimmed item name entered in the dialog.
     *
     * @return the trimmed item name string
     */
    public String getItemName() {
        return nameField.getText().trim();
    }

    /**
     * Returns the trimmed item icon URL or file path entered in the dialog.
     *
     * @return the trimmed icon path/URL string
     */
    public String getItemImg() {
        return imgField.getText().trim();
    }
}
