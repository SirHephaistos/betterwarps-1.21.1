package as.sirhephaistos;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class WarpCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetterwarps");
    // --- Suggestion provider: list of warps the user can see ---
    private static final SuggestionProvider<ServerCommandSource> WARP_NAME_SUGGESTER = (ctx, builder) -> {
        var wManager = WarpManager.get();
        var src = ctx.getSource();
        var warps = wManager.listWarps().keySet().stream()
                .filter(name -> canSeeWarp(src, name))
                .sorted()
                .toArray(String[]::new);
        return CommandSource.suggestMatching(warps, builder);
    };

    private WarpCommands() {
    }

    /**
     * Check if the source has permission to see the given warp in the warp list.
     *
     * @param src      Command source
     * @param warpName Name of the warp
     * @return true if the source has permission to see the warp
     */
    private static boolean canSeeWarp(ServerCommandSource src, String warpName) {
        return Permissions.check(src, "simplybetterwarps.see." + warpName.toLowerCase(), 3) || Permissions.check(src, "simplybetterwarps.see.*");
    }

    /**
     * Check if the source has permission to teleport to the given warp.
     *
     * @param src      Command source
     * @param warpName Name of the warp
     * @return true if the source has permission to teleport to the warp
     */
    private static boolean canTpToWarp(ServerCommandSource src, String warpName) {
        return Permissions.check(src, "simplybetterwarps.tpto." + warpName.toLowerCase(), 3) || Permissions.check(src, "simplybetterwarps.tpto.*");
    }

    /**
     * Register warp commands to the dispatcher.
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // ----- Reusable executors -----
        Command<ServerCommandSource> HELP_EXECUTOR = ctx -> {
            ctx.getSource().sendFeedback(() -> Text.literal("""
                                    [Simply Better Warps] Commands:
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

            if (!canTpToWarp(source, warpName)) {
                source.sendError(Text.literal("[Simply Better Warps] You don't have permission to teleport to '" + warpName + "'."));
                return 0;
            }

            var wManager = WarpManager.get();
            try {
                WarpPoint wp = wManager.getWarp(warpName);
                Identifier dimId = Identifier.tryParse(wp.dimensionId());
                if (dimId == null) {
                    source.sendError(Text.literal("Invalid dimension id on warp: " + wp.dimensionId()));
                    return 0;
                }
                RegistryKey<World> targetKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
                ServerWorld targetWorld = Objects.requireNonNull(player.getServer()).getWorld(targetKey);
                if (targetWorld == null) {
                    source.sendError(Text.literal("Target dimension not found on server: " + wp.dimensionId()));
                    return 0;
                }
                BlockPos targetPos = BlockPos.ofFloored(wp.x(), wp.y(), wp.z());
                ChunkPos chunkPos = new ChunkPos(targetPos);
                targetWorld.getChunkManager().addTicket(ChunkTicketType.POST_TELEPORT, chunkPos, 1, player.getId());
                targetWorld.getChunk(chunkPos.x, chunkPos.z);
                player.teleport(targetWorld, wp.x(), wp.y(), wp.z(), wp.yaw(), wp.pitch());
                source.sendFeedback(() -> Text.literal("Teleported to '" + warpName + "' in " + wp.dimensionId() + "."), false);
                return 1;
            } catch (Exception e) {
                source.sendError(Text.literal(e.getMessage()));
                LOGGER.error("Error during warp teleport:{}", e.getMessage(), e);
                return 0;
            }
        };

        Command<ServerCommandSource> SETWARP_EXECUTOR = ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayer();
            String warpName = StringArgumentType.getString(ctx, "name");
            if (p == null) {
                ctx.getSource().sendError(Text.literal("[Simply Better Warps] Only players can set warps."));
                return 0;
            }
            WarpPoint wp = WarpPoint.fromPlayerPosition(p);
            var wManager = WarpManager.get();
            wManager.setWarp(warpName, wp);
            ctx.getSource().sendFeedback(() -> Text.literal("[Simply Better Warps] Warp '%s' saved. at %s%s".formatted(
                    warpName, wp.dimensionId(), String.format(" (%.1f, %.1f, %.1f)", wp.x(), wp.y(), wp.z()))), false);
            return 1;
        };

        Command<ServerCommandSource> DELWARP_EXECUTOR = ctx -> {
            String warpName = StringArgumentType.getString(ctx, "name");
            boolean ok = WarpManager.get().delWarp(warpName);
            if (ok) {
                ctx.getSource().sendFeedback(() -> Text.literal("[Simply Better Warps] Warp deleted: " + warpName), false);
                return 1;
            } else {
                ctx.getSource().sendError(Text.literal("[Simply Better Warps] Warp not found: " + warpName));
                return 0;
            }
        };

        Command<ServerCommandSource> LIST_EXECUTOR = ctx -> {
            var src = ctx.getSource();
            var names = WarpManager.get().listWarps().keySet().stream()
                    .filter(name -> canSeeWarp(src, name))
                    .sorted()
                    .toList();

            if (names.isEmpty()) {
                src.sendFeedback(() -> Text.literal("[Simply Better Warps] Aucun warp visible."), false);
                return 1;
            }

            List<Text> clickable = new ArrayList<>();
            for (String name : names) {
                boolean canTp = canTpToWarp(src, name);

                MutableText item = Text.literal(name);

                if (canTp) {
                    item = item.styled(s -> s
                            .withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warp " + name))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Clique pour /warp " + name))));
                } else {
                    item = item.styled(s -> s
                            .withItalic(true)
                            .withColor(0x7f7f7f)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Tu n'as pas la permission de /warp " + name))));
                }

                clickable.add(item);
            }

            Text listText;
            try {
                listText = Texts.join(clickable, Text.literal(", "));
            } catch (Throwable ignored) {
                // Fallback si mappings diffèrent : construire manuellement
                MutableText tmp = Text.empty();
                for (int i = 0; i < clickable.size(); i++) {
                    if (i > 0) tmp.append(Text.literal(", "));
                    tmp.append(clickable.get(i));
                }
                listText = tmp;
            }

            Text msg = Text.literal("[Simply Better Warps] Warps (" + names.size() + "): ").append(listText);
            src.sendFeedback(() -> msg, false);
            return 1;
        };

        // /simplybetterwarps -> usage hint
        dispatcher.register(
                literal("simplybetterwarps")
                        .requires(src -> Permissions.check(src, "simplybetterwarps.basic", 1))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("[Simply Better Warps] Usage: /warp help"), false);
                            return 1;
                        })
        );

        // /warp, /warp help, /warp <name>
        dispatcher.register(
                literal("warp")
                        .requires(src -> Permissions.check(src, "simplybetterwarps.basic", 1))
                        .executes(HELP_EXECUTOR)
                        .then(literal("help")
                                .requires(src -> Permissions.check(src, "simplybetterwarps.basic", 1))
                                .executes(HELP_EXECUTOR)
                        )
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                // Permission globale de base pour utiliser /warp,
                                // le check fin par-warp est fait dans l'executor.
                                .requires(src -> Permissions.check(src, "simplybetterwarps.warpto", 1))
                                .executes(WARPTP_EXECUTOR)
                        )
        );

        // /setwarp <name>
        dispatcher.register(
                literal("setwarp")
                        .requires(src -> Permissions.check(src, "simplybetterwarps.setwarp", 1))
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .executes(SETWARP_EXECUTOR)
                        )
        );

        // /delwarp <name>
        dispatcher.register(
                literal("delwarp")
                        .requires(src -> Permissions.check(src, "simplybetterwarps.delwarp", 1))
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .executes(DELWARP_EXECUTOR)
                        )
        );

        // /warps
        dispatcher.register(
                literal("warps")
                        .requires(src -> Permissions.check(src, "simplybetterwarps.basic", 1))
                        .executes(LIST_EXECUTOR)
        );
    }
}
