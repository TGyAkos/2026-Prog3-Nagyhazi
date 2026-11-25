package hu.bme.prog3.factcalc.model;

import java.time.Instant;

public class SavedPlan {
    private String planName;
    private String catalogName;
    private Instant savedAt;
    private ProductionPlanConfig config;
    private ProductionPlanResult result;

    /**
     * Creates an empty SavedPlan instance for deserialization.
     */
    public SavedPlan() {
    }

    /**
     * Creates a persistable SavedPlan snapshot containing metadata, configuration, and solved result.
     *
     * @param planName    user-assigned name of the plan
     * @param catalogName name of the catalog used when saving
     * @param savedAt     UTC timestamp when the plan was saved
     * @param config      planner input configuration
     * @param result      solved production line result snapshot
     */
    public SavedPlan(String planName, String catalogName, Instant savedAt, ProductionPlanConfig config, ProductionPlanResult result) {
        this.planName = planName;
        this.catalogName = catalogName;
        this.savedAt = savedAt;
        this.config = config;
        this.result = result;
    }

    /**
     * Returns the user-assigned name of this saved plan.
     *
     * @return the plan name
     */
    public String getPlanName() {
        return planName;
    }

    /**
     * Sets the user-assigned name of this saved plan.
     *
     * @param planName the plan name
     */
    public void setPlanName(String planName) {
        this.planName = planName;
    }

    /**
     * Returns the name of the catalog associated with this plan.
     *
     * @return the catalog name
     */
    public String getCatalogName() {
        return catalogName;
    }

    /**
     * Sets the name of the catalog associated with this plan.
     *
     * @param catalogName the catalog name
     */
    public void setCatalogName(String catalogName) {
        this.catalogName = catalogName;
    }

    /**
     * Returns the timestamp when the plan was saved.
     *
     * @return the save Instant
     */
    public Instant getSavedAt() {
        return savedAt;
    }

    /**
     * Sets the timestamp when the plan was saved.
     *
     * @param savedAt the save Instant
     */
    public void setSavedAt(Instant savedAt) {
        this.savedAt = savedAt;
    }

    /**
     * Returns the saved planner configuration.
     *
     * @return the ProductionPlanConfig
     */
    public ProductionPlanConfig getConfig() {
        return config;
    }

    /**
     * Sets the saved planner configuration.
     *
     * @param config the ProductionPlanConfig
     */
    public void setConfig(ProductionPlanConfig config) {
        this.config = config;
    }

    /**
     * Returns the saved solved production plan result snapshot.
     *
     * @return the ProductionPlanResult
     */
    public ProductionPlanResult getResult() {
        return result;
    }

    /**
     * Sets the saved solved production plan result snapshot.
     *
     * @param result the ProductionPlanResult
     */
    public void setResult(ProductionPlanResult result) {
        this.result = result;
    }
}
