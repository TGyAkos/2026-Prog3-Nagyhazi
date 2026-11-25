package hu.bme.prog3.factcalc.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import hu.bme.prog3.factcalc.model.Catalog;
import hu.bme.prog3.factcalc.model.Item;
import hu.bme.prog3.factcalc.model.Machine;
import hu.bme.prog3.factcalc.model.Recipe;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GsonFactory {

    /**
     * Private constructor to prevent instantiation of utility factory class.
     */
    private GsonFactory() {
    }

    /**
     * Builds a pretty-printing Gson instance configured with custom adapters for UUID, Instant, and Catalog.
     *
     * @return configured Gson instance
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(UUID.class, new UuidAdapter())
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .registerTypeAdapter(Catalog.class, new CatalogAdapter())
                .create();
    }

    private static final class UuidAdapter implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
        /**
         * Serializes a UUID to its standard string JSON primitive.
         *
         * @param src       the UUID to serialize
         * @param typeOfSrc runtime type of the source object
         * @param context   Gson serialization context
         * @return JSON primitive string, or null if src is null
         */
        @Override
        public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? null : new JsonPrimitive(src.toString());
        }

        /**
         * Deserializes a UUID from a JSON string element.
         *
         * @param json    JSON element containing the UUID string
         * @param typeOfT target type
         * @param context Gson deserialization context
         * @return parsed UUID, or null if JSON is null or blank
         * @throws JsonParseException if the string is not a valid UUID
         */
        @Override
        public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            String text = json.getAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return UUID.fromString(text.trim());
            } catch (IllegalArgumentException ex) {
                throw new JsonParseException("Invalid UUID: " + text, ex);
            }
        }
    }

    private static final class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        /**
         * Serializes an Instant to an ISO-8601 JSON string.
         *
         * @param src       the Instant to serialize
         * @param typeOfSrc runtime type of the source object
         * @param context   Gson serialization context
         * @return JSON primitive ISO-8601 string, or null if src is null
         */
        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? null : new JsonPrimitive(src.toString());
        }

        /**
         * Deserializes an Instant from an ISO-8601 JSON string element.
         *
         * @param json    JSON element containing the timestamp string
         * @param typeOfT target type
         * @param context Gson deserialization context
         * @return parsed Instant, or null if JSON is null
         * @throws JsonParseException if the timestamp cannot be parsed
         */
        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            return Instant.parse(json.getAsString());
        }
    }

    private static final class CatalogAdapter implements JsonSerializer<Catalog>, JsonDeserializer<Catalog> {
        private static final Type ITEM_LIST_TYPE = new TypeToken<List<Item>>() {
        }.getType();
        private static final Type MACHINE_LIST_TYPE = new TypeToken<List<Machine>>() {
        }.getType();
        private static final Type RECIPE_LIST_TYPE = new TypeToken<List<Recipe>>() {
        }.getType();

        /**
         * Serializes a Catalog into JSON containing separate {@code items}, {@code machines}, and {@code recipes} arrays.
         *
         * @param src       the Catalog to serialize
         * @param typeOfSrc runtime type of the source object
         * @param context   Gson serialization context
         * @return JSON object representing the catalog
         */
        @Override
        public JsonElement serialize(Catalog src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", src.getName());
            obj.add("items", context.serialize(src.getPlainItems(), ITEM_LIST_TYPE));
            obj.add("machines", context.serialize(src.getMachines(), MACHINE_LIST_TYPE));
            obj.add("recipes", context.serialize(src.getRecipes(), RECIPE_LIST_TYPE));
            return obj;
        }

        /**
         * Deserializes a Catalog from a JSON object with {@code name}, {@code items}, {@code machines}, and {@code recipes}.
         *
         * @param json    JSON element representing the catalog
         * @param typeOfT target type
         * @param context Gson deserialization context
         * @return populated Catalog instance
         * @throws JsonParseException if the JSON structure is invalid
         */
        @Override
        public Catalog deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String name = obj.has("name") && !obj.get("name").isJsonNull()
                    ? obj.get("name").getAsString()
                    : "Untitled Catalog";
            Catalog catalog = new Catalog(name);

            if (obj.has("items") && obj.get("items").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("items");
                List<Item> items = context.deserialize(arr, ITEM_LIST_TYPE);
                catalog.addAllItems(items);
            }
            if (obj.has("machines") && obj.get("machines").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("machines");
                List<Machine> machines = context.deserialize(arr, MACHINE_LIST_TYPE);
                catalog.addAllItems(machines);
            }
            if (obj.has("recipes") && obj.get("recipes").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("recipes");
                List<Recipe> recipes = context.deserialize(arr, RECIPE_LIST_TYPE);
                catalog.addAllRecipes(recipes);
            }
            return catalog;
        }
    }
}
