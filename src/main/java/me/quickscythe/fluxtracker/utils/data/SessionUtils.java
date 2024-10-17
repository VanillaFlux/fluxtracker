package me.quickscythe.fluxtracker.utils.data;

import json2.JSONArray;
import json2.JSONObject;
import me.quickscythe.fluxcore.utils.data.AccountManager;
import me.quickscythe.fluxcore.utils.data.StorageManager;
import me.quickscythe.fluxcore.utils.sql.SqlUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionUtils {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static Session getSession(UUID uid) {
        if (!SESSIONS.containsKey(uid)) SESSIONS.put(uid, new Session());
        return SESSIONS.get(uid);
    }

    public static void clearSession(UUID uid){
        SESSIONS.remove(uid);
    }

    public static void saveSession(ServerPlayerEntity player) {
        AccountManager accountManager = (AccountManager) StorageManager.getDataManager("playerdata");
        JSONObject json = accountManager.getData(player.getUuid());
        JSONArray session_list = json.has("sessions") ? json.getJSONArray("sessions") : json.put("sessions", new JSONArray()).getJSONArray("sessions");
        Session session_info = SessionUtils.getSession(player.getUuid());
        session_info.put("time_left", new Date().getTime());
        session_info.put("playtime", session_info.getLong("time_left") - session_info.getLong("time_joined"));

        session_info.put("jumps", player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP)) - session_info.getLong("jumps_start"));
        session_info.remove("jumps_start");

        session_list.put(session_info);
        json.put("sessions", session_list);

        accountManager.save(player, json);
        clearSession(player.getUuid());
    }

    public static void startSession(ServerPlayerEntity player) {
        AccountManager accountManager = (AccountManager) StorageManager.getDataManager("playerdata");
        if (!new File(accountManager.getPlayerFolder(), player.getUuid().toString() + ".json").exists()) {
            String sql = "INSERT INTO users(uuid,username,discord_key,discord_id,password,last_seen,json) VALUES ('" + player.getUuid() + "','" + player.getName().toString().substring(8, player.getName().toString().length() - 1) + "','null','null','null','" + new Date().getTime() + "','{}');";
            SqlUtils.getDatabase("core").input(sql);
        }
        Session session = SessionUtils.getSession(player.getUuid());
        session.put("time_joined", new Date().getTime());
        session.put("jumps_start", player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP)));
    }

    public static void handleDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity instanceof PlayerEntity dier) {
            Session session = SessionUtils.getSession(dier.getUuid());
            session.put("deaths", session.has("deaths") ? session.getInt("deaths") + 1 : 1);
            JSONObject killed_by = session.getJSONObject("killed_by");
            String key;
            if (damageSource.getAttacker() != null) {
                key = formatName(damageSource.getAttacker());
            } else {
                if (damageSource.getTypeRegistryEntry().equals(DamageTypes.EXPLOSION)) {
                    key = "Explosion";
                } else {
                    key = damageSource.getName();
                }
            }
            System.out.println("Killed by: " + key);
            killed_by.put(key, killed_by.has(key) ? killed_by.getInt(key) + 1 : 1);
        }
        if (damageSource.getAttacker() instanceof PlayerEntity attacker) {
            Session session = SessionUtils.getSession(attacker.getUuid());
            JSONObject kills = session.getJSONObject("kills");
            String key = formatName(entity);
            kills.put(key, kills.has(key) ? kills.getInt(key) + 1 : 1);
        }
    }

    private static String formatName(Entity entity) {
        return entity.getType().equals(EntityType.PLAYER)
                ? entity.getName().getString()
                : (entity.getType().getName().getString().equals(entity.getName().getString())
                ? entity.getName().getString()
                : entity.getName().getString() + "[" + entity.getType().getName().getString() + "]");
    }

//    public static JSONObject getJson(String key, JSONObject json) {
//        return json.has(key) ? json.getJSONObject(key) : json.put(key, new JSONObject()).getJSONObject(key);
//    }

//    public static void addInt(String key, int i, JSONObject json) {
//        json.put(key, json.has(key) ? json.getInt(key) + 1 : 1);
//    }

    public static class Session extends JSONObject {

        public Session() {
            super();
        }


        @Override
        public JSONObject getJSONObject(String key) {
            return
                    super.has(key)
                            ? super.getJSONObject(key)
                            : super.put(key, new JSONObject())
                                .getJSONObject(key);
        }

    }
}
