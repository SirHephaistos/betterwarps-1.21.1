package as.sirhephaistos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Central registry for warps, grouped per dimension.
 * Persists to and loads from <server>/config/betterwarps.json.
 */
public final class WarpManager {
    private static final WarpManager INSTANCE = new WarpManager();
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetterwarps");
    /**
     * Map<warpNameLower, WarpPoint>
     */
    private final Map<String, WarpPoint> warps = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String fileName = "warps.json";
    private Path file;

    private WarpManager() {
    }

    public static WarpManager get() {
        return INSTANCE;
    }

    /**
     * Utility: normalize user-provided warp names (lowercase, trimmed).
     */
    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Loads warps from config/simplybetter/warps.json.
     * If the file does not exist, it is created empty.
     */
    public void load() {
        try {
            file = Path.of("config", "simplybetter", fileName);
            Files.createDirectories(file.getParent());

            // Create empty file if it does not exist
            if (!Files.exists(file)) {
                try (Writer writer = Files.newBufferedWriter(file)) {
                    writer.write("{}");
                }
                return;
            }

            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                warps.clear();
                for (String name : root.keySet()) {
                    JsonObject obj = root.getAsJsonObject(name);
                    warps.put(name.toLowerCase(Locale.ROOT), WarpPoint.fromJson(obj));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load warps from {}", file.toString(), e);
        }
    }

    /**
     * Saves current warps to config/simplybetter/warps.json (pretty JSON).
     * Creates directories and file if needed.
     */
    public void save() {
        try {
            file = Path.of("config", "simplybetter", fileName);
            Files.createDirectories(file.getParent());

            JsonObject root = new JsonObject();
            for (var entry : warps.entrySet()) {
                root.add(entry.getKey(), entry.getValue().toJson());
            }

            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save warps to {}", file.toString(), e);
        }
    }

    /**
     * Sets or updates a warp point.
     */
    public void setWarp(String name, WarpPoint point) {
        String normName = normalizeName(name);
        warps.put(normName, point);
    }

    /**
     * Deletes a warp point. Returns true if deleted, false if not found.
     */
    public boolean delWarp(String name) {
        String normName = normalizeName(name);
        return warps.remove(normName) != null;
    }

    /**
     * Lists all warps.
     */
    public Map<String, WarpPoint> listWarps() {
        return Map.copyOf(warps);
    }

    /**
     * Gets a warp point by name. Returns null if not found.
     *
     * @param warpName Name of the warp to retrieve.
     * @return WarpPoint or null if not found.
     */
    public WarpPoint getWarp(String warpName) {
        String normName = normalizeName(warpName);
        if (!warps.containsKey(normName)) {
            throw new IllegalArgumentException("Warp " + normName + " not found");
        }
        return warps.get(normName);
    }
}
