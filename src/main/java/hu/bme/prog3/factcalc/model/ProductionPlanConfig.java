package hu.bme.prog3.factcalc.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProductionPlanConfig {
    private CalculationMode mode = CalculationMode.TARGET_OUTPUT;
    private TimeUnit displayUnit = TimeUnit.PER_MINUTE;
    private boolean autoMode = false;
    private List<RateEntry> inputs = new ArrayList<>();
    private List<RateEntry> targets = new ArrayList<>();
    private Map<UUID, UUID> recipeOverrides = new LinkedHashMap<>();
    private Map<UUID, UUID> machineSelections = new LinkedHashMap<>();
    private Map<UUID, Boolean> recycledByproducts = new LinkedHashMap<>();

    /**
     * Creates a default ProductionPlanConfig in TARGET_OUTPUT mode using PER_MINUTE display units.
     */
    public ProductionPlanConfig() {
    }

    /**
     * Creates a deep copy of an existing ProductionPlanConfig.
     *
     * @param other the configuration to copy
     */
    public ProductionPlanConfig(ProductionPlanConfig other) {
        this.mode = other.mode;
        this.displayUnit = other.displayUnit;
        this.autoMode = other.autoMode;
        for (RateEntry e : other.getInputs()) {
            this.inputs.add(new RateEntry(e));
        }
        for (RateEntry e : other.getTargets()) {
            this.targets.add(new RateEntry(e));
        }
        this.recipeOverrides.putAll(other.getRecipeOverrides());
        this.machineSelections.putAll(other.getMachineSelections());
        this.recycledByproducts.putAll(other.getRecycledByproducts());
    }

    /**
     * Returns the configured calculation mode, defaulting to TARGET_OUTPUT.
     *
     * @return the CalculationMode
     */
    public CalculationMode getMode() {
        return mode == null ? CalculationMode.TARGET_OUTPUT : mode;
    }

    /**
     * Sets the calculation mode.
     *
     * @param mode the CalculationMode (defaults to TARGET_OUTPUT if null)
     */
    public void setMode(CalculationMode mode) {
        this.mode = mode == null ? CalculationMode.TARGET_OUTPUT : mode;
    }

    /**
     * Returns the UI display time unit, defaulting to PER_MINUTE.
     *
     * @return the display TimeUnit
     */
    public TimeUnit getDisplayUnit() {
        return displayUnit == null ? TimeUnit.PER_MINUTE : displayUnit;
    }

    /**
     * Sets the UI display time unit.
     *
     * @param displayUnit the display TimeUnit (defaults to PER_MINUTE if null)
     */
    public void setDisplayUnit(TimeUnit displayUnit) {
        this.displayUnit = displayUnit == null ? TimeUnit.PER_MINUTE : displayUnit;
    }

    /**
     * Returns whether automatic alternative-recipe optimization is enabled.
     *
     * @return true if auto mode is enabled
     */
    public boolean isAutoMode() {
        return autoMode;
    }

    /**
     * Enables or disables automatic alternative-recipe optimization.
     *
     * @param autoMode true to enable auto mode
     */
    public void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    /**
     * Returns the list of user-supplied external input entries.
     *
     * @return mutable list of input RateEntry objects
     */
    public List<RateEntry> getInputs() {
        if (inputs == null) inputs = new ArrayList<>();
        return inputs;
    }

    /**
     * Replaces the list of user-supplied external input entries.
     *
     * @param inputs the new list of input entries
     */
    public void setInputs(List<RateEntry> inputs) {
        this.inputs = inputs == null ? new ArrayList<>() : new ArrayList<>(inputs);
    }

    /**
     * Returns the list of target output entries.
     *
     * @return mutable list of target RateEntry objects
     */
    public List<RateEntry> getTargets() {
        if (targets == null) targets = new ArrayList<>();
        return targets;
    }

    /**
     * Replaces the list of target output entries.
     *
     * @param targets the new list of target entries
     */
    public void setTargets(List<RateEntry> targets) {
        this.targets = targets == null ? new ArrayList<>() : new ArrayList<>(targets);
    }

    /**
     * Returns the map of item UUID to explicitly overridden recipe UUID.
     *
     * @return mutable map of recipe overrides
     */
    public Map<UUID, UUID> getRecipeOverrides() {
        if (recipeOverrides == null) recipeOverrides = new LinkedHashMap<>();
        return recipeOverrides;
    }

    /**
     * Replaces the item-to-recipe override map.
     *
     * @param recipeOverrides map of item UUID to recipe UUID
     */
    public void setRecipeOverrides(Map<UUID, UUID> recipeOverrides) {
        this.recipeOverrides = recipeOverrides == null ? new LinkedHashMap<>() : new LinkedHashMap<>(recipeOverrides);
    }

    /**
     * Returns the map of recipe UUID to user-selected machine UUID.
     *
     * @return mutable map of machine selections
     */
    public Map<UUID, UUID> getMachineSelections() {
        if (machineSelections == null) machineSelections = new LinkedHashMap<>();
        return machineSelections;
    }

    /**
     * Replaces the recipe-to-machine selection map.
     *
     * @param machineSelections map of recipe UUID to machine UUID
     */
    public void setMachineSelections(Map<UUID, UUID> machineSelections) {
        this.machineSelections = machineSelections == null ? new LinkedHashMap<>() : new LinkedHashMap<>(machineSelections);
    }

    /**
     * Returns the map indicating whether each recyclable byproduct item is recursively reused.
     *
     * @return mutable map of item UUID to recycling flag
     */
    public Map<UUID, Boolean> getRecycledByproducts() {
        if (recycledByproducts == null) recycledByproducts = new LinkedHashMap<>();
        return recycledByproducts;
    }

    /**
     * Replaces the recursive byproduct recycling configuration map.
     *
     * @param recycledByproducts map of byproduct item UUID to boolean flag
     */
    public void setRecycledByproducts(Map<UUID, Boolean> recycledByproducts) {
        this.recycledByproducts = recycledByproducts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(recycledByproducts);
    }
}
