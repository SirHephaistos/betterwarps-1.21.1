package as.sirhephaistos;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class BetterWarpsServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        // load warps on server start
        ServerLifecycleEvents.SERVER_STARTING.register(server -> WarpManager.get().load(server));

        // save warps on server stop
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> WarpManager.get().save());

        //command registration callback
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                WarpCommands.register(dispatcher)
        );
    }
}
