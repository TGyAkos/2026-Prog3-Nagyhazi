package hu.bme.prog3.factcalc.model;

import java.util.UUID;

public class FlowEdge {
    public enum EdgeType {
        EXTERNAL_INPUT("External Input"),
        INTERMEDIATE("Intermediate"),
        FINAL_OUTPUT("Final Output"),
        BYPRODUCT_OUTPUT("Byproduct Output"),
        RECURSIVE_FEEDBACK("Recursive Feedback");

        private final String label;

        /**
         * Constructs an EdgeType enum constant with a display label.
         *
         * @param label human-readable edge category label
         */
        EdgeType(String label) {
            this.label = label;
        }

        /**
         * Returns the display label of this edge type.
         *
         * @return the edge type label
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns the display label of this edge type.
         *
         * @return the edge type label
         */
        @Override
        public String toString() {
            return label;
        }
    }

    private UUID sourceRecipeId;
    private String sourceLabel;
    private UUID targetRecipeId;
    private String targetLabel;
    private UUID itemId;
    private String itemName;
    private double ratePerSecond;
    private EdgeType type;

    /**
     * Creates an empty FlowEdge instance for deserialization.
     */
    public FlowEdge() {
    }

    /**
     * Creates a directed FlowEdge representing item movement between two nodes or boundaries.
     *
     * @param sourceRecipeId source recipe UUID, or null if originating from external input
     * @param sourceLabel    display label of the flow source
     * @param targetRecipeId target recipe UUID, or null if exiting to final/byproduct output
     * @param targetLabel    display label of the flow destination
     * @param itemId         UUID of the transferred item
     * @param itemName       display name of the transferred item
     * @param ratePerSecond  flow rate in items per second
     * @param type           classification of the edge
     */
    public FlowEdge(
            UUID sourceRecipeId,
            String sourceLabel,
            UUID targetRecipeId,
            String targetLabel,
            UUID itemId,
            String itemName,
            double ratePerSecond,
            EdgeType type
    ) {
        this.sourceRecipeId = sourceRecipeId;
        this.sourceLabel = sourceLabel;
        this.targetRecipeId = targetRecipeId;
        this.targetLabel = targetLabel;
        this.itemId = itemId;
        this.itemName = itemName;
        this.ratePerSecond = ratePerSecond;
        this.type = type;
    }

    /**
     * Returns the source recipe UUID, or null for external inputs.
     *
     * @return the source recipe UUID or null
     */
    public UUID getSourceRecipeId() {
        return sourceRecipeId;
    }

    /**
     * Sets the source recipe UUID.
     *
     * @param sourceRecipeId the source recipe UUID or null
     */
    public void setSourceRecipeId(UUID sourceRecipeId) {
        this.sourceRecipeId = sourceRecipeId;
    }

    /**
     * Returns the display label of the flow source.
     *
     * @return the source label
     */
    public String getSourceLabel() {
        return sourceLabel;
    }

    /**
     * Sets the display label of the flow source.
     *
     * @param sourceLabel the source label
     */
    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    /**
     * Returns the target recipe UUID, or null for terminal outputs.
     *
     * @return the target recipe UUID or null
     */
    public UUID getTargetRecipeId() {
        return targetRecipeId;
    }

    /**
     * Sets the target recipe UUID.
     *
     * @param targetRecipeId the target recipe UUID or null
     */
    public void setTargetRecipeId(UUID targetRecipeId) {
        this.targetRecipeId = targetRecipeId;
    }

    /**
     * Returns the display label of the flow target.
     *
     * @return the target label
     */
    public String getTargetLabel() {
        return targetLabel;
    }

    /**
     * Sets the display label of the flow target.
     *
     * @param targetLabel the target label
     */
    public void setTargetLabel(String targetLabel) {
        this.targetLabel = targetLabel;
    }

    /**
     * Returns the UUID of the item flowing along this edge.
     *
     * @return the item UUID
     */
    public UUID getItemId() {
        return itemId;
    }

    /**
     * Sets the UUID of the item flowing along this edge.
     *
     * @param itemId the item UUID
     */
    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    /**
     * Returns the display name of the item flowing along this edge.
     *
     * @return the item name
     */
    public String getItemName() {
        return itemName;
    }

    /**
     * Sets the display name of the item flowing along this edge.
     *
     * @param itemName the item name
     */
    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    /**
     * Returns the flow rate along this edge in items per second.
     *
     * @return flow rate in items per second
     */
    public double getRatePerSecond() {
        return ratePerSecond;
    }

    /**
     * Sets the flow rate along this edge in items per second.
     *
     * @param ratePerSecond flow rate in items per second
     */
    public void setRatePerSecond(double ratePerSecond) {
        this.ratePerSecond = ratePerSecond;
    }

    /**
     * Returns the classification type of this flow edge.
     *
     * @return the EdgeType
     */
    public EdgeType getType() {
        return type;
    }

    /**
     * Sets the classification type of this flow edge.
     *
     * @param type the EdgeType
     */
    public void setType(EdgeType type) {
        this.type = type;
    }

    /**
     * Checks whether this edge represents a recursive byproduct feedback loop.
     *
     * @return true if the edge type is RECURSIVE_FEEDBACK
     */
    public boolean isRecursive() {
        return type == EdgeType.RECURSIVE_FEEDBACK;
    }
}
