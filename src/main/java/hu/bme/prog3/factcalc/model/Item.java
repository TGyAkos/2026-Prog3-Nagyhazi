package hu.bme.prog3.factcalc.model;

import java.util.Objects;
import java.util.UUID;

public class Item {
    private UUID id;
    private String name;
    private String img;

    /**
     * Creates an empty Item instance for deserialization.
     */
    public Item() {
    }

    /**
     * Creates an Item with the specified identifier, display name, and image path.
     *
     * @param id   unique identifier of the item
     * @param name display name of the item
     * @param img  optional icon path or URL (defaults to empty string if null)
     */
    public Item(UUID id, String name, String img) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.img = img == null ? "" : img;
    }

    /**
     * Creates a shallow copy of an existing Item.
     *
     * @param other the item to copy
     */
    public Item(Item other) {
        this(other.id, other.name, other.img);
    }

    /**
     * Returns the unique identifier of this item.
     *
     * @return the item UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the unique identifier of this item.
     *
     * @param id the new item UUID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the display name of this item.
     *
     * @return the item name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name of this item.
     *
     * @param name the new item name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the icon path or URL for this item, never null.
     *
     * @return the icon path or URL, or an empty string if unset
     */
    public String getImg() {
        return img == null ? "" : img;
    }

    /**
     * Sets the icon path or URL for this item.
     *
     * @param img the icon path or URL (treated as empty string if null)
     */
    public void setImg(String img) {
        this.img = img == null ? "" : img;
    }

    /**
     * Compares this item with another object based on item UUID equality.
     *
     * @param o the object to compare with
     * @return true if the other object is an Item with the same ID
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item item)) return false;
        return Objects.equals(id, item.id);
    }

    /**
     * Computes the hash code from the item's UUID.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Returns the item's display name or its UUID string if unnamed.
     *
     * @return string representation of the item
     */
    @Override
    public String toString() {
        return name != null ? name : String.valueOf(id);
    }
}
