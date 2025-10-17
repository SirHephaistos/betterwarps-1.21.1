package as.sirhephaistos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Central registry for warps, grouped per dimension.
 * Persists to and loads from <server>/config/betterwarps.json.
 *
 * Data shape:
 * {
 *   "<dimension-id>": {
 *     "<warp-name-lower>": { "dim": "...", "x": 0.0, "y": 64.0, "z": 0.0, "yaw": 0.0, "pitch": 0.0 }
 *   }
 * }
 */
public final class WarpManager {
    private static final WarpManager INSTANCE = new WarpManager();
    public static WarpManager get() { return INSTANCE; }

    /** Map<dimensionId, Map<warpNameLower, WarpPoint>> */
    private final Map<String, Map<String, WarpPoint>> warpsByDim = new HashMap<>();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Path file;

    private WarpManager() {}

    /**
     * Initializes storage location and loads existing data if present.
     * Call from SERVER_STARTING.
     */
    public void load(MinecraftServer server) {
        try {
            file = server.getRunDirectory().resolve("config").resolve("betterwarps.json");
            Files.createDirectories(file.getParent());

            if (!Files.exists(file)) {
                // Create empty file on first start to avoid confusion
                save(); // writes current empty map
                return;
            }

            try (Reader r = Files.newBufferedReader(file)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                warpsByDim.clear();

                for (String dim : root.keySet()) {
                    JsonObject byName = root.getAsJsonObject(dim);
                    Map<String, WarpPoint> map = new HashMap<>();
                    for (String name : byName.keySet()) {
                        JsonObject o = byName.getAsJsonObject(name);
                        map.put(name, WarpPoint.fromJson(o));
                    }
                    warpsByDim.put(dim, map);
                }
            }
        } catch (Exception e) {
            // Keep server alive, just log
            e.printStackTrace();
        }
    }

    /**
     * Serializes current state to disk. Call from SERVER_STOPPING and whenever
     * you want to force a flush after mutations (optional).
     */
    public void save() {
        try {
            JsonObject root = new JsonObject();
            for (var dimEntry : warpsByDim.entrySet()) {
                JsonObject names = new JsonObject();
                for (var nameEntry : dimEntry.getValue().entrySet()) {
                    names.add(nameEntry.getKey(), nameEntry.getValue().toJson());
                }
                root.add(dimEntry.getKey(), names);
            }
            if (file == null) {
                // Safety guard if save() is called before load()
                throw new IllegalStateException("WarpManager.save() called before load(); file path is null.");
            }
            try (Writer w = Files.newBufferedWriter(file)) {
                gson.toJson(root, w);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Creates or overwrites a warp at the player's current position, in the player's current dimension.
     * Name is normalized to lower-case.
     */
    public void setWarp(ServerPlayerEntity player, String name) {
        String normalized = normalizeName(name);
        String dim = dimId(player.getWorld().getRegistryKey());

        warpsByDim
                .computeIfAbsent(dim, k -> new HashMap<>())
                .put(normalized, WarpPoint.fromPlayerPosition(player));
    }

    /**
     * Retrieves a warp by name within a dimension. Returns null if not found.
     */
    public WarpPoint getWarp(RegistryKey<World> dimKey, String name) {
        String normalized = normalizeName(name);
        String dim = dimId(dimKey);
        Map<String, WarpPoint> map = warpsByDim.get(dim);
        return (map == null) ? null : map.get(normalized);
    }

    /**
     * Deletes a warp by name within a dimension. Returns true if a warp was removed.
     */
    public boolean delWarp(RegistryKey<World> dimKey, String name) {
        String normalized = normalizeName(name);
        String dim = dimId(dimKey);
        Map<String, WarpPoint> map = warpsByDim.get(dim);
        return map != null && map.remove(normalized) != null;
    }

    /**
     * Returns an immutable view of all warps in the provided dimension.
     * Useful for suggestions and listing.
     */
    public Map<String, WarpPoint> listWarps(RegistryKey<World> dimKey) {
        Map<String, WarpPoint> m = warpsByDim.get(dimId(dimKey));
        if (m == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(m);
    }

    /**
     * Utility: returns namespaced dimension id (e.g. "minecraft:overworld").
     */
    private static String dimId(RegistryKey<World> key) {
        Identifier id = key.getValue();
        return id.toString();
    }

    /**
     * Utility: normalize user-provided warp names (lowercase, trimmed).
     */
    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
