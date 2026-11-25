package hu.bme.prog3.factcalc.model;

public enum CalculationMode {
    TARGET_OUTPUT("Production Line from Target Output"), MAX_OUTPUT("Max Output from Inputs");

    private final String displayName;

    /**
     * Constructs a CalculationMode enum constant with a human-readable label.
     *
     * @param displayName the UI display name for this mode
     */
    CalculationMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable display name for this calculation mode.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the display name when rendered in Swing controls.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return displayName;
    }
}
