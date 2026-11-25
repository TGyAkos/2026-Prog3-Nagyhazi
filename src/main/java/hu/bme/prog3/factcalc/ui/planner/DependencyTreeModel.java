package hu.bme.prog3.factcalc.ui.planner;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.FlowEdge;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ProductionNode;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.TimeUnit;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a cycle-safe Swing TreeModel representing the solved production graph
 * from final target outputs down to external/missing inputs.
 */
public final class DependencyTreeModel {

    public static final class TreeNodeData {
        private final String label;
        private final String iconPath;
        private final boolean recursiveTag;

        /**
         * Creates display metadata for a node in the production dependency tree.
         *
         * @param label        formatted display text for the tree node
         * @param iconPath     optional icon path or URL associated with the node's item or recipe
         * @param recursiveTag {@code true} if the node represents a recursive byproduct/cycle edge
         */
        public TreeNodeData(String label, String iconPath, boolean recursiveTag) {
            this.label = label;
            this.iconPath = iconPath;
            this.recursiveTag = recursiveTag;
        }

        /**
         * Returns the formatted label text displayed in the tree.
         *
         * @return the node display label
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns the icon URL or file path associated with this node.
         *
         * @return the icon path string (may be empty)
         */
        public String getIconPath() {
            return iconPath;
        }

        /**
         * Returns whether this node denotes a recursive feedback loop or cycle break.
         *
         * @return {@code true} if recursive/cyclic, {@code false} otherwise
         */
        public boolean isRecursiveTag() {
            return recursiveTag;
        }

        /**
         * Returns the node's display label for Swing tree rendering.
         *
         * @return the node display label
         */
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private DependencyTreeModel() {
    }

    /**
     * Builds a Swing {@link DefaultTreeModel} tracing production dependencies from final outputs
     * and surplus byproducts down to external and raw inputs, breaking cycles safely.
     *
     * @param catalog     the active factory catalog for resolving item and recipe names/icons
     * @param result      the solved production plan result (may be {@code null})
     * @param displayUnit the time unit used to format flow rates
     * @return the constructed {@link DefaultTreeModel} rooted at {@code "Production Line"}
     */
    public static DefaultTreeModel build(Catalog catalog, ProductionPlanResult result, TimeUnit displayUnit) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                new TreeNodeData("Production Line", "", false)
        );
        if (result == null || catalog == null) {
            return new DefaultTreeModel(root);
        }

        Map<UUID, ProductionNode> nodesByRecipeId = new LinkedHashMap<>();
        for (ProductionNode node : result.getNodes()) {
            nodesByRecipeId.put(node.getRecipeId(), node);
        }

        Set<UUID> userInputIds = new HashSet<>();
        for (ProductionPlanResult.InputSummaryEntry entry : result.getUserInputs()) {
            userInputIds.add(entry.getItemId());
        }
        Set<UUID> missingInputIds = new HashSet<>();
        for (RateEntry entry : result.getCalculatedMissingInputs()) {
            missingInputIds.add(entry.getItemId());
        }

        for (RateEntry finalOut : result.getFinalOutputs()) {
            Item item = catalog.findItem(finalOut.getItemId()).orElse(null);
            String itemName = item != null ? item.getName() : String.valueOf(finalOut.getItemId());
            String iconPath = item != null ? item.getImg() : "";
            String rateStr = formatRate(finalOut.getRate(), displayUnit);
            DefaultMutableTreeNode targetNode = new DefaultMutableTreeNode(
                    new TreeNodeData("Target: " + itemName + " (" + rateStr + ")", iconPath, false)
            );
            root.add(targetNode);

            // Find producers contributing to FINAL_OUTPUT for this item
            for (FlowEdge edge : result.getEdges()) {
                if (edge.getType() == FlowEdge.EdgeType.FINAL_OUTPUT && edge.getItemId().equals(finalOut.getItemId())) {
                    ProductionNode producer = nodesByRecipeId.get(edge.getSourceRecipeId());
                    if (producer != null) {
                        Set<UUID> ancestors = new HashSet<>();
                        targetNode.add(buildRecipeBranch(catalog, result, nodesByRecipeId, userInputIds, missingInputIds,
                                producer, edge.getRatePerSecond(), displayUnit, ancestors));
                    }
                }
            }
        }

        if (!result.getByproductOutputs().isEmpty()) {
            DefaultMutableTreeNode bypRoot = new DefaultMutableTreeNode(
                    new TreeNodeData("Byproduct Outputs", "", false)
            );
            for (RateEntry byp : result.getByproductOutputs()) {
                Item item = catalog.findItem(byp.getItemId()).orElse(null);
                String itemName = item != null ? item.getName() : String.valueOf(byp.getItemId());
                String iconPath = item != null ? item.getImg() : "";
                bypRoot.add(new DefaultMutableTreeNode(
                        new TreeNodeData(itemName + " (" + formatRate(byp.getRate(), displayUnit) + ")", iconPath, false)
                ));
            }
            root.add(bypRoot);
        }

