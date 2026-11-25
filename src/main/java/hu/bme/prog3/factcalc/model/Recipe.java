package hu.bme.prog3.factcalc.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;

public class Recipe {
    private UUID id;
    private String name;
    private double time;
    private String img;

    @SerializedName("is_original")
    private boolean isOriginal;

    private List<RecipeMachineSpec> machines = new ArrayList<>();
    private List<ItemAmount> inputs = new ArrayList<>();
    private List<ItemAmount> outputs = new ArrayList<>();
    private List<ItemAmount> byproducts = new ArrayList<>();

    /**
     * Creates an empty Recipe instance for deserialization.
     */
    public Recipe() {
    }

    /**
     * Creates a Recipe with its full configuration of machines, inputs, outputs, and byproducts.
     *
     * @param id         unique identifier of the recipe
     * @param name       display name of the recipe
     * @param time       base crafting duration in seconds
     * @param img        optional icon path or URL
     * @param isOriginal true if the user marks this recipe as original; false if alternative
     * @param machines   list of compatible machine or machine-type specifications
     * @param inputs     list of consumed input item amounts per cycle
     * @param outputs    list of produced main output item amounts per cycle
     * @param byproducts optional list of produced byproduct item amounts per cycle
     */
    public Recipe(
            UUID id,
            String name,
            double time,
            String img,
            boolean isOriginal,
            List<RecipeMachineSpec> machines,
            List<ItemAmount> inputs,
            List<ItemAmount> outputs,
            List<ItemAmount> byproducts
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.time = time;
        this.img = img == null ? "" : img;
        this.isOriginal = isOriginal;
        this.machines = machines == null ? new ArrayList<>() : new ArrayList<>(machines);
        this.inputs = inputs == null ? new ArrayList<>() : new ArrayList<>(inputs);
        this.outputs = outputs == null ? new ArrayList<>() : new ArrayList<>(outputs);
        this.byproducts = byproducts == null ? new ArrayList<>() : new ArrayList<>(byproducts);
    }

    /**
     * Creates a deep copy of an existing Recipe.
     *
     * @param other the recipe to copy
     */
    public Recipe(Recipe other) {
        this.id = other.id;
        this.name = other.name;
        this.time = other.time;
        this.img = other.img == null ? "" : other.img;
        this.isOriginal = other.isOriginal;
        this.machines = new ArrayList<>();
        for (RecipeMachineSpec spec : other.getMachines()) {
            this.machines.add(new RecipeMachineSpec(spec));
        }
        this.inputs = new ArrayList<>();
        for (ItemAmount ia : other.getInputs()) {
            this.inputs.add(new ItemAmount(ia));
        }
        this.outputs = new ArrayList<>();
        for (ItemAmount ia : other.getOutputs()) {
            this.outputs.add(new ItemAmount(ia));
        }
        this.byproducts = new ArrayList<>();
        for (ItemAmount ia : other.getByproducts()) {
            this.byproducts.add(new ItemAmount(ia));
        }
    }

    /**
     * Returns the unique identifier of this recipe.
     *
     * @return the recipe UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the unique identifier of this recipe.
     *
     * @param id the new recipe UUID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the display name of this recipe.
     *
     * @return the recipe name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name of this recipe.
     *
     * @param name the new recipe name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the base crafting duration in seconds.
     *
     * @return crafting time in seconds
     */
    public double getTime() {
        return time;
    }

    /**
     * Sets the base crafting duration in seconds.
     *
     * @param time crafting time in seconds
     */
    public void setTime(double time) {
        this.time = time;
    }

    /**
     * Returns the icon path or URL for this recipe, never null.
     *
     * @return the icon path or URL, or empty string if unset
     */
    public String getImg() {
        return img == null ? "" : img;
    }

    /**
     * Sets the icon path or URL for this recipe.
     *
     * @param img the icon path or URL
     */
    public void setImg(String img) {
        this.img = img == null ? "" : img;
    }

    /**
     * Returns whether this recipe is marked as an original recipe rather than an alternative.
     *
     * @return true if original, false if alternative
     */
    public boolean isOriginal() {
        return isOriginal;
    }

    /**
     * Sets whether this recipe is an original recipe.
     *
     * @param original true if original, false if alternative
     */
    public void setOriginal(boolean original) {
        isOriginal = original;
    }

    /**
     * Returns the mutable list of compatible machine/type specifications.
     *
     * @return list of RecipeMachineSpec entries
     */
    public List<RecipeMachineSpec> getMachines() {
        if (machines == null) machines = new ArrayList<>();
        return machines;
    }

    /**
     * Replaces the compatible machine/type specifications list.
     *
     * @param machines the new list of machine specifications
     */
    public void setMachines(List<RecipeMachineSpec> machines) {
        this.machines = machines == null ? new ArrayList<>() : new ArrayList<>(machines);
    }

    /**
     * Returns the list of required input items per cycle.
     *
     * @return list of input ItemAmount entries
     */
    public List<ItemAmount> getInputs() {
        if (inputs == null) inputs = new ArrayList<>();
        return inputs;
    }

    /**
     * Replaces the list of required input items per cycle.
     *
     * @param inputs the new list of input ItemAmount entries
     */
    public void setInputs(List<ItemAmount> inputs) {
        this.inputs = inputs == null ? new ArrayList<>() : new ArrayList<>(inputs);
    }

    /**
     * Returns the list of primary output items produced per cycle.
     *
     * @return list of output ItemAmount entries
     */
    public List<ItemAmount> getOutputs() {
        if (outputs == null) outputs = new ArrayList<>();
        return outputs;
    }

