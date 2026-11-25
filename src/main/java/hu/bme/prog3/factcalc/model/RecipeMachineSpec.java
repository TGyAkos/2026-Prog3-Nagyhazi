package hu.bme.prog3.factcalc.model;

import java.util.Objects;
import java.util.UUID;

public class RecipeMachineSpec {
    private UUID id;
    private String type;
    private double multiplier;

    /**
     * Creates an empty RecipeMachineSpec instance for deserialization.
     */
    public RecipeMachineSpec() {
    }

    /**
     * Creates a specification targeting a specific machine by its UUID.
     *
     * @param machineId  the specific machine UUID
     * @param multiplier recipe-specific speed multiplier for this machine
     * @return a new RecipeMachineSpec referencing the machine ID
     */
    public static RecipeMachineSpec byId(UUID machineId, double multiplier) {
        RecipeMachineSpec spec = new RecipeMachineSpec();
        spec.id = Objects.requireNonNull(machineId, "machineId must not be null");
        spec.multiplier = multiplier;
        return spec;
    }

    /**
     * Creates a specification targeting any machine belonging to the given machine type.
     *
     * @param machineType the generic machine type name
     * @param multiplier  recipe-specific speed multiplier for this machine type
     * @return a new RecipeMachineSpec referencing the machine type
     */
    public static RecipeMachineSpec byType(String machineType, double multiplier) {
        RecipeMachineSpec spec = new RecipeMachineSpec();
        spec.type = Objects.requireNonNull(machineType, "machineType must not be null");
        spec.multiplier = multiplier;
        return spec;
    }

    /**
     * Creates a RecipeMachineSpec with explicit fields.
     *
     * @param id         optional specific machine UUID
     * @param type       optional generic machine type
     * @param multiplier recipe-specific speed multiplier
     */
    public RecipeMachineSpec(UUID id, String type, double multiplier) {
        this.id = id;
        this.type = type;
        this.multiplier = multiplier;
    }

    /**
     * Creates a copy of an existing RecipeMachineSpec.
     *
     * @param other the specification to copy
     */
    public RecipeMachineSpec(RecipeMachineSpec other) {
        this(other.id, other.type, other.multiplier);
    }

    /**
     * Returns the specific machine UUID, or null if this spec targets a machine type.
     *
     * @return the specific machine ID or null
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the specific machine UUID.
     *
     * @param id the machine ID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the generic machine type, or null if this spec targets a specific machine ID.
     *
     * @return the machine type or null
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the generic machine type.
     *
     * @param type the machine type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the recipe-specific speed multiplier.
     *
     * @return the multiplier
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Sets the recipe-specific speed multiplier.
     *
     * @param multiplier the new multiplier
     */
    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    /**
     * Checks whether this specification references a specific machine ID.
     *
     * @return true if a specific machine ID is set
     */
    public boolean isSpecificMachine() {
        return id != null;
    }

    /**
     * Checks whether this specification references a generic machine type.
     *
     * @return true if ID is null and a non-blank type is set
     */
    public boolean isMachineType() {
        return id == null && type != null && !type.isBlank();
    }

    /**
     * Checks equality against another object by ID, type, and multiplier.
     *
     * @param o the object to compare with
     * @return true if all fields are equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeMachineSpec that)) return false;
        return Double.compare(multiplier, that.multiplier) == 0
                && Objects.equals(id, that.id)
                && Objects.equals(type, that.type);
    }

    /**
     * Computes a hash code from ID, type, and multiplier.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, type, multiplier);
    }
}
