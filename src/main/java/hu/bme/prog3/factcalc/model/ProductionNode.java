package hu.bme.prog3.factcalc.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductionNode {
    private UUID recipeId;
    private String recipeName;
    private boolean originalRecipe;
    private UUID machineId;
    private String machineName;
    private double executionsPerSecond;
    private double effectiveTimeSeconds;
    private double exactMachineCount;
    private int ceilMachineCount;
    private List<RateEntry> inputs = new ArrayList<>();
    private List<RateEntry> outputs = new ArrayList<>();
    private List<RateEntry> byproducts = new ArrayList<>();

    /**
     * Creates an empty ProductionNode instance.
     */
    public ProductionNode() {
    }

    /**
     * Returns the UUID of the recipe executed at this step.
     *
     * @return the recipe UUID
     */
    public UUID getRecipeId() {
        return recipeId;
    }

    /**
     * Sets the UUID of the recipe executed at this step.
     *
     * @param recipeId the recipe UUID
     */
    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
    }

    /**
     * Returns the display name of the executed recipe.
     *
     * @return the recipe name
     */
    public String getRecipeName() {
        return recipeName;
    }

    /**
     * Sets the display name of the executed recipe.
     *
     * @param recipeName the recipe name
     */
    public void setRecipeName(String recipeName) {
        this.recipeName = recipeName;
    }

    /**
     * Returns whether the executed recipe is marked as original.
     *
     * @return true if original, false if alternative
     */
    public boolean isOriginalRecipe() {
        return originalRecipe;
    }

    /**
     * Sets whether the executed recipe is original.
     *
     * @param originalRecipe true if original, false if alternative
     */
    public void setOriginalRecipe(boolean originalRecipe) {
        this.originalRecipe = originalRecipe;
    }

    /**
     * Returns the UUID of the machine selected to run this recipe.
     *
     * @return the selected machine UUID
     */
    public UUID getMachineId() {
        return machineId;
    }

    /**
     * Sets the UUID of the machine selected to run this recipe.
     *
     * @param machineId the selected machine UUID
     */
    public void setMachineId(UUID machineId) {
        this.machineId = machineId;
    }

    /**
     * Returns the display name of the selected machine.
     *
     * @return the machine name
     */
    public String getMachineName() {
        return machineName;
    }

    /**
     * Sets the display name of the selected machine.
     *
     * @param machineName the machine name
     */
    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }

    /**
     * Returns the solved recipe execution rate in cycles per second.
     *
     * @return cycles per second
     */
    public double getExecutionsPerSecond() {
        return executionsPerSecond;
    }

    /**
     * Sets the solved recipe execution rate in cycles per second.
     *
     * @param executionsPerSecond cycles per second
     */
    public void setExecutionsPerSecond(double executionsPerSecond) {
        this.executionsPerSecond = executionsPerSecond;
    }

    /**
     * Returns the effective craft duration per cycle on the selected machine in seconds.
     *
     * @return effective craft time in seconds
     */
    public double getEffectiveTimeSeconds() {
        return effectiveTimeSeconds;
    }

    /**
     * Sets the effective craft duration per cycle in seconds.
     *
     * @param effectiveTimeSeconds effective craft time in seconds
     */
    public void setEffectiveTimeSeconds(double effectiveTimeSeconds) {
        this.effectiveTimeSeconds = effectiveTimeSeconds;
    }

    /**
     * Returns the exact fractional number of machines required for this step.
     *
     * @return exact machine count
     */
    public double getExactMachineCount() {
        return exactMachineCount;
    }

    /**
     * Sets the exact fractional number of machines required for this step.
     *
     * @param exactMachineCount exact machine count
     */
    public void setExactMachineCount(double exactMachineCount) {
        this.exactMachineCount = exactMachineCount;
    }

    /**
     * Returns the rounded-up integer number of machines required for this step.
     *
     * @return ceiling machine count
     */
    public int getCeilMachineCount() {
        return ceilMachineCount;
    }

    /**
     * Sets the rounded-up integer number of machines required for this step.
     *
     * @param ceilMachineCount ceiling machine count
     */
    public void setCeilMachineCount(int ceilMachineCount) {
        this.ceilMachineCount = ceilMachineCount;
    }

    /**
     * Returns the per-second input item consumption rates for this step.
     *
     * @return list of input RateEntry objects
     */
    public List<RateEntry> getInputs() {
        if (inputs == null) inputs = new ArrayList<>();
        return inputs;
    }

    /**
     * Replaces the per-second input item consumption rates for this step.
     *
     * @param inputs list of input RateEntry objects
     */
    public void setInputs(List<RateEntry> inputs) {
        this.inputs = inputs == null ? new ArrayList<>() : new ArrayList<>(inputs);
    }

    /**
     * Returns the per-second main output production rates for this step.
     *
     * @return list of output RateEntry objects
     */
    public List<RateEntry> getOutputs() {
        if (outputs == null) outputs = new ArrayList<>();
        return outputs;
    }

    /**
     * Replaces the per-second main output production rates for this step.
     *
     * @param outputs list of output RateEntry objects
     */
    public void setOutputs(List<RateEntry> outputs) {
        this.outputs = outputs == null ? new ArrayList<>() : new ArrayList<>(outputs);
    }

    /**
     * Returns the per-second byproduct generation rates for this step.
     *
     * @return list of byproduct RateEntry objects
     */
    public List<RateEntry> getByproducts() {
        if (byproducts == null) byproducts = new ArrayList<>();
        return byproducts;
    }

    /**
     * Replaces the per-second byproduct generation rates for this step.
     *
     * @param byproducts list of byproduct RateEntry objects
     */
    public void setByproducts(List<RateEntry> byproducts) {
        this.byproducts = byproducts == null ? new ArrayList<>() : new ArrayList<>(byproducts);
    }

    /**
     * Rounds an exact fractional machine count up to the nearest integer with floating-point tolerance.
     *
     * @param exactCount the exact fractional machine count
     * @return the rounded-up integer machine count (0 if exactCount &le; 1e-9)
     */
    public static int computeCeilMachines(double exactCount) {
        if (exactCount <= 1e-9) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(exactCount - 1e-9));
    }
}
