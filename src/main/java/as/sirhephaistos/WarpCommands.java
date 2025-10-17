package as.sirhephaistos;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource; // for suggestMatching
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class WarpCommands {
    private WarpCommands() {}
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetterwarps");


    // --- Suggestion provider for warp names in the current dimension ---
    private static final SuggestionProvider<ServerCommandSource> WARP_NAME_SUGGESTER= (ctx, builder) -> {
        var wManager = WarpManager.get();
        var warps = wManager.listWarps().keySet().toArray(new String[0]);
        return CommandSource.suggestMatching(warps, builder);
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // ----- Reusable executors -----
        Command<ServerCommandSource> HELP_EXECUTOR = ctx -> {
            ctx.getSource().sendFeedback(() -> Text.literal("""
                [Simplybetterwarps] Commands:
/warp <name>      - teleport to a warp in the current dimension
/setwarp <name>   - create or overwrite a warp at your position
/delwarp <name>   - delete a warp in the current dimension
/warps            - list warps in the current dimension
/warp help        - show this help
"""), false);
            return 1;
        };
        Command<ServerCommandSource> WARPTP_EXECUTOR = ctx -> {
            var source = ctx.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendError(Text.literal("Only players can use warps."));
                return 0;
            }
            String warpName = StringArgumentType.getString(ctx, "name");
            var wManager = WarpManager.get();
            try{
                WarpPoint wp = wManager.getWarp(warpName);
                // Resolve target dimension from the warp itself
                Identifier dimId = Identifier.tryParse(wp.dimensionId);
                if (dimId == null) {
                    source.sendError(Text.literal("Invalid dimension id on warp: " + wp.dimensionId));
                    return 0;
                }
                RegistryKey<World> targetKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
                ServerWorld targetWorld = player.getServer().getWorld(targetKey);
                if (targetWorld == null) {
                    source.sendError(Text.literal("Target dimension not found on server: " + wp.dimensionId));
                    return 0;
                }
                BlockPos targetPos = BlockPos.ofFloored(wp.x, wp.y, wp.z);
                ChunkPos chunkPos = new ChunkPos(targetPos);
                // Load chunk ticket to ensure the chunk is loaded
                targetWorld.getChunkManager().addTicket(ChunkTicketType.POST_TELEPORT, chunkPos, 1, player.getId());
                // Force load synchronously to FULL status (guard against edge cases)
                targetWorld.getChunk(chunkPos.x, chunkPos.z);
                player.teleport(targetWorld, wp.x, wp.y, wp.z, wp.yaw, wp.pitch);
                source.sendFeedback(() -> Text.literal("Teleported to '" + warpName + "' in " + wp.dimensionId + "."), false);
                return 1;
            }catch (Exception e){
                source.sendError(Text.literal(e.getMessage()));
                LOGGER.error("Error during warp teleport:{}", e.getMessage(), e);
                return 0;
            }
        };
        Command<ServerCommandSource> SETWARP_EXECUTOR = ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            String warpName = StringArgumentType.getString(ctx, "name");
            //if not a player, send error feedback
            if (p == null) {
                ctx.getSource().sendError(Text.literal("[Simplybetterwarps] Only players can set warps."));
                return 0;
            }
            WarpPoint wp = WarpPoint.fromPlayerPosition(p);
            var wManager = WarpManager.get();
            wManager.setWarp(warpName, wp);
            ctx.getSource().sendFeedback(() -> Text.literal("[Simplybetterwarps] Warp '%s' saved. at %s%s".formatted(warpName, wp.dimensionId, String.format(" (%.1f, %.1f, %.1f)", wp.x, wp.y, wp.z))), false);
            return 1;
        };
        Command<ServerCommandSource> DELWARP_EXECUTOR = ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            String warpName = StringArgumentType.getString(ctx, "name");
            WarpPoint wp = WarpPoint.fromPlayerPosition(p);
            var wManager = WarpManager.get();
            boolean ok = WarpManager.get().delWarp(warpName);
            if (ok) {
                ctx.getSource().sendFeedback(() -> Text.literal("[Simplybetterwarps] Warp deleted: " + warpName), false);
                return 1;
            } else {
                ctx.getSource().sendError(Text.literal("[Simplybetterwarps] Warp not found: " + warpName));
                return 0;
            }
        };
        Command<ServerCommandSource> LIST_EXECUTOR = ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            var wManager = WarpManager.get();
            var names = WarpManager.get().listWarps().keySet().stream().sorted().toList();
            ctx.getSource().sendFeedback(
                    () -> Text.literal("Warps (" + names.size() + "): " + String.join(", ", names)),
                    false
            );
            return 1;
        };
        // /simplybetterwarps -> usage hint
        dispatcher.register(
                literal("simplybetterwarps")
                        .requires(src -> Permissions.check(src, "simplybetterwarps-basic", 0))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("[Simplybetterwarps] Usage: /warp help"), false);
                            return 1;
                        })
        );
        // /warp, /warp help, /warp <name> (with suggestions)
        dispatcher.register(
                literal("warp")
                        .requires(src -> Permissions.check(src, "simplybetterwarps-basic", 0))
                        .executes(HELP_EXECUTOR)
                        .then(literal("help")
                                .requires(src -> Permissions.check(src, "simplybetterwarps-basic", 0))
                                .executes(HELP_EXECUTOR)
                        )
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .requires(src -> Permissions.check(src, "simplybetterwarps-warpto", 0))
                                .executes(WARPTP_EXECUTOR)
                        )
        );
        // /setwarp <name> (no suggestions by default; names are user-defined)
        dispatcher.register(
                literal("setwarp")
                        .requires(src -> Permissions.check(src, "simplybetterwarps-setwarp", 2))
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .executes(SETWARP_EXECUTOR)
                        )
        );

        // /delwarp <name> (with suggestions)
        dispatcher.register(
                literal("delwarp")
                        .requires(src -> Permissions.check(src, "simplybetterwarps-delwarp", 2))
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .executes(DELWARP_EXECUTOR)
                        )
        );
        // /warps
        dispatcher.register(
                literal("warps")
                        .requires(src -> Permissions.check(src, "simplybetterwarps-basic", 0))
                        .executes(LIST_EXECUTOR)
        );
    }
}
