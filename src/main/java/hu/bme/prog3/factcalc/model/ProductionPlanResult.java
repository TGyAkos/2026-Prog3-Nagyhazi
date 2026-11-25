package hu.bme.prog3.factcalc.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProductionPlanResult {

    public static class InputSummaryEntry {
        private UUID itemId;
        private String itemName;
        private double requiredRatePerSecond;
        private Double suppliedCapPerSecond;
        private boolean bottleneck;

        /**
         * Creates an empty InputSummaryEntry for deserialization.
         */
        public InputSummaryEntry() {
        }

        /**
         * Creates an InputSummaryEntry summarizing required vs. supplied capacity for a user input item.
         *
         * @param itemId                item UUID
         * @param itemName              display name of the item
         * @param requiredRatePerSecond solved consumption rate in items per second
         * @param suppliedCapPerSecond  user-configured capacity in items per second, or null if unlimited
         * @param bottleneck            true if this input limits maximum output in MAX_OUTPUT mode
         */
        public InputSummaryEntry(UUID itemId, String itemName, double requiredRatePerSecond, Double suppliedCapPerSecond, boolean bottleneck) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.requiredRatePerSecond = requiredRatePerSecond;
            this.suppliedCapPerSecond = suppliedCapPerSecond;
            this.bottleneck = bottleneck;
        }

        /**
         * Returns the input item UUID.
         *
         * @return the item ID
         */
        public UUID getItemId() {
            return itemId;
        }

        /**
         * Sets the input item UUID.
         *
         * @param itemId the item ID
         */
        public void setItemId(UUID itemId) {
            this.itemId = itemId;
        }

        /**
         * Returns the input item display name.
         *
         * @return the item name
         */
        public String getItemName() {
            return itemName;
        }

        /**
         * Sets the input item display name.
         *
         * @param itemName the item name
         */
        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        /**
         * Returns the solved consumption rate in items per second.
         *
         * @return required rate in items per second
         */
        public double getRequiredRatePerSecond() {
            return requiredRatePerSecond;
        }

        /**
         * Sets the solved consumption rate in items per second.
         *
         * @param requiredRatePerSecond required rate in items per second
         */
        public void setRequiredRatePerSecond(double requiredRatePerSecond) {
            this.requiredRatePerSecond = requiredRatePerSecond;
        }

        /**
         * Returns the user-supplied rate cap in items per second, or null if unlimited.
         *
         * @return supplied capacity in items per second, or null
         */
        public Double getSuppliedCapPerSecond() {
            return suppliedCapPerSecond;
        }

        /**
         * Sets the user-supplied rate cap in items per second.
         *
         * @param suppliedCapPerSecond supplied capacity in items per second, or null
         */
        public void setSuppliedCapPerSecond(Double suppliedCapPerSecond) {
            this.suppliedCapPerSecond = suppliedCapPerSecond;
        }

        /**
         * Returns whether this input is a limiting bottleneck in MAX_OUTPUT mode.
         *
         * @return true if bottleneck
         */
        public boolean isBottleneck() {
            return bottleneck;
        }

        /**
         * Marks whether this input is a limiting bottleneck in MAX_OUTPUT mode.
         *
         * @param bottleneck true if bottleneck
         */
        public void setBottleneck(boolean bottleneck) {
            this.bottleneck = bottleneck;
        }
    }

    public static class MachineTotal {
        private UUID machineId;
        private String machineName;
        private double exactCount;
        private int ceilCountSum;

        /**
         * Creates an empty MachineTotal for deserialization.
         */
        public MachineTotal() {
        }

        /**
         * Creates an aggregated machine count summary for a specific machine type/model.
         *
         * @param machineId    machine UUID
         * @param machineName  display name of the machine
         * @param exactCount   sum of exact fractional machine counts across all steps
         * @param ceilCountSum sum of rounded-up machine counts across all steps
         */
        public MachineTotal(UUID machineId, String machineName, double exactCount, int ceilCountSum) {
            this.machineId = machineId;
            this.machineName = machineName;
            this.exactCount = exactCount;
            this.ceilCountSum = ceilCountSum;
        }

        /**
         * Returns the machine UUID.
         *
         * @return the machine ID
         */
        public UUID getMachineId() {
            return machineId;
        }

        /**
         * Sets the machine UUID.
         *
         * @param machineId the machine ID
         */
        public void setMachineId(UUID machineId) {
            this.machineId = machineId;
        }

        /**
         * Returns the display name of the machine.
         *
         * @return the machine name
         */
        public String getMachineName() {
            return machineName;
        }

        /**
         * Sets the display name of the machine.
         *
         * @param machineName the machine name
         */
        public void setMachineName(String machineName) {
            this.machineName = machineName;
        }

        /**
         * Returns the total exact fractional machine count across all active steps.
         *
         * @return total exact count
         */
        public double getExactCount() {
            return exactCount;
        }

        /**
         * Sets the total exact fractional machine count across all active steps.
         *
         * @param exactCount total exact count
         */
        public void setExactCount(double exactCount) {
            this.exactCount = exactCount;
        }

        /**
         * Returns the sum of rounded-up integer machine counts across all active steps.
         *
         * @return summed ceiling count
         */
        public int getCeilCountSum() {
            return ceilCountSum;
        }

        /**
         * Sets the sum of rounded-up integer machine counts across all active steps.
         *
         * @param ceilCountSum summed ceiling count
         */
        public void setCeilCountSum(int ceilCountSum) {
            this.ceilCountSum = ceilCountSum;
        }
    }

    private CalculationMode mode;
    private List<ProductionNode> nodes = new ArrayList<>();
    private List<FlowEdge> edges = new ArrayList<>();
    private List<InputSummaryEntry> userInputs = new ArrayList<>();
    private List<RateEntry> calculatedMissingInputs = new ArrayList<>();
    private List<RateEntry> finalOutputs = new ArrayList<>();
    private List<RateEntry> byproductOutputs = new ArrayList<>();
    private List<MachineTotal> machineTotals = new ArrayList<>();
    private Set<UUID> recyclableByproductIds = new LinkedHashSet<>();
    private List<SolverNotice> notices = new ArrayList<>();
    private Map<UUID, UUID> resolvedRecipes = new LinkedHashMap<>();
    private Map<UUID, UUID> resolvedMachines = new LinkedHashMap<>();
    private Map<UUID, Boolean> resolvedRecycling = new LinkedHashMap<>();

    /**
     * Creates an empty ProductionPlanResult instance.
     */
    public ProductionPlanResult() {
    }

    /**
     * Returns the calculation mode used to produce this result.
     *
     * @return the CalculationMode
     */
    public CalculationMode getMode() {
        return mode;
    }

    /**
     * Sets the calculation mode used to produce this result.
     *
     * @param mode the CalculationMode
     */
    public void setMode(CalculationMode mode) {
        this.mode = mode;
    }

    /**
     * Returns the list of solved recipe production nodes.
     *
     * @return list of ProductionNode instances
     */
    public List<ProductionNode> getNodes() {
        if (nodes == null) nodes = new ArrayList<>();
        return nodes;
    }

    /**
     * Replaces the list of solved recipe production nodes.
     *
     * @param nodes list of ProductionNode instances
     */
    public void setNodes(List<ProductionNode> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
    }

    /**
     * Returns the directed item flow edges connecting nodes and boundaries.
     *
     * @return list of FlowEdge instances
     */
    public List<FlowEdge> getEdges() {
        if (edges == null) edges = new ArrayList<>();
        return edges;
    }

    /**
     * Replaces the directed item flow edges list.
     *
     * @param edges list of FlowEdge instances
     */
    public void setEdges(List<FlowEdge> edges) {
        this.edges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
    }

    /**
     * Returns summary entries for all user-supplied input items.
     *
     * @return list of InputSummaryEntry objects
     */
    public List<InputSummaryEntry> getUserInputs() {
        if (userInputs == null) userInputs = new ArrayList<>();
        return userInputs;
    }

    /**
     * Replaces summary entries for user-supplied input items.
     *
     * @param userInputs list of InputSummaryEntry objects
     */
    public void setUserInputs(List<InputSummaryEntry> userInputs) {
        this.userInputs = userInputs == null ? new ArrayList<>() : new ArrayList<>(userInputs);
    }

    /**
     * Returns required raw inputs not supplied by the user that were auto-calculated.
     *
     * @return list of RateEntry objects in items per second
     */
    public List<RateEntry> getCalculatedMissingInputs() {
        if (calculatedMissingInputs == null) calculatedMissingInputs = new ArrayList<>();
        return calculatedMissingInputs;
    }

    /**
     * Replaces the list of auto-calculated missing raw inputs.
     *
     * @param calculatedMissingInputs list of RateEntry objects
     */
    public void setCalculatedMissingInputs(List<RateEntry> calculatedMissingInputs) {
        this.calculatedMissingInputs = calculatedMissingInputs == null ? new ArrayList<>() : new ArrayList<>(calculatedMissingInputs);
    }

    /**
     * Returns the final target and co-product output rates.
     *
     * @return list of RateEntry objects in items per second
     */
    public List<RateEntry> getFinalOutputs() {
        if (finalOutputs == null) finalOutputs = new ArrayList<>();
        return finalOutputs;
    }

    /**
     * Replaces the list of final output rates.
     *
     * @param finalOutputs list of RateEntry objects
     */
    public void setFinalOutputs(List<RateEntry> finalOutputs) {
        this.finalOutputs = finalOutputs == null ? new ArrayList<>() : new ArrayList<>(finalOutputs);
    }

    /**
     * Returns unrecycled or surplus byproduct output rates.
     *
     * @return list of byproduct RateEntry objects in items per second
     */
    public List<RateEntry> getByproductOutputs() {
        if (byproductOutputs == null) byproductOutputs = new ArrayList<>();
        return byproductOutputs;
    }

    /**
     * Replaces the list of byproduct output rates.
     *
     * @param byproductOutputs list of byproduct RateEntry objects
     */
    public void setByproductOutputs(List<RateEntry> byproductOutputs) {
        this.byproductOutputs = byproductOutputs == null ? new ArrayList<>() : new ArrayList<>(byproductOutputs);
    }

    /**
     * Returns the aggregated machine counts grouped by concrete machine.
     *
     * @return list of MachineTotal summaries
     */
    public List<MachineTotal> getMachineTotals() {
        if (machineTotals == null) machineTotals = new ArrayList<>();
        return machineTotals;
    }

    /**
     * Replaces the aggregated machine count summaries.
     *
     * @param machineTotals list of MachineTotal summaries
     */
    public void setMachineTotals(List<MachineTotal> machineTotals) {
        this.machineTotals = machineTotals == null ? new ArrayList<>() : new ArrayList<>(machineTotals);
    }

    /**
     * Returns the set of byproduct item UUIDs eligible for recursive reuse in the active graph.
     *
     * @return set of recyclable byproduct item UUIDs
     */
    public Set<UUID> getRecyclableByproductIds() {
        if (recyclableByproductIds == null) recyclableByproductIds = new LinkedHashSet<>();
        return recyclableByproductIds;
    }

    /**
     * Replaces the set of recyclable byproduct item UUIDs.
     *
     * @param recyclableByproductIds set of item UUIDs
     */
    public void setRecyclableByproductIds(Set<UUID> recyclableByproductIds) {
        this.recyclableByproductIds = recyclableByproductIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(recyclableByproductIds);
    }

    /**
     * Returns solver notices (INFO, WARNING, ERROR) generated during calculation.
     *
     * @return list of SolverNotice objects
     */
    public List<SolverNotice> getNotices() {
        if (notices == null) notices = new ArrayList<>();
        return notices;
    }

    /**
     * Replaces the list of solver notices.
     *
     * @param notices list of SolverNotice objects
     */
    public void setNotices(List<SolverNotice> notices) {
        this.notices = notices == null ? new ArrayList<>() : new ArrayList<>(notices);
    }

    /**
     * Returns the map of item UUID to resolved recipe UUID used in the solution.
     *
     * @return map of item UUID to recipe UUID
     */
    public Map<UUID, UUID> getResolvedRecipes() {
        if (resolvedRecipes == null) resolvedRecipes = new LinkedHashMap<>();
        return resolvedRecipes;
    }

    /**
     * Replaces the item-to-recipe resolution map.
     *
     * @param resolvedRecipes map of item UUID to recipe UUID
     */
    public void setResolvedRecipes(Map<UUID, UUID> resolvedRecipes) {
        this.resolvedRecipes = resolvedRecipes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resolvedRecipes);
    }

    /**
     * Returns the map of recipe UUID to resolved machine UUID used in the solution.
     *
     * @return map of recipe UUID to machine UUID
     */
    public Map<UUID, UUID> getResolvedMachines() {
        if (resolvedMachines == null) resolvedMachines = new LinkedHashMap<>();
        return resolvedMachines;
    }

    /**
     * Replaces the recipe-to-machine resolution map.
     *
     * @param resolvedMachines map of recipe UUID to machine UUID
     */
    public void setResolvedMachines(Map<UUID, UUID> resolvedMachines) {
        this.resolvedMachines = resolvedMachines == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resolvedMachines);
    }

    /**
     * Returns the effective recycling toggle for each recyclable byproduct item.
     *
     * @return map of byproduct item UUID to recycling boolean
     */
    public Map<UUID, Boolean> getResolvedRecycling() {
        if (resolvedRecycling == null) resolvedRecycling = new LinkedHashMap<>();
        return resolvedRecycling;
    }

    /**
     * Replaces the effective byproduct recycling map.
     *
     * @param resolvedRecycling map of byproduct item UUID to recycling boolean
     */
    public void setResolvedRecycling(Map<UUID, Boolean> resolvedRecycling) {
        this.resolvedRecycling = resolvedRecycling == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resolvedRecycling);
    }
}
