package me.quickscythe.fluxtracker.utils;

import me.quickscythe.fluxcore.api.FluxEntrypoint;
import me.quickscythe.fluxcore.utils.CoreUtils;
import me.quickscythe.fluxcore.api.data.StorageManager;
import me.quickscythe.fluxtracker.utils.data.event.EventManager;
import me.quickscythe.fluxcore.FluxInitializer;

public class Utils {
   private static FluxEntrypoint entrypoint;



    public static boolean init(FluxEntrypoint entrypoint) {
        try {
            Utils.entrypoint = entrypoint;

            StorageManager.registerDataManager(new EventManager());

        } catch (Exception e) {
            CoreUtils.getLoggerUtils().log("Error initializing " + entrypoint.getMod().getName() + ": " + e.getMessage());
            throw new RuntimeException(e);
//            return false;
        }
        return true;
    }




    public static FluxEntrypoint getEntrypoint() {
        return entrypoint;
    }


    //config files
}