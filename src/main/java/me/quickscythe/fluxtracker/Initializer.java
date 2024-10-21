package me.quickscythe.fluxtracker;

import me.quickscythe.fluxcore.FluxInitializer;
import me.quickscythe.fluxcore.api.FluxEntrypoint;
import me.quickscythe.fluxcore.utils.CoreUtils;
import me.quickscythe.fluxtracker.commands.DiscordCommandManager;
import me.quickscythe.fluxtracker.commands.EventCommandManager;
import me.quickscythe.fluxtracker.commands.RegisterAltCommandManager;
import me.quickscythe.fluxtracker.server.ServerListener;
import me.quickscythe.fluxtracker.utils.Utils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class Initializer extends FluxEntrypoint {



    @Override
    public void onInitialize() {
        if (Utils.init(this)) {

            ServerListener listener = new ServerListener();
            ServerPlayConnectionEvents.JOIN.register(listener);
            ServerPlayConnectionEvents.DISCONNECT.register(listener);
            ServerLivingEntityEvents.AFTER_DEATH.register(listener);
            ServerLifecycleEvents.SERVER_STOPPING.register(listener);
            CommandRegistrationCallback.EVENT.register(new DiscordCommandManager());
            CommandRegistrationCallback.EVENT.register(new EventCommandManager());
            CommandRegistrationCallback.EVENT.register(new RegisterAltCommandManager());
        }
    }

////    @Override
//    public boolean debug() {
//        return VERSION.equalsIgnoreCase("DEBUG");
//    }
}
