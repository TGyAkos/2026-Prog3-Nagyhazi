package hu.bme.prog3.factcalc.model;

import java.util.Objects;
import java.util.UUID;

public class Machine extends Item {
    private String type;
    private double multiplier;

    /**
     * Creates an empty Machine instance for deserialization.
     */
    public Machine() {
        super();
    }

    /**
     * Creates a Machine with its identifier, name, category type, icon, and speed multiplier.
     *
     * @param id         unique identifier of the machine
     * @param name       display name of the machine
     * @param type       machine category or type (e.g., "assembler", "smelter")
     * @param img        optional icon path or URL
     * @param multiplier base crafting speed multiplier of the machine
     */
    public Machine(UUID id, String name, String type, String img, double multiplier) {
        super(id, name, img);
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.multiplier = multiplier;
    }

    /**
     * Creates a copy of an existing Machine.
     *
     * @param other the machine to copy
     */
    public Machine(Machine other) {
        this(other.getId(), other.getName(), other.type, other.getImg(), other.multiplier);
    }

    /**
     * Returns the machine category type.
     *
     * @return the machine type string
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the machine category type.
     *
     * @param type the new machine type string
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the machine's general crafting speed multiplier.
     *
     * @return the speed multiplier
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Sets the machine's general crafting speed multiplier.
     *
     * @param multiplier the new speed multiplier
     */
    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }
}
