package me.quickscythe.fluxtracker.utils.data.event;

import json2.JSONArray;
import json2.JSONObject;
import me.quickscythe.fluxcore.utils.ApiManager;
import me.quickscythe.fluxcore.utils.CoreUtils;
import me.quickscythe.fluxcore.utils.data.AccountManager;
import me.quickscythe.fluxcore.utils.data.StorageManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static net.minecraft.text.Text.literal;

public class Event {
    /*TODO
     * 1) When add player, assign role *
     * 2) When remove player, remove role *
     * 3) When stop, remove role before deleting *
     * 4) Fix remove(Player) method *
     */

    private final List<UUID> players = new ArrayList<>();
    private final String name;
    private JSONObject data;
    private Status status;
    private TextColor color = TextColor.fromRgb(Color.WHITE.getRGB());


    public Event(String name) {
        this.name = name;
        reload();
        save();
    }

    public void reload() {
        StorageManager.getStorage().load("eventdata." + name);
        if (StorageManager.getStorage().get("eventdata." + name) != null) {
            JSONObject json = StorageManager.getStorage().getJsonObject("eventdata." + name);
            this.status = Status.valueOf(json.getString("status"));
            JSONArray players = json.getJSONArray("players");
            for (int i = 0; i < players.length(); i++) {
                this.players.add(UUID.fromString(players.getString(i)));
            }
            this.data = json.getJSONObject("data");
            if (data.has("color")) {
                color(TextColor.fromRgb(data.getInt("color")));
            }
        } else {
            this.status = Status.INIT;
            this.data = new JSONObject();
        }
    }

    public String name() {
        return name;
    }

    public void add(ServerPlayerEntity player) {
        if (status().equals(Status.OPT_IN)) {
            if (players.contains(player.getUuid())) {
                player.sendMessage(literal("§cYou're already in this event."));
                return;
            }
            player.sendMessage(literal("§eYou've joined the event: ").append(formatName()));
//            player.sendMessage(Text.translatable("events.core.started2", getName()));
            if (data.has("rules")) {
                rules(player);
            }
            players.add(player.getUuid());
            AccountManager accountManager = (AccountManager) StorageManager.getDataManager("playerdata");
            ApiManager.appData("assign_role?a=" + data().getString("role") + "&b=" + accountManager.getData(player.getUuid()).getString("discord_id"));
            save();
        } else {
            player.sendMessage(literal("§cYou can't join this event right now."));
        }
    }

    public void remove(ServerPlayerEntity player) {
        if (players.contains(player.getUuid())) {
            players.remove(player.getUuid());
            AccountManager accountManager = (AccountManager) StorageManager.getDataManager("playerdata");
            ApiManager.appData("remove_role?a=" + data().getString("role") + "&b=" + accountManager.getData(player.getUuid()).getString("discord_id"));
            player.sendMessage(literal("§bYou've left the event: ").append(formatName()));
            save();
        } else {
            player.sendMessage(literal("§cYou're not in this event."));
        }
    }

    public JSONArray rules() {
        if (data.has("rules")) {
            if (data.get("rules") instanceof String) {
                JSONArray rules = new JSONArray();
                rules.put(data.getString("rules"));
                data.put("rules", rules);
            }

        } else {
            data.put("rules", new JSONArray());
        }
        return data.getJSONArray("rules");
    }

    public void rules(ServerPlayerEntity player) {
        LinkedList<Text> messages = new LinkedList<>();
        rules(messages);
        for (Text message : messages) {
            player.sendMessage(message);
        }
    }

    public void rules(LinkedList<Text> messages) {
        String prefix = "§e Event rules:";
        if (data.get("rules") instanceof String)
            messages.add(literal(prefix + " ").append(literal(data.getString("rules"))));
        else if (data.get("rules") instanceof JSONArray) {
            JSONArray rules = data.getJSONArray("rules");
            messages.add(literal(prefix));
            for (int i = 0; i < rules.length(); i++) {
                messages.add(literal("§e - ").append(literal(rules.getString(i))));
            }
        }
    }

    public List<UUID> players() {
        return players;
    }

