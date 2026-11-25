package hu.bme.prog3.factcalc.model;

import java.util.Objects;
import java.util.UUID;

public class RateEntry {
    private UUID itemId;
    private Double rate;
    private TimeUnit unit;

    /**
     * Creates an empty RateEntry defaulting to items per minute.
     */
    public RateEntry() {
        this.unit = TimeUnit.PER_MINUTE;
    }

    /**
     * Creates a RateEntry for an item with an optional rate and time unit.
     *
     * @param itemId the item UUID
     * @param rate   the flow rate (may be null for unlimited inputs in target mode)
     * @param unit   the time unit for the rate (defaults to PER_MINUTE if null)
     */
    public RateEntry(UUID itemId, Double rate, TimeUnit unit) {
        this.itemId = itemId;
        this.rate = rate;
        this.unit = unit == null ? TimeUnit.PER_MINUTE : unit;
    }

    /**
     * Creates a copy of an existing RateEntry.
     *
     * @param other the RateEntry to copy
     */
    public RateEntry(RateEntry other) {
        this(other.itemId, other.rate, other.unit);
    }

    /**
     * Returns the item UUID for this entry.
     *
     * @return the item ID
     */
    public UUID getItemId() {
        return itemId;
    }

    /**
     * Sets the item UUID for this entry.
     *
     * @param itemId the new item ID
     */
    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    /**
     * Returns the flow rate in the configured time unit, or null if unlimited.
     *
     * @return the rate value or null
     */
    public Double getRate() {
        return rate;
    }

    /**
     * Sets the flow rate in the configured time unit.
     *
     * @param rate the new rate value (null for unlimited)
     */
    public void setRate(Double rate) {
        this.rate = rate;
    }

    /**
     * Returns the time unit for this rate entry, never null.
     *
     * @return the TimeUnit
     */
    public TimeUnit getUnit() {
        return unit == null ? TimeUnit.PER_MINUTE : unit;
    }

    /**
     * Sets the time unit for this rate entry.
     *
     * @param unit the new TimeUnit (defaults to PER_MINUTE if null)
     */
    public void setUnit(TimeUnit unit) {
        this.unit = unit == null ? TimeUnit.PER_MINUTE : unit;
    }

    /**
     * Converts the configured rate to items per second, or returns null if rate is null.
     *
     * @return rate in items per second, or null if unset
     */
    public Double getRatePerSecond() {
        if (rate == null) {
            return null;
        }
        return getUnit().toPerSecond(rate);
    }

    /**
     * Compares this RateEntry with another object for field equality.
     *
     * @param o the object to compare with
     * @return true if itemId, rate, and unit all match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RateEntry that)) return false;
        return Objects.equals(itemId, that.itemId)
                && Objects.equals(rate, that.rate)
                && unit == that.unit;
    }

    /**
     * Computes a hash code from itemId, rate, and unit.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(itemId, rate, unit);
    }
}
