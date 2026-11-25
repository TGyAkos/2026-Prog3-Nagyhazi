package hu.bme.prog3.factcalc.service;

import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.ItemAmount;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.Recipe;
import hu.bme.prog3.factcalc.model.RecipeMachineSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Provides validated CRUD operations and referential-integrity enforcement
 * for an in-memory Catalog.
 */
public class CatalogService {

    public static class ValidationException extends RuntimeException {
        /**
         * Creates a ValidationException describing a CRUD or integrity violation.
         *
         * @param message descriptive error message
         */
        public ValidationException(String message) {
            super(message);
        }
    }

    private Catalog catalog;

    /**
     * Creates a CatalogService operating on the given Catalog.
     *
     * @param catalog the catalog to manage
     */
    public CatalogService(Catalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    }

    /**
     * Returns the currently managed Catalog.
     *
     * @return the active Catalog
     */
    public Catalog getCatalog() {
        return catalog;
    }

    /**
     * Replaces the currently managed Catalog instance.
     *
     * @param catalog the new Catalog to manage
     */
    public void setCatalog(Catalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    }

    /**
     * Validates and creates a new plain Item in the catalog with a generated UUID.
     *
     * @param name display name of the item (must be non-blank)
     * @param img  optional icon path or URL
     * @return the newly created Item
     * @throws ValidationException if validation fails
     */
    public Item createItem(String name, String img) {
        validateItemFields(name);
        Item item = new Item(UUID.randomUUID(), name.trim(), img);
        catalog.putItem(item);
        return item;
    }

    /**
     * Updates an existing plain Item's name and image path.
     *
     * @param id   UUID of the item to update
     * @param name new display name (must be non-blank)
     * @param img  new icon path or URL
     * @throws ValidationException if the item does not exist, is a Machine, or validation fails
     */
    public void updateItem(UUID id, String name, String img) {
        Item existing = catalog.findItem(id)
                .orElseThrow(() -> new ValidationException("Item not found: " + id));
        if (existing instanceof Machine) {
            throw new ValidationException("Use updateMachine to modify a machine.");
        }
        validateItemFields(name);
        existing.setName(name.trim());
        existing.setImg(img);
    }

    /**
     * Deletes an item from the catalog after verifying no recipes reference it.
     *
     * @param id UUID of the item (or machine) to delete
     * @throws ValidationException if the item is missing or still referenced by any recipe
     */
    public void deleteItem(UUID id) {
        Item existing = catalog.findItem(id)
                .orElseThrow(() -> new ValidationException("Item not found: " + id));
        if (existing instanceof Machine) {
            deleteMachine(id);
            return;
        }
        List<String> referencingRecipes = findRecipesReferencingItem(id);
        if (!referencingRecipes.isEmpty()) {
            throw new ValidationException(
                    "Cannot delete item '" + existing.getName() + "' because it is referenced by recipe(s): "
                            + String.join(", ", referencingRecipes));
        }
        catalog.removeItem(id);
    }

    /**
     * Validates and creates a new Machine in the catalog with a generated UUID.
     *
     * @param name       display name of the machine (must be non-blank)
     * @param type       machine category type (must be non-blank)
     * @param img        optional icon path or URL
     * @param multiplier general crafting speed multiplier (must be &gt; 0)
     * @return the newly created Machine
     * @throws ValidationException if validation fails
     */
    public Machine createMachine(String name, String type, String img, double multiplier) {
        validateMachineFields(name, type, multiplier);
        Machine machine = new Machine(UUID.randomUUID(), name.trim(), type.trim(), img, multiplier);
        catalog.putItem(machine);
        return machine;
    }