    /**
     * Replaces the list of primary output items produced per cycle.
     *
     * @param outputs the new list of output ItemAmount entries
     */
    public void setOutputs(List<ItemAmount> outputs) {
        this.outputs = outputs == null ? new ArrayList<>() : new ArrayList<>(outputs);
    }

    /**
     * Returns the list of byproduct items generated per cycle.
     *
     * @return list of byproduct ItemAmount entries
     */
    public List<ItemAmount> getByproducts() {
        if (byproducts == null) byproducts = new ArrayList<>();
        return byproducts;
    }

    /**
     * Replaces the list of byproduct items generated per cycle.
     *
     * @param byproducts the new list of byproduct ItemAmount entries
     */
    public void setByproducts(List<ItemAmount> byproducts) {
        this.byproducts = byproducts == null ? new ArrayList<>() : new ArrayList<>(byproducts);
    }

    /**
     * Checks whether the given machine can execute this recipe.
     *
     * @param machine the candidate machine
     * @return true if the machine matches either a specific machine ID or machine type spec
     */
    public boolean isCompatibleWith(Machine machine) {
        return findRecipeMultiplierFor(machine).isPresent();
    }

    /**
     * Resolves the recipe-specific speed multiplier for the given machine.
     * Specific machine ID entries always override generic machine type entries.
     *
     * @param machine the machine to resolve the multiplier for
     * @return an OptionalDouble containing the recipe multiplier if compatible, or empty otherwise
     */
    public OptionalDouble findRecipeMultiplierFor(Machine machine) {
        if (machine == null || machines == null) {
            return OptionalDouble.empty();
        }
        for (RecipeMachineSpec spec : machines) {
            if (spec.getId() != null && spec.getId().equals(machine.getId())) {
                return OptionalDouble.of(spec.getMultiplier());
            }
        }
        if (machine.getType() != null) {
            for (RecipeMachineSpec spec : machines) {
                if (spec.getId() == null && machine.getType().equals(spec.getType())) {
                    return OptionalDouble.of(spec.getMultiplier());
                }
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Computes the effective craft duration in seconds using
     * {@code time / (recipeMachineMultiplier * machine.multiplier)}.
     *
     * @param machine the compatible machine executing the recipe
     * @return effective execution time in seconds
     * @throws IllegalArgumentException if the machine is not compatible with this recipe
     * @throws IllegalStateException    if either multiplier is non-positive
     */
    public double getEffectiveTime(Machine machine) {
        OptionalDouble recipeMult = findRecipeMultiplierFor(machine);
        if (recipeMult.isEmpty()) {
            throw new IllegalArgumentException(
                    "Machine '" + (machine != null ? machine.getName() : "null") + "' is not compatible with recipe '" + name + "'");
        }
        double rMult = recipeMult.getAsDouble();
        double mMult = machine.getMultiplier();
        if (rMult <= 0.0 || mMult <= 0.0) {
            throw new IllegalStateException("Multipliers must be positive");
        }
        return time / (rMult * mMult);
    }

    /**
     * Returns the total input quantity of a given item consumed per recipe cycle.
     *
     * @param itemId the item UUID to look up
     * @return consumed piece count per cycle (0.0 if absent)
     */
    public double getInputPiece(UUID itemId) {
        return getInputs().stream()
                .filter(ia -> Objects.equals(ia.getId(), itemId))
                .mapToDouble(ItemAmount::getPiece)
                .sum();
    }

    /**
     * Returns the total main output quantity of a given item produced per recipe cycle.
     *
     * @param itemId the item UUID to look up
     * @return produced main output piece count per cycle (0.0 if absent)
     */
    public double getOutputPiece(UUID itemId) {
        return getOutputs().stream()
                .filter(ia -> Objects.equals(ia.getId(), itemId))
                .mapToDouble(ItemAmount::getPiece)
                .sum();
    }

    /**
     * Returns the total byproduct quantity of a given item generated per recipe cycle.
     *
     * @param itemId the item UUID to look up
     * @return produced byproduct piece count per cycle (0.0 if absent)
     */
    public double getByproductPiece(UUID itemId) {
        return getByproducts().stream()
                .filter(ia -> Objects.equals(ia.getId(), itemId))
                .mapToDouble(ItemAmount::getPiece)
                .sum();
    }

    /**
     * Checks whether this recipe produces the given item as a main output.
     *
     * @param itemId the item UUID to check
     * @return true if the item appears in outputs
     */
    public boolean producesOutput(UUID itemId) {
        return getOutputs().stream().anyMatch(ia -> Objects.equals(ia.getId(), itemId));
    }

    /**
     * Checks whether this recipe consumes the given item as an input.
     *
     * @param itemId the item UUID to check
     * @return true if the item appears in inputs
     */
    public boolean consumesInput(UUID itemId) {
        return getInputs().stream().anyMatch(ia -> Objects.equals(ia.getId(), itemId));
    }

    /**
     * Checks whether this recipe generates the given item as a byproduct.
     *
     * @param itemId the item UUID to check
     * @return true if the item appears in byproducts
     */
    public boolean producesByproduct(UUID itemId) {
        return getByproducts().stream().anyMatch(ia -> Objects.equals(ia.getId(), itemId));
    }

    /**
     * Compares this recipe with another object by recipe UUID.
     *
     * @param o the object to compare with
     * @return true if the other object is a Recipe with the same ID
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Recipe recipe)) return false;
        return Objects.equals(id, recipe.id);
    }

    /**
     * Computes the hash code from the recipe's UUID.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Returns the recipe's display name or its UUID string if unnamed.
     *
     * @return string representation of the recipe
     */
    @Override
    public String toString() {
        return name != null ? name : String.valueOf(id);
    }
}