        return new DefaultTreeModel(root);
    }

    /**
     * Recursively constructs a subtree node for a single {@link ProductionNode} and its incoming flow edges,
     * halting expansion when an ancestor recipe is revisited to prevent infinite recursion.
     *
     * @param catalog                the active factory catalog
     * @param result                 the solved production plan result containing flow edges
     * @param nodesByRecipeId        lookup map from recipe ID to {@link ProductionNode}
     * @param userInputIds           set of user-supplied input item IDs
     * @param missingInputIds        set of auto-calculated missing input item IDs
     * @param node                   the current production node being expanded
     * @param flowContributionPerSec item flow rate supplied by this node to its parent edge in items/sec
     * @param unit                   display time unit for formatting rates
     * @param ancestors              set of ancestor recipe IDs along the active DFS path for cycle detection
     * @return the populated {@link DefaultMutableTreeNode} subtree
     */
    private static DefaultMutableTreeNode buildRecipeBranch(
            Catalog catalog,
            ProductionPlanResult result,
            Map<UUID, ProductionNode> nodesByRecipeId,
            Set<UUID> userInputIds,
            Set<UUID> missingInputIds,
            ProductionNode node,
            double flowContributionPerSec,
            TimeUnit unit,
            Set<UUID> ancestors
    ) {
        String recipeIcon = catalog.findRecipe(node.getRecipeId()).map(r -> r.getImg()).orElse("");
        String altTag = node.isOriginalRecipe() ? "" : " [Alternative]";
        String label = String.format(
                "%s - %s - %s: %.2f (%d pcs)%s",
                node.getRecipeName(),
                formatRate(flowContributionPerSec, unit),
                node.getMachineName(),
                node.getExactMachineCount(),
                node.getCeilMachineCount(),
                altTag
        );
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
                new TreeNodeData(label, recipeIcon, false)
        );

        if (!ancestors.add(node.getRecipeId())) {
            treeNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData("[Recursive Feedback Cycle]", "", true)
            ));
            return treeNode;
        }

        // Expand edges feeding into this recipe node
        List<FlowEdge> incoming = result.getEdges().stream()
                .filter(e -> node.getRecipeId().equals(e.getTargetRecipeId()))
                .toList();

        for (FlowEdge edge : incoming) {
            Item item = catalog.findItem(edge.getItemId()).orElse(null);
            String itemName = item != null ? item.getName() : edge.getItemName();
            String itemIcon = item != null ? item.getImg() : "";
            String rateStr = formatRate(edge.getRatePerSecond(), unit);

            if (edge.getType() == FlowEdge.EdgeType.RECURSIVE_FEEDBACK) {
                treeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(itemName + " (" + rateStr + ") [Recursive Feedback from " + edge.getSourceLabel() + "]", itemIcon, true)
                ));
            } else if (edge.getType() == FlowEdge.EdgeType.EXTERNAL_INPUT) {
                String tag = missingInputIds.contains(edge.getItemId())
                        ? " [Calculated Missing Input]"
                        : (userInputIds.contains(edge.getItemId()) ? " [External Input]" : " [Raw Input]");
                treeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(itemName + " (" + rateStr + ")" + tag, itemIcon, false)
                ));
            } else if (edge.getType() == FlowEdge.EdgeType.INTERMEDIATE) {
                ProductionNode upstream = nodesByRecipeId.get(edge.getSourceRecipeId());
                if (upstream != null) {
                    if (ancestors.contains(upstream.getRecipeId())) {
                        treeNode.add(new DefaultMutableTreeNode(
                                new TreeNodeData(itemName + " (" + rateStr + ") [Recursive Cycle -> " + upstream.getRecipeName() + "]", itemIcon, true)
                        ));
                    } else {
                        treeNode.add(buildRecipeBranch(catalog, result, nodesByRecipeId, userInputIds, missingInputIds,
                                upstream, edge.getRatePerSecond(), unit, ancestors));
                    }
                }
            }
        }

        ancestors.remove(node.getRecipeId());
        return treeNode;
    }

    /**
     * Converts a per-second item rate to the requested {@link TimeUnit} and formats it with two decimal places.
     *
     * @param ratePerSecond rate expressed in items per second
     * @param unit          target display time unit
     * @return formatted rate string including the unit suffix
     */
    public static String formatRate(double ratePerSecond, TimeUnit unit) {
        double converted = unit.fromPerSecond(ratePerSecond);
        return String.format("%.2f %s", converted, unit.getLabel());
    }
}