    public void start(MinecraftServer server) {

        for (UUID uid : players) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uid);
            if (player != null) player.sendMessage(literal("§bThe event has started: ").append(formatName()));
        }
        status(Status.STARTED);
    }


    public void optIn(MinecraftServer server) {
        for (UUID uid : players) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uid);
            if (player != null)
                player.sendMessage(literal("§eThe opt-in period for ").append(formatName()).append(literal(" §ehas started.")));
        }
        status(Status.OPT_IN);
    }

    public void stop(MinecraftServer server) {
        for (UUID uid : players) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uid);
            if (player != null) player.sendMessage(literal("§bThe event has ended: ").append(formatName()));
        }
        status(Status.STOPPED);
    }

    public Status status() {
        return status;
    }

    public void status(Status status) {
        this.status = status;
        switch (status) {
            case OPT_IN:
                ApiManager.checkToken();
                String result = ApiManager.appData("create_role?a=" + name + "&b=" + color().getRgb());
                if (!result.contains("error")) {
                    data().put("role", new JSONObject(result).getString("success"));
                }
                break;
            case STOPPED:
                AccountManager accountManager = (AccountManager) StorageManager.getDataManager("playerdata");
                for (UUID uid : players) {
                    ApiManager.appData("remove_role?a=" + data().getString("role") + "&b=" + accountManager.getData(uid).getString("discord_id"));
                }
                players.clear();
                ApiManager.appData("delete_role?a=" + data().getString("role"));
                break;
        }
        save();
    }

    public JSONObject json() {
        JSONObject json = new JSONObject();
        json.put("status", status);

        JSONArray players = new JSONArray();
        this.players.forEach(players::put);
        json.put("players", players);
        if (!data.has("color")) {
            data.put("color", color.getRgb());
        }
        json.put("data", data);
        json.put("name", name);
        return json;
    }

    public void save() {
        EventManager eventManager = (EventManager) StorageManager.getDataManager("eventdata");
        File file = new File(eventManager.getEventFolder(), name() + ".json");
        try {
            if (!file.exists())
                CoreUtils.getLoggerUtils().log("Creating event file (" + file.getName() + "): " + file.createNewFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        StorageManager.getStorage().set("eventdata." + name(), json());
        StorageManager.getStorage().saveAndRemove("eventdata." + name());
    }

    public JSONObject data() {
        return data;
    }


    private Text formatName() {
        return literal(name()).styled(style -> style.withColor(TextColor.fromRgb(color().getRgb())));
    }


    public void handleJoin(ServerPlayerEntity player, LinkedList<Text> started_messages) {
        //todo manage messages via config
        if (status() == Status.STARTED) {
            if (players().contains(player.getUuid())) {
                started_messages.add(Text.literal("§c------[§4§lEvent§c]------"));
                started_messages.add(Text.literal("§cThe ").append(formatName()).append(literal(" event is currently live!")));
                if (data().has("rules")) {
                    rules(started_messages);
                }
                StringBuilder players = new StringBuilder();
                AccountManager accountManager = (AccountManager) StorageManager.getDataManager("playerdata");
                for (int i = 0; i < players().size(); i++) {
                    players.append(accountManager.getUsername(players().get(i)));
                    if (i < players().size() - 1) {
                        players.append(", ");
                    }
                }
                players.append(".");
                started_messages.add(Text.literal("§cParticipating players: " + players));
                started_messages.add(Text.literal("§c------------------"));
            }
        }
        if (status() == Status.OPT_IN) {
            //Last call to opt in: %s
            player.sendMessage(Text.literal("§e-----------------"));
            if (players().contains(player.getUuid())) {
                player.sendMessage(Text.literal("§eReminder: You have opted in to the event: ").append(formatName()));
            } else {
                player.sendMessage(Text.literal("§eReminder: You have not yet opt in to ").append(formatName())); //You have not opted in to the event: %s
            }
            if (data().has("opt_in_last_call")) {
                player.sendMessage(Text.literal("§eLast call to opt in: §f" + data().getString("opt_in_last_call")));
            }
            player.sendMessage(Text.literal("§e-----------------"));
        }
    }

    public TextColor color() {
        return color;
    }

    public void color(TextColor color) {
        this.color = color;
        if (data().has("role"))
            ApiManager.appData("update_role?a=" + data().getString("role") + "&b=color&c=" + color.getRgb());
        save();
    }


    public enum Status {
        INIT, STARTED, OPT_IN, STOPPED
    }

//    public static class Data extends JSONObject {
//
//        public Data() {
//            super();
//        }
//
//        public Data(JSONObject data) {
//            super(data);
//            System.out.println(this.toString(2));
//        }
//    }
}
