package me.quickscythe.fluxtracker.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.quickscythe.fluxcore.utils.NamedTextColor;
import me.quickscythe.fluxcore.api.data.InternalStorage;
import me.quickscythe.fluxcore.api.data.StorageManager;
import me.quickscythe.fluxtracker.utils.data.event.Event;
import me.quickscythe.fluxtracker.utils.data.event.EventManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class RegisterAltCommandManager implements CommandRegistrationCallback {

    public GameProfile getGameProfileByUsername(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (connection.getResponseCode() == 200) {
                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String id = json.get("id").getAsString();
                String name = json.get("name").getAsString();
                UUID uuid = UUID.fromString(id.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                        "$1-$2-$3-$4-$5"
                ));
                return new GameProfile(uuid, name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }



    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("register-alt").executes(context -> {
            ServerCommandSource source = context.getSource();
            source.sendError(Text.literal("Invalid arguments. Usage: /register-alt <username>"));
            return 0;
        }).then(argument("alt", StringArgumentType.word()).executes(context -> {
            ServerCommandSource source = context.getSource();
            String username = StringArgumentType.getString(context, "alt");
            InternalStorage storage = StorageManager.getStorage();
//            YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(source.getServer().getProxy());

            if(storage.has("data.alts." + source.getPlayerOrThrow().getUuid())){
                GameProfile alt = getGameProfileByUsername(storage.getString("data.alts." + source.getPlayerOrThrow().getUuid()));
                source.getServer().getPlayerManager().getWhitelist().remove(alt);
            }
            GameProfile profile = getGameProfileByUsername(username);
            source.getServer().getPlayerManager().getWhitelist().add(new WhitelistEntry(profile));
            storage.set("data.alts." + source.getPlayerOrThrow().getUuid(), username);
            storage.saveAndRemove("data.alts");

            return 1;
        }).suggests((context, builder) -> {
            ServerCommandSource source = context.getSource();
            List<String> actions = new java.util.ArrayList<>(List.of("start", "optin", "join", "leave", "stop", "reload", "create", "delete", "edit"));
            if (!source.hasPermissionLevel(4)) {
                actions.remove("start");
                actions.remove("stop");
                actions.remove("optin");
                actions.remove("reload");
                actions.remove("create");
                actions.remove("delete");
                actions.remove("edit");
            }
            return CommandSource.suggestMatching(actions, builder);
        }).then(argument("event", StringArgumentType.word()).executes(context -> {
            ServerCommandSource source = context.getSource();
            String action = StringArgumentType.getString(context, "action");
            EventManager eventManager = (EventManager) StorageManager.getDataManager("eventdata");

            if (action.equalsIgnoreCase("create")) {
                eventManager.createEvent(StringArgumentType.getString(context, "event"));
                return 1;
            }
            Event event = eventManager.getEvents().stream().filter(e -> e.name().equalsIgnoreCase(StringArgumentType.getString(context, "event"))).findFirst().orElse(null);
            if (event == null) {
                source.sendError(Text.literal("Invalid event."));
                return 1;
            }

            if (action.equalsIgnoreCase("start")) {
                event.start(context.getSource().getServer());
            }
            if (action.equalsIgnoreCase("optin")) {
                event.optIn(context.getSource().getServer());
            }
            if (action.equalsIgnoreCase("join")) {
                event.add(source.getPlayerOrThrow());
            }
            if (action.equalsIgnoreCase("leave")) {
                event.remove(source.getPlayerOrThrow());
            }
            if (action.equalsIgnoreCase("stop")) {
                event.stop(source.getServer());
            }
            if (action.equalsIgnoreCase("reload")) {
                event.reload();
            }
            if (action.equalsIgnoreCase("delete")) {
                eventManager.getEvents().remove(event);
                source.sendMessage(Text.literal("Event deleted."));
            }
            if (action.equalsIgnoreCase("edit")) {
                source.sendError(Text.literal("Not implemented yet."));
            }

            return 1;
        }).suggests((context, builder) -> {
            ServerCommandSource source = context.getSource();
            EventManager eventManager = (EventManager) StorageManager.getDataManager("eventdata");
            List<String> events = eventManager.getEvents().stream().filter(e -> e.status().equals(Event.Status.OPT_IN) || source.hasPermissionLevel(4)).map(Event::name).collect(Collectors.toList());
            return CommandSource.suggestMatching(events, builder);
        }).then(argument("option", StringArgumentType.word())
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    String action = StringArgumentType.getString(context, "action");
                    if (action.equalsIgnoreCase("edit")) {
                        source.sendError(Text.literal("Usage: /event edit <event> <option> <value>"));
                    }
                    return 1;
                }).suggests((context, builder) -> {
                    ServerCommandSource source = context.getSource();
//                    List<String> players = source.getServer().getPlayerManager().getPlayerList().stream().map(player -> player.getName().getString()).collect(Collectors.toList());
                    return CommandSource.suggestMatching(List.of("color", "rules"), builder);
                }).then(argument("value", StringArgumentType.greedyString()).executes(context -> {
                    EventManager eventManager = (EventManager) StorageManager.getDataManager("eventdata");
                    ServerCommandSource source = context.getSource();
                    String action = StringArgumentType.getString(context, "action");
                    String option = StringArgumentType.getString(context, "option");
                    String value = StringArgumentType.getString(context, "value");
                    if (action.equalsIgnoreCase("edit")) {
                        Event event = eventManager.getEvents().stream().filter(e -> e.name().equalsIgnoreCase(StringArgumentType.getString(context, "event"))).findFirst().orElse(null);
                        if (event == null) {
                            source.sendError(Text.literal("Invalid event."));
                            return 1;
                        }
                        if (option.equalsIgnoreCase("color")) {
                            TextColor color;
                            try {
                                color = TextColor.fromRgb(Integer.parseInt(value, 16));
                            } catch (NumberFormatException e) {
                                color = NamedTextColor.valueOf(value.toUpperCase()).color();

                            }
                            event.color(color);
                            TextColor finalColor = color;
                            source.sendMessage(Text.literal("Event color set to ").append(Text.literal(value).styled(style -> style.withColor(finalColor.getRgb()))));
                        }
                        if (option.equalsIgnoreCase("rules")) {
                            if (value.startsWith("+")) {
                                event.rules().put(value.substring(1));
                            }
                            if (value.startsWith("-")) {
                                event.rules().remove(Integer.parseInt(value.substring(1)));
                            }
                            event.rules(source.getPlayerOrThrow());
                        }
                    }
                    return 1;
                }))))));

    }
}