    /**
     * Updates an existing Machine while ensuring type changes do not leave any recipe without compatible machines.
     *
     * @param id         UUID of the machine to update
     * @param name       new display name
     * @param type       new machine category type
     * @param img        new icon path or URL
     * @param multiplier new general speed multiplier
     * @throws ValidationException if the machine is missing, fields are invalid, or a recipe would lose all compatible machines
     */
    public void updateMachine(UUID id, String name, String type, String img, double multiplier) {
        Machine existing = catalog.findMachine(id)
                .orElseThrow(() -> new ValidationException("Machine not found: " + id));
        validateMachineFields(name, type, multiplier);

        String oldType = existing.getType();
        String newType = type.trim();
        if (!Objects.equals(oldType, newType)) {
            List<String> orphaned = findRecipesLeftWithoutMachinesIfRemoved(existing);
            if (!orphaned.isEmpty()) {
                existing.setType(newType);
                List<String> stillOrphaned = new ArrayList<>();
                for (Recipe recipe : catalog.getRecipes()) {
                    if (catalog.getCompatibleMachines(recipe).isEmpty()) {
                        stillOrphaned.add(recipe.getName());
                    }
                }
                existing.setType(oldType);
                if (!stillOrphaned.isEmpty()) {
                    throw new ValidationException(
                            "Cannot change machine type from '" + oldType + "' to '" + newType
                                    + "' because the following recipe(s) would have no compatible machines: "
                                    + String.join(", ", stillOrphaned));
                }
            }
        }

        existing.setName(name.trim());
        existing.setType(newType);
        existing.setImg(img);
        existing.setMultiplier(multiplier);
    }

    /**
     * Deletes a machine from the catalog after checking recipe item references, explicit machine ID references,
     * and type-based machine availability.
     *
     * @param id UUID of the machine to delete
     * @throws ValidationException if the machine is not found or deleting it breaks referential integrity
     */
    public void deleteMachine(UUID id) {
        Machine existing = catalog.findMachine(id)
                .orElseThrow(() -> new ValidationException("Machine not found: " + id));

        Set<String> blockingReasons = new LinkedHashSet<>();

        for (String recipeName : findRecipesReferencingItem(id)) {
            blockingReasons.add(recipeName + " (used as ingredient/output)");
        }
        for (Recipe recipe : catalog.getRecipes()) {
            for (RecipeMachineSpec spec : recipe.getMachines()) {
                if (id.equals(spec.getId())) {
                    blockingReasons.add(recipe.getName() + " (explicit machine reference)");
                    break;
                }
            }
        }
        for (String recipeName : findRecipesLeftWithoutMachinesIfRemoved(existing)) {
            blockingReasons.add(recipeName + " (would have no compatible machines)");
        }

        if (!blockingReasons.isEmpty()) {
            throw new ValidationException(
                    "Cannot delete machine '" + existing.getName() + "' because it is required by recipe(s): "
                            + String.join(", ", blockingReasons));
        }
        catalog.removeItem(id);
    }

    /**
     * Validates and creates a new Recipe in the catalog with a generated UUID.
     *
     * @param name       display name of the recipe
     * @param time       base crafting duration in seconds
     * @param img        optional icon path or URL (inherits primary output icon if blank)
     * @param isOriginal true for an original recipe, false for an alternative recipe
     * @param machines   list of machine or machine-type specifications
     * @param inputs     list of input item amounts
     * @param outputs    list of main output item amounts
     * @param byproducts list of byproduct item amounts
     * @return the newly created Recipe
     * @throws ValidationException if validation fails
     */
    public Recipe createRecipe(
            String name,
            double time,
            String img,
            boolean isOriginal,
            List<RecipeMachineSpec> machines,
            List<ItemAmount> inputs,
            List<ItemAmount> outputs,
            List<ItemAmount> byproducts
    ) {
        Recipe recipe = new Recipe(
                UUID.randomUUID(),
                name == null ? "" : name.trim(),
                time,
                img,
                isOriginal,
                machines,
                inputs,
                outputs,
                byproducts
        );
        validateRecipe(recipe);
        applyDefaultRecipeImage(recipe);
        catalog.putRecipe(recipe);
        return recipe;
    }

