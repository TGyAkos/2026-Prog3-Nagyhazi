package hu.bme.prog3.factcalc.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * In-memory catalog indexing all Items (including Machines, since Machine extends Item)
 * and Recipes.
 */
public class Catalog {
    private String name;
    private final Map<UUID, Item> itemsById = new LinkedHashMap<>();
    private final Map<UUID, Recipe> recipesById = new LinkedHashMap<>();

    /**
     * Creates a new empty catalog with the default name "Untitled Catalog".
     */
    public Catalog() {
        this("Untitled Catalog");
    }

    /**
     * Creates a new empty catalog with the given name.
     *
     * @param name catalog name (falls back to "Untitled Catalog" if null or blank)
     */
    public Catalog(String name) {
        this.name = name == null || name.isBlank() ? "Untitled Catalog" : name;
    }

    /**
     * Returns the catalog's display name.
     *
     * @return the catalog name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the catalog's display name.
     *
     * @param name the new catalog name (falls back to "Untitled Catalog" if null or blank)
     */
    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "Untitled Catalog" : name;
    }

    /**
     * Inserts or replaces an Item (or Machine) in the catalog by its ID.
     *
     * @param item the item or machine to store
     * @throws IllegalArgumentException if item or its ID is null
     */
    public void putItem(Item item) {
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException("Item and item ID must not be null");
        }
        itemsById.put(item.getId(), item);
    }

    /**
     * Removes an item or machine from the catalog by its UUID.
     *
     * @param itemId the UUID of the item to remove
     */
    public void removeItem(UUID itemId) {
        itemsById.remove(itemId);
    }

    /**
     * Inserts or replaces a Recipe in the catalog by its ID.
     *
     * @param recipe the recipe to store
     * @throws IllegalArgumentException if recipe or its ID is null
     */
    public void putRecipe(Recipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            throw new IllegalArgumentException("Recipe and recipe ID must not be null");
        }
        recipesById.put(recipe.getId(), recipe);
    }

    /**
     * Removes a recipe from the catalog by its UUID.
     *
     * @param recipeId the UUID of the recipe to remove
     */
    public void removeRecipe(UUID recipeId) {
        recipesById.remove(recipeId);
    }

    /**
     * Looks up an Item (including Machines) by UUID.
     *
     * @param id the item UUID
     * @return an Optional containing the item if found
     */
    public Optional<Item> findItem(UUID id) {
        return Optional.ofNullable(itemsById.get(id));
    }

    /**
     * Looks up a Machine by UUID.
     *
     * @param id the machine UUID
     * @return an Optional containing the machine if found and is a Machine instance
     */
    public Optional<Machine> findMachine(UUID id) {
        Item item = itemsById.get(id);
        if (item instanceof Machine machine) {
            return Optional.of(machine);
        }
        return Optional.empty();
    }

    /**
     * Looks up a Recipe by UUID.
     *
     * @param id the recipe UUID
     * @return an Optional containing the recipe if found
     */
    public Optional<Recipe> findRecipe(UUID id) {
        return Optional.ofNullable(recipesById.get(id));
    }

    /**
     * Returns all items in the catalog, including machines.
     *
     * @return list of all Item and Machine instances
     */
    public List<Item> getAllItems() {
        return new ArrayList<>(itemsById.values());
    }

    /**
     * Returns only plain items (excluding Machine instances).
     *
     * @return list of non-machine Item instances
     */
    public List<Item> getPlainItems() {
        List<Item> result = new ArrayList<>();
        for (Item item : itemsById.values()) {
            if (!(item instanceof Machine)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Returns all machines stored in the catalog.
     *
     * @return list of Machine instances
     */
    public List<Machine> getMachines() {
        List<Machine> result = new ArrayList<>();
        for (Item item : itemsById.values()) {
            if (item instanceof Machine machine) {
                result.add(machine);
            }
        }
        return result;
    }

    /**
     * Returns all recipes stored in the catalog.
     *
     * @return list of Recipe instances
     */
    public List<Recipe> getRecipes() {
        return new ArrayList<>(recipesById.values());
    }

    /**
     * Finds all machines in the catalog capable of executing the given recipe.
     *
     * @param recipe the recipe to check compatibility against
     * @return list of compatible Machine instances
     */
    public List<Machine> getCompatibleMachines(Recipe recipe) {
        List<Machine> compatible = new ArrayList<>();
        if (recipe == null) {
            return compatible;
        }
        for (Machine machine : getMachines()) {
            if (recipe.isCompatibleWith(machine)) {
                compatible.add(machine);
            }
        }
        return compatible;
    }

    /**
     * Collects all distinct non-blank machine category types present in the catalog.
     *
     * @return sorted set of machine type names
     */
    public Set<String> getMachineTypes() {
        Set<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Machine machine : getMachines()) {
            if (machine.getType() != null && !machine.getType().isBlank()) {
                types.add(machine.getType());
            }
        }
        return types;
    }

    /**
     * Finds all recipes whose primary outputs include the specified item.
     *
     * @param itemId the produced item UUID
     * @return list of recipes producing that item
     */
    public List<Recipe> getRecipesProducing(UUID itemId) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : recipesById.values()) {
            if (recipe.producesOutput(itemId)) {
                result.add(recipe);
            }
        }
        return result;
    }

    /**
     * Removes all items, machines, and recipes from the catalog.
     */
    public void clear() {
        itemsById.clear();
        recipesById.clear();
    }

    /**
     * Adds a collection of items or machines into the catalog.
     *
     * @param items collection of items to add (ignored if null)
     */
    public void addAllItems(Collection<? extends Item> items) {
        if (items == null) return;
        for (Item item : items) {
            putItem(item);
        }
    }

    /**
     * Adds a collection of recipes into the catalog.
     *
     * @param recipes collection of recipes to add (ignored if null)
     */
    public void addAllRecipes(Collection<Recipe> recipes) {
        if (recipes == null) return;
        for (Recipe recipe : recipes) {
            putRecipe(recipe);
        }
    }
}
