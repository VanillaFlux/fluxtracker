package me.quickscythe.fluxtracker.server;

import me.quickscythe.fluxcore.utils.CoreUtils;
import me.quickscythe.fluxcore.api.data.AccountManager;
import me.quickscythe.fluxcore.api.data.StorageManager;
import me.quickscythe.fluxtracker.utils.data.MapManager;
import me.quickscythe.fluxtracker.utils.data.SessionUtils;
import me.quickscythe.fluxtracker.utils.data.event.Event;
import me.quickscythe.fluxtracker.utils.data.event.EventManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;

public class ServerListener implements ServerPlayConnectionEvents.Join, ServerPlayConnectionEvents.Disconnect, ServerLivingEntityEvents.AfterDeath, ServerLifecycleEvents.ServerStopping {

    @Override
    public void onPlayDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        AccountManager accountManager = (AccountManager) StorageManager.getDataManager("playerdata");
        if (accountManager.isAltOrShadow(handler.getPlayer())) {
            return;
        }
        CoreUtils.getLoggerUtils().log("Player disconnected: " + handler.player.getName().getString());
        SessionUtils.saveSession(handler.player);
        ((MapManager) StorageManager.getDataManager("maps")).updateMapMarkers(handler.getPlayer());
    }

    @Override
    public void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        if(((AccountManager) StorageManager.getDataManager("playerdata")).isAltOrShadow(handler.player)){
            return;
        }
        SessionUtils.startSession(handler.getPlayer());
        ((MapManager) StorageManager.getDataManager("maps")).readyAssets(handler.getPlayer());
        ((EventManager) StorageManager.getDataManager("eventdata")).handleJoin(handler.player);
    }

    @Override
    public void afterDeath(LivingEntity entity, DamageSource damageSource) {
        SessionUtils.handleDeath(entity, damageSource);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        for(Event event : ((EventManager) StorageManager.getDataManager("eventdata")).getEvents())
            event.save();
    }
}