    /**
     * Validates and updates an existing Recipe in place.
     *
     * @param id         UUID of the recipe to update
     * @param name       new display name
     * @param time       new base crafting duration in seconds
     * @param img        new icon path or URL
     * @param isOriginal true for an original recipe, false for an alternative recipe
     * @param machines   updated list of machine specifications
     * @param inputs     updated list of input item amounts
     * @param outputs    updated list of output item amounts
     * @param byproducts updated list of byproduct item amounts
     * @throws ValidationException if the recipe is not found or validation fails
     */
    public void updateRecipe(
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
        Recipe existing = catalog.findRecipe(id)
                .orElseThrow(() -> new ValidationException("Recipe not found: " + id));
        Recipe candidate = new Recipe(
                id,
                name == null ? "" : name.trim(),
                time,
                img,
                isOriginal,
                machines,
                inputs,
                outputs,
                byproducts
        );
        validateRecipe(candidate);
        applyDefaultRecipeImage(candidate);
        existing.setName(candidate.getName());
        existing.setTime(candidate.getTime());
        existing.setImg(candidate.getImg());
        existing.setOriginal(candidate.isOriginal());
        existing.setMachines(candidate.getMachines());
        existing.setInputs(candidate.getInputs());
        existing.setOutputs(candidate.getOutputs());
        existing.setByproducts(candidate.getByproducts());
    }

    /**
     * Deletes a recipe from the catalog by its UUID.
     *
     * @param id UUID of the recipe to delete
     * @throws ValidationException if the recipe does not exist
     */
    public void deleteRecipe(UUID id) {
        if (catalog.findRecipe(id).isEmpty()) {
            throw new ValidationException("Recipe not found: " + id);
        }
        catalog.removeRecipe(id);
    }

    /**
     * Validates all structural, numeric, and referential constraints on a Recipe against the current catalog.
     *
     * @param recipe the Recipe to validate
     * @throws ValidationException if any field, item reference, or machine specification is invalid
     */
    public void validateRecipe(Recipe recipe) {
        if (recipe == null) {
            throw new ValidationException("Recipe must not be null.");
        }
        if (recipe.getName() == null || recipe.getName().isBlank()) {
            throw new ValidationException("Recipe name must not be blank.");
        }
        if (Double.isNaN(recipe.getTime()) || Double.isInfinite(recipe.getTime()) || recipe.getTime() <= 0.0) {
            throw new ValidationException("Recipe production time must be greater than 0.");
        }
        if (recipe.getOutputs().isEmpty()) {
            throw new ValidationException("Recipe must have at least one output item.");
        }

        validateItemAmounts(recipe.getInputs(), "inputs", false);
        validateItemAmounts(recipe.getOutputs(), "outputs", true);
        validateItemAmounts(recipe.getByproducts(), "byproducts", true);

        if (recipe.getMachines().isEmpty()) {
            throw new ValidationException("Recipe must specify at least one compatible machine or machine type.");
        }

        Set<UUID> seenMachineIds = new HashSet<>();
        Set<String> seenMachineTypes = new HashSet<>();
        boolean resolvesToAtLeastOneMachine = false;

        for (RecipeMachineSpec spec : recipe.getMachines()) {
            boolean hasId = spec.getId() != null;
            boolean hasType = spec.getType() != null && !spec.getType().isBlank();
            if (hasId == hasType) {
                throw new ValidationException("Each machine specification must set either a specific machine ID or a machine type (not both or neither).");
            }
            if (Double.isNaN(spec.getMultiplier()) || Double.isInfinite(spec.getMultiplier()) || spec.getMultiplier() <= 0.0) {
                throw new ValidationException("Recipe machine multiplier must be greater than 0.");
            }
            if (hasId) {
                if (!seenMachineIds.add(spec.getId())) {
                    throw new ValidationException("Duplicate specific machine in recipe machine list.");
                }
                if (catalog.findMachine(spec.getId()).isEmpty()) {
                    throw new ValidationException("Referenced machine does not exist in catalog: " + spec.getId());
                }
                resolvesToAtLeastOneMachine = true;
            } else {
                String type = spec.getType().trim();
                spec.setType(type);
                if (!seenMachineTypes.add(type)) {
                    throw new ValidationException("Duplicate machine type '" + type + "' in recipe machine list.");
                }
                for (Machine m : catalog.getMachines()) {
                    if (type.equals(m.getType())) {
                        resolvesToAtLeastOneMachine = true;
                        break;
                    }
                }
            }
        }

        if (!resolvesToAtLeastOneMachine) {
            throw new ValidationException("Recipe's machine list does not match any existing machine in the catalog.");
        }
    }

