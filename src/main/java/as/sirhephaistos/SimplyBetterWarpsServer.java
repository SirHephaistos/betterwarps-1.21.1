package as.sirhephaistos;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimplyBetterWarpsServer implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetter-warps");

    @Override
    public void onInitializeServer() {
        LOGGER.info("[Simply Better Warps] Initializing Simply Better Warps");
        // load warps on server start
        ServerLifecycleEvents.SERVER_STARTING.register(server -> WarpManager.get().load());
        // save warps on server stop
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> WarpManager.get().save());

        LOGGER.info("[Simply Better Warps] Registering commands");
        //command registration callback
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                WarpCommands.register(dispatcher)
        );
        LOGGER.info("[Simply Better Warps] Commands registered");
    }
}
