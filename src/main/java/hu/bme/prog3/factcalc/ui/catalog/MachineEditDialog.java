package hu.bme.prog3.factcalc.ui.catalog;

import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.service.IconCacheService;
import hu.bme.prog3.factcalc.ui.components.ErrorDialogHelper;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
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
import java.util.Set;

public class MachineEditDialog extends JDialog {
    private final JTextField nameField = new JTextField(24);
    private final JComboBox<String> typeCombo;
    private final JSpinner multiplierSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 10000.0, 0.1));
    private final JTextField imgField = new JTextField(24);
    private final JLabel previewLabel = new JLabel();
    private final IconCacheService iconCache;
    private boolean confirmed = false;

    /**
     * Creates a modal dialog for adding a new machine or editing an existing one.
     *
     * @param owner         the parent window for modal centering
     * @param existing      the machine to edit, or {@code null} when creating a new machine
     * @param existingTypes set of known machine types to populate the editable type dropdown
     * @param iconCache     icon cache service used for live icon previews
     */
    public MachineEditDialog(Window owner, Machine existing, Set<String> existingTypes, IconCacheService iconCache) {
        super(owner, existing == null ? "Add Machine" : "Edit Machine", ModalityType.APPLICATION_MODAL);
        this.iconCache = iconCache;
        this.typeCombo = new JComboBox<>(existingTypes.toArray(new String[0]));
        this.typeCombo.setEditable(true);

        if (existing != null) {
            nameField.setText(existing.getName());
            typeCombo.setSelectedItem(existing.getType());
            multiplierSpinner.setValue(existing.getMultiplier());
            imgField.setText(existing.getImg());
        } else if (typeCombo.getItemCount() > 0) {
            typeCombo.setSelectedIndex(0);
        }

        buildUi();
        updatePreview();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * Builds the machine form fields, preview box, document listeners, and Save/Cancel buttons.
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
        form.add(new JLabel("Type:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(typeCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Multiplier:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(multiplierSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
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
        gbc.gridy = 4;
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
     * Opens a file chooser dialog and inserts the chosen image path into the icon field.
     */
    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imgField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Updates the icon preview label asynchronously from the current icon path/URL text.
     */
    private void updatePreview() {
        String path = imgField.getText().trim();
        previewLabel.setIcon(iconCache.getIcon(path, 48, () ->
                previewLabel.setIcon(iconCache.getIcon(imgField.getText().trim(), 48, null))));
    }

    /**
     * Validates machine name, type, and multiplier, marks the dialog confirmed, and closes it.
     */
    private void onSave() {
        if (nameField.getText().trim().isEmpty()) {
            ErrorDialogHelper.showError(this, "Validation Error", "Machine name must not be blank.");
            return;
        }
        String type = getMachineType();
        if (type.isEmpty()) {
            ErrorDialogHelper.showError(this, "Validation Error", "Machine type must not be blank.");
            return;
        }
        if (getMultiplier() <= 0.0) {
            ErrorDialogHelper.showError(this, "Validation Error", "Machine multiplier must be greater than 0.");
            return;
        }
        confirmed = true;
        dispose();
    }

    /**
     * Returns whether the user confirmed saving changes in the dialog.
     *
     * @return {@code true} if saved with valid inputs, {@code false} if cancelled
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Returns the trimmed machine name entered by the user.
     *
     * @return the trimmed machine name
     */
    public String getMachineName() {
        return nameField.getText().trim();
    }

    /**
     * Returns the trimmed machine type selected or typed in the type combo box.
     *
     * @return the machine type string, or an empty string if unset
     */
    public String getMachineType() {
        Object val = typeCombo.getEditor().getItem();
        return val == null ? "" : val.toString().trim();
    }

    /**
     * Returns the speed multiplier value entered in the multiplier spinner.
     *
     * @return the machine speed multiplier
     */
    public double getMultiplier() {
        return ((Number) multiplierSpinner.getValue()).doubleValue();
    }

    /**
     * Returns the trimmed machine icon URL or file path entered by the user.
     *
     * @return the trimmed icon path/URL string
     */
    public String getMachineImg() {
        return imgField.getText().trim();
    }
}
