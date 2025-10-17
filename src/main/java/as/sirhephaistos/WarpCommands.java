package as.sirhephaistos;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource; // for suggestMatching
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class WarpCommands {
    private WarpCommands() {}

    // --- Suggestion provider for warp names in the current dimension ---
    private static final SuggestionProvider<ServerCommandSource> WARP_NAME_SUGGESTER = (ctx, builder) -> {
        var world = ctx.getSource().getWorld();
        if (world == null) return builder.buildFuture();
        var names = WarpManager.get().listWarps(world.getRegistryKey()).keySet();
        return CommandSource.suggestMatching(names, builder);
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // ----- Reusable executors -----
        Command<ServerCommandSource> HELP_EXECUTOR = ctx -> {
            ctx.getSource().sendFeedback(() -> Text.literal("""
                [BetterWarps] Commands:
/warp <name>      - teleport to a warp in the current dimension
/setwarp <name>   - create or overwrite a warp at your position
/delwarp <name>   - delete a warp in the current dimension
/warps            - list warps in the current dimension
/warp help        - show this help
"""), false);
            return 1;
        };

        Command<ServerCommandSource> WARPTP_EXECUTOR = ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            if (p == null) return 0;

            String warpName = StringArgumentType.getString(ctx, "name");
            RegistryKey<World> dimKey = p.getWorld().getRegistryKey();

            WarpPoint wp = WarpManager.get().getWarp(dimKey, warpName);
            if (wp == null) {
                ctx.getSource().sendError(Text.literal("Warp not found in this dimension: " + warpName));
                return 0;
            }
            var world = p.getServer().getWorld(dimKey);
            if (world == null) {
                ctx.getSource().sendError(Text.literal("World is not available: " + dimKey.getValue()));
                return 0;
            }
            p.teleport(world, wp.x, wp.y, wp.z, wp.yaw, wp.pitch);
            ctx.getSource().sendFeedback(() -> Text.literal("Teleported to '" + warpName + "'."), false);
            return 1;
        };

        Command<ServerCommandSource> SETWARP_EXECUTOR = ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            if (p == null) return 0;
            String warpName = StringArgumentType.getString(ctx, "name");
            WarpManager.get().setWarp(p, warpName);
            ctx.getSource().sendFeedback(() -> Text.literal("Warp '" + warpName + "' saved."), false);
            return 1;
        };

        Command<ServerCommandSource> DELWARP_EXECUTOR = ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            if (p == null) return 0;
            String warpName = StringArgumentType.getString(ctx, "name");
            boolean ok = WarpManager.get().delWarp(p.getWorld().getRegistryKey(), warpName);
            if (ok) {
                ctx.getSource().sendFeedback(() -> Text.literal("Warp deleted: " + warpName), false);
                return 1;
            } else {
                ctx.getSource().sendError(Text.literal("Warp not found: " + warpName));
                return 0;
            }
        };

        Command<ServerCommandSource> LIST_EXECUTOR = ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            if (p == null) return 0;
            var names = WarpManager.get().listWarps(p.getWorld().getRegistryKey()).keySet();
            ctx.getSource().sendFeedback(
                    () -> Text.literal("Warps (" + names.size() + "): " + String.join(", ", names)),
                    false
            );
            return 1;
        };

        // /betterwarps -> usage hint
        dispatcher.register(
                literal("betterwarps")
                        .requires(src -> Permissions.check(src, "betterwarps-basic", 0))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("Usage: /warp help"), false);
                            return 1;
                        })
        );

        // /warp, /warp help, /warp <name> (with suggestions)
        dispatcher.register(
                literal("warp")
                        .requires(src -> Permissions.check(src, "betterwarps-basic", 0))
                        .executes(HELP_EXECUTOR)
                        .then(literal("help")
                                .requires(src -> Permissions.check(src, "betterwarps-basic", 0))
                                .executes(HELP_EXECUTOR)
                        )
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER) // <--- suggestions here
                                .requires(src -> Permissions.check(src, "betterwarps-warpto", 0))
                                .executes(WARPTP_EXECUTOR)
                        )
        );

        // /setwarp <name> (no suggestions by default; names are user-defined)
        dispatcher.register(
                literal("setwarp")
                        .requires(src -> Permissions.check(src, "betterwarps-setwarp", 2))
                        .then(argument("name", StringArgumentType.word())
                                .executes(SETWARP_EXECUTOR)
                        )
        );

        // /delwarp <name> (with suggestions)
        dispatcher.register(
                literal("delwarp")
                        .requires(src -> Permissions.check(src, "betterwarps-delwarp", 2))
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER) // <--- suggestions here
                                .executes(DELWARP_EXECUTOR)
                        )
        );

        // /warps
        dispatcher.register(
                literal("warps")
                        .requires(src -> Permissions.check(src, "betterwarps-basic", 0))
                        .executes(LIST_EXECUTOR)
        );
    }
}
