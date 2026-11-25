package hu.bme.prog3.factcalc.model;

import java.util.Objects;
import java.util.UUID;

public class ItemAmount {
    private UUID id;
    private double piece;

    /**
     * Creates an empty ItemAmount instance for deserialization.
     */
    public ItemAmount() {
    }

    /**
     * Creates an ItemAmount associating an item ID with a per-cycle quantity.
     *
     * @param id    the item UUID
     * @param piece quantity of the item per recipe cycle
     */
    public ItemAmount(UUID id, double piece) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.piece = piece;
    }

    /**
     * Creates a copy of an existing ItemAmount.
     *
     * @param other the ItemAmount to copy
     */
    public ItemAmount(ItemAmount other) {
        this(other.id, other.piece);
    }

    /**
     * Returns the referenced item UUID.
     *
     * @return the item ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the referenced item UUID.
     *
     * @param id the new item ID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the quantity of the item per recipe execution cycle.
     *
     * @return the item quantity
     */
    public double getPiece() {
        return piece;
    }

    /**
     * Sets the quantity of the item per recipe execution cycle.
     *
     * @param piece the new item quantity
     */
    public void setPiece(double piece) {
        this.piece = piece;
    }

    /**
     * Checks value equality based on item ID and quantity.
     *
     * @param o the object to compare with
     * @return true if both ID and piece match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemAmount that)) return false;
        return Double.compare(piece, that.piece) == 0 && Objects.equals(id, that.id);
    }

    /**
     * Computes a hash code from item ID and quantity.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, piece);
    }
}
