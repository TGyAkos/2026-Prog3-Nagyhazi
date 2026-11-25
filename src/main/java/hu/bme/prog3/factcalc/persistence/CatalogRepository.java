package hu.bme.prog3.factcalc.persistence;

import com.google.gson.Gson;
import hu.bme.prog3.factcalc.model.Catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CatalogRepository {
    public static final String DEFAULT_PRESET_RESOURCE = "/presets/sample_catalog.json";

    private final Gson gson;

    /**
     * Creates a CatalogRepository using the default GsonFactory configuration.
     */
    public CatalogRepository() {
        this(GsonFactory.createGson());
    }

    /**
     * Creates a CatalogRepository using a caller-supplied Gson instance.
     *
     * @param gson configured Gson serializer/deserializer
     */
    public CatalogRepository(Gson gson) {
        this.gson = gson;
    }

    /**
     * Loads and parses a Catalog from a JSON file on disk.
     *
     * @param path path to the catalog JSON file
     * @return deserialized Catalog
     * @throws IOException if the file cannot be read or contains invalid/empty JSON
     */
    public Catalog load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Catalog catalog = gson.fromJson(reader, Catalog.class);
            if (catalog == null) {
                throw new IOException("Catalog file is empty or invalid: " + path);
            }
            return catalog;
        } catch (RuntimeException ex) {
            throw new IOException("Failed to parse catalog JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * Loads and parses a Catalog from a classpath resource.
     *
     * @param resourcePath classpath resource path (e.g. "/presets/sample_catalog.json")
     * @return deserialized Catalog
     * @throws IOException if the resource is missing, unreadable, or invalid JSON
     */
    public Catalog loadFromResource(String resourcePath) throws IOException {
        try (InputStream is = CatalogRepository.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Preset resource not found: " + resourcePath);
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Catalog catalog = gson.fromJson(reader, Catalog.class);
                if (catalog == null) {
                    throw new IOException("Preset resource is empty: " + resourcePath);
                }
                return catalog;
            }
        } catch (RuntimeException ex) {
            throw new IOException("Failed to parse preset catalog JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * Loads the built-in sample factory catalog preset from classpath resources.
     *
     * @return the built-in default preset Catalog
     * @throws IOException if the preset resource cannot be loaded or parsed
     */
    public Catalog loadDefaultPreset() throws IOException {
        return loadFromResource(DEFAULT_PRESET_RESOURCE);
    }

    /**
     * Serializes a Catalog as UTF-8 JSON to the given file path, creating parent directories if needed.
     *
     * @param catalog the Catalog to save
     * @param path    destination file path
     * @throws IOException if writing to disk fails
     */
    public void save(Catalog catalog, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(catalog, writer);
        }
    }
}
