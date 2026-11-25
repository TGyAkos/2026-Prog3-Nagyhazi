package hu.bme.prog3.factcalc.model;

public enum TimeUnit {
    PER_SECOND("item/sec", 1.0),
    PER_MINUTE("item/min", 60.0),
    PER_HOUR("item/hr", 3600.0);

    private final String label;
    private final double secondsFactor;

    /**
     * Constructs a TimeUnit with a display label and conversion factor relative to one second.
     *
     * @param label         unit label shown in the UI
     * @param secondsFactor number of seconds in one period of this unit
     */
    TimeUnit(String label, double secondsFactor) {
        this.label = label;
        this.secondsFactor = secondsFactor;
    }

    /**
     * Returns the display label of this time unit.
     *
     * @return the unit label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Converts a rate expressed in this unit to items per second.
     *
     * @param rate the flow rate in this unit
     * @return the equivalent rate in items per second
     */
    public double toPerSecond(double rate) {
        return rate / secondsFactor;
    }

    /**
     * Converts a rate in items per second into this time unit.
     *
     * @param ratePerSecond the flow rate in items per second
     * @return the equivalent rate in this unit
     */
    public double fromPerSecond(double ratePerSecond) {
        return ratePerSecond * secondsFactor;
    }

    /**
     * Returns the unit label for display in combo boxes and tables.
     *
     * @return the unit label
     */
    @Override
    public String toString() {
        return label;
    }
}
