package me.quickscythe.fluxtracker.utils;

import me.quickscythe.fluxcore.utils.CoreUtils;
import me.quickscythe.fluxcore.utils.data.StorageManager;
import me.quickscythe.fluxtracker.Initializer;
import me.quickscythe.fluxtracker.utils.data.event.EventManager;

public class Utils {
   private static Initializer mod;



    public static boolean init(Initializer mod) {
        try {
            Utils.mod = mod;

            StorageManager.registerDataManager(new EventManager());

        } catch (Exception e) {
            CoreUtils.getLoggerUtils().log("Error initializing " + mod.NAME + ": " + e.getMessage());
            throw new RuntimeException(e);
//            return false;
        }
        return true;
    }




    public static Initializer getInitializer() {
        return mod;
    }


    //config files
}