    /**
     * Validates an input, output, or byproduct item-amount list for existence, uniqueness, and positive quantities.
     *
     * @param list            the list of ItemAmount entries to check
     * @param sectionName     label of the list section for error messages
     * @param requirePositive whether positive quantity is strictly required
     */
    private void validateItemAmounts(List<ItemAmount> list, String sectionName, boolean requirePositive) {
        Set<UUID> seen = new HashSet<>();
        for (ItemAmount ia : list) {
            if (ia.getId() == null) {
                throw new ValidationException("All entries in " + sectionName + " must reference an item.");
            }
            if (catalog.findItem(ia.getId()).isEmpty()) {
                throw new ValidationException("Referenced item in " + sectionName + " does not exist: " + ia.getId());
            }
            if (!seen.add(ia.getId())) {
                String itemName = catalog.findItem(ia.getId()).map(Item::getName).orElse(ia.getId().toString());
                throw new ValidationException("Duplicate item '" + itemName + "' in " + sectionName + ".");
            }
            if (Double.isNaN(ia.getPiece()) || Double.isInfinite(ia.getPiece())
                    || (requirePositive ? ia.getPiece() <= 0.0 : ia.getPiece() <= 0.0)) {
                throw new ValidationException("Item quantity in " + sectionName + " must be greater than 0.");
            }
        }
    }

    /**
     * Copies the primary output item's icon path onto the recipe if the recipe's own icon path is blank.
     *
     * @param recipe the recipe to populate
     */
    private void applyDefaultRecipeImage(Recipe recipe) {
        if (recipe.getImg().isBlank() && !recipe.getOutputs().isEmpty()) {
            UUID primaryOutId = recipe.getOutputs().get(0).getId();
            catalog.findItem(primaryOutId).ifPresent(item -> recipe.setImg(item.getImg()));
        }
    }

    /**
     * Validates basic fields required for an Item.
     *
     * @param name item name to validate
     */
    private void validateItemFields(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Item name must not be blank.");
        }
    }

    /**
     * Validates basic fields required for a Machine.
     *
     * @param name       machine name
     * @param type       machine type
     * @param multiplier machine speed multiplier
     */
    private void validateMachineFields(String name, String type, double multiplier) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Machine name must not be blank.");
        }
        if (type == null || type.isBlank()) {
            throw new ValidationException("Machine type must not be blank.");
        }
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier) || multiplier <= 0.0) {
            throw new ValidationException("Machine multiplier must be greater than 0.");
        }
    }

    /**
     * Collects names of all recipes that consume or produce the given item ID.
     *
     * @param itemId item UUID to search for
     * @return list of referencing recipe names
     */
    private List<String> findRecipesReferencingItem(UUID itemId) {
        List<String> names = new ArrayList<>();
        for (Recipe recipe : catalog.getRecipes()) {
            if (recipe.consumesInput(itemId) || recipe.producesOutput(itemId) || recipe.producesByproduct(itemId)) {
                names.add(recipe.getName());
            }
        }
        return names;
    }

    /**
     * Finds names of recipes that currently have compatible machines but would have zero if {@code machineToRemove} is removed.
     *
     * @param machineToRemove the machine whose removal is being simulated
     * @return list of recipe names that would become orphaned
     */
    private List<String> findRecipesLeftWithoutMachinesIfRemoved(Machine machineToRemove) {
        List<String> affected = new ArrayList<>();
        List<Machine> remaining = new ArrayList<>();
        for (Machine m : catalog.getMachines()) {
            if (!m.getId().equals(machineToRemove.getId())) {
                remaining.add(m);
            }
        }
        for (Recipe recipe : catalog.getRecipes()) {
            boolean hadAny = !catalog.getCompatibleMachines(recipe).isEmpty();
            boolean hasRemaining = false;
            for (Machine m : remaining) {
                if (recipe.isCompatibleWith(m)) {
                    hasRemaining = true;
                    break;
                }
            }
            if (hadAny && !hasRemaining) {
                affected.add(recipe.getName());
            }
        }
        return affected;
    }
}
