package as.sirhephaistos;

import com.google.gson.JsonObject;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public record WarpPoint(String dimensionId, double x, double y, double z, float yaw, float pitch) {
    /**
     * Creates a WarpPoint from explicit parameters
     *
     * @param dimensionId Dimension identifier as a namespaced string, e.g. "minecraft:overworld".
     * @param x           Block X coordinate
     * @param y           Block Y coordinate
     * @param z           Block Z coordinate
     * @param yaw         Player yaw rotation
     * @param pitch       Player pitch rotation
     *
     */
    public WarpPoint {
    }

    /**
     * Creates a WarpPoint from a player position and rotation
     *
     */
    public static WarpPoint fromPlayerPosition(ServerPlayerEntity player) {
        RegistryKey<World> dimensionKey = player.getWorld().getRegistryKey();
        Vec3d playerPos = player.getBlockPos().toCenterPos();
        return new WarpPoint(
                dimensionKey.getValue().toString(),
                playerPos.getX(),
                playerPos.getY(),
                playerPos.getZ(),
                player.getYaw(),
                player.getPitch()
        );
    }

    /**
     * Creates a WarpPoint from a JSON Object loaded from disk.
     */
    public static WarpPoint fromJson(JsonObject obj) {
        String dimensionId = obj.get("dimension").getAsString();
        double x = obj.get("x").getAsDouble();
        double y = obj.get("y").getAsDouble();
        double z = obj.get("z").getAsDouble();
        float yaw = obj.get("yaw").getAsFloat();
        float pitch = obj.get("pitch").getAsFloat();
        return new WarpPoint(dimensionId, x, y, z, yaw, pitch);
    }

    /**
     * Converts this WarpPoint to a JSON Object suitable for saving to disk.
     */
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("dimension", this.dimensionId);
        obj.addProperty("x", this.x);
        obj.addProperty("y", this.y);
        obj.addProperty("z", this.z);
        obj.addProperty("yaw", this.yaw);
        obj.addProperty("pitch", this.pitch);
        return obj;
    }
}
