package hu.bme.prog3.factcalc.persistence;

import com.google.gson.Gson;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.ProductionPlanConfig;
import hu.bme.prog3.factcalc.model.ProductionPlanResult;
import hu.bme.prog3.factcalc.model.RateEntry;
import hu.bme.prog3.factcalc.model.SavedPlan;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlanRepository {
    private final Gson gson;

    /**
     * Creates a PlanRepository using the default GsonFactory configuration.
     */
    public PlanRepository() {
        this(GsonFactory.createGson());
    }

    /**
     * Creates a PlanRepository using a caller-supplied Gson instance.
     *
     * @param gson configured Gson serializer/deserializer
     */
    public PlanRepository(Gson gson) {
        this.gson = gson;
    }

    /**
     * Loads and parses a SavedPlan from a JSON file on disk.
     *
     * @param path path to the saved plan file
     * @return deserialized SavedPlan
     * @throws IOException if the file cannot be read or contains invalid/empty JSON
     */
    public SavedPlan load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            SavedPlan plan = gson.fromJson(reader, SavedPlan.class);
            if (plan == null) {
                throw new IOException("Saved plan file is empty or invalid: " + path);
            }
            return plan;
        } catch (RuntimeException ex) {
            throw new IOException("Failed to parse saved plan JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * Serializes a SavedPlan as UTF-8 JSON to the given file path, creating parent directories if needed.
     *
     * @param plan the SavedPlan snapshot to persist
     * @param path destination file path
     * @throws IOException if writing to disk fails
     */
    public void save(SavedPlan plan, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(plan, writer);
        }
    }

    /**
     * Checks whether all item, machine, and recipe IDs referenced in a saved plan
     * exist in the currently loaded catalog.
     *
     * @param plan    the loaded SavedPlan to validate
     * @param catalog the active Catalog to validate references against
     * @return list of human-readable descriptions for any missing references (empty if fully compatible)
     */
    public List<String> validateAgainstCatalog(SavedPlan plan, Catalog catalog) {
        List<String> missing = new ArrayList<>();
        if (plan == null || catalog == null) {
            missing.add("Plan or catalog is null");
            return missing;
        }
        ProductionPlanConfig config = plan.getConfig();
        if (config != null) {
            for (RateEntry entry : config.getInputs()) {
                if (entry.getItemId() != null && catalog.findItem(entry.getItemId()).isEmpty()) {
                    missing.add("Input item ID: " + entry.getItemId());
                }
            }
            for (RateEntry entry : config.getTargets()) {
                if (entry.getItemId() != null && catalog.findItem(entry.getItemId()).isEmpty()) {
                    missing.add("Target item ID: " + entry.getItemId());
                }
            }
            for (Map.Entry<UUID, UUID> e : config.getRecipeOverrides().entrySet()) {
                if (catalog.findItem(e.getKey()).isEmpty()) {
                    missing.add("Override item ID: " + e.getKey());
                }
                if (catalog.findRecipe(e.getValue()).isEmpty()) {
                    missing.add("Override recipe ID: " + e.getValue());
                }
            }
            for (Map.Entry<UUID, UUID> e : config.getMachineSelections().entrySet()) {
                if (catalog.findRecipe(e.getKey()).isEmpty()) {
                    missing.add("Selected recipe ID: " + e.getKey());
                }
                if (catalog.findMachine(e.getValue()).isEmpty()) {
                    missing.add("Selected machine ID: " + e.getValue());
                }
            }
            for (UUID itemId : config.getRecycledByproducts().keySet()) {
                if (catalog.findItem(itemId).isEmpty()) {
                    missing.add("Recycled byproduct item ID: " + itemId);
                }
            }
        }
        ProductionPlanResult result = plan.getResult();
        if (result != null) {
            result.getNodes().forEach(node -> {
                if (node.getRecipeId() != null && catalog.findRecipe(node.getRecipeId()).isEmpty()) {
                    missing.add("Result recipe '" + node.getRecipeName() + "' (" + node.getRecipeId() + ")");
                }
                if (node.getMachineId() != null && catalog.findMachine(node.getMachineId()).isEmpty()) {
                    missing.add("Result machine '" + node.getMachineName() + "' (" + node.getMachineId() + ")");
                }
            });
        }
        return missing;
    }
}
