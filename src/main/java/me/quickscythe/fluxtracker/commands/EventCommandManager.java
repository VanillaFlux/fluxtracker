package me.quickscythe.fluxtracker.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.quickscythe.fluxcore.utils.NamedTextColor;
import me.quickscythe.fluxcore.utils.data.StorageManager;
import me.quickscythe.fluxtracker.utils.data.event.Event;
import me.quickscythe.fluxtracker.utils.data.event.EventManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EventCommandManager implements CommandRegistrationCallback {
    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("event").executes(context -> {
            ServerCommandSource source = context.getSource();
            source.sendError(Text.literal("Invalid arguments. Usage: /event <action> <event>"));
            return 0;
        }).then(argument("action", StringArgumentType.word()).executes(context -> {
            ServerCommandSource source = context.getSource();
            String action = StringArgumentType.getString(context, "action");
            if (action.equalsIgnoreCase("reload")) {
                EventManager eventManager = (EventManager) StorageManager.getDataManager("eventdata");
                eventManager.reload();
                source.sendMessage(Text.literal("Events reloaded."));
                return 1;
            }
            if (action.equalsIgnoreCase("create")) {
                source.sendError(Text.literal("Usage: /event create <event>"));
//                source.sendError(Text.literal("Not implemented yet."));
                return 1;
            }
            source.sendError(Text.literal("Invalid arguments. Usage: /event <action> <event>"));
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
                    if(action.equalsIgnoreCase("edit")){
                        source.sendError(Text.literal("Usage: /event edit <event> <option> <value>"));
                    }
                    return 1;
                }).suggests((context, builder) -> {
                    ServerCommandSource source = context.getSource();
//                    List<String> players = source.getServer().getPlayerManager().getPlayerList().stream().map(player -> player.getName().getString()).collect(Collectors.toList());
                    return CommandSource.suggestMatching(List.of("color","rules"), builder);
                }).then(argument("value", StringArgumentType.greedyString()).executes(context -> {
                    ServerCommandSource source = context.getSource();
                    String action = StringArgumentType.getString(context, "action");
                    String option = StringArgumentType.getString(context, "option");
                    String value = StringArgumentType.getString(context, "value");
                    EventManager eventManager = (EventManager) StorageManager.getDataManager("eventdata");
                    if(action.equalsIgnoreCase("edit")){
                        Event event = eventManager.getEvents().stream().filter(e -> e.name().equalsIgnoreCase(StringArgumentType.getString(context, "event"))).findFirst().orElse(null);
                        if(event == null){
                            source.sendError(Text.literal("Invalid event."));
                            return 1;
                        }
                        if(option.equalsIgnoreCase("color")){
                            TextColor color;
                            try{
                                color = TextColor.fromRgb(Integer.parseInt(value, 16));
                            }catch (NumberFormatException e){
                                color = NamedTextColor.valueOf(value.toUpperCase()).color();

                            }
                            event.color(color);
                            TextColor finalColor = color;
                            source.sendMessage(Text.literal("Event color set to ").append(Text.literal(value).styled(style -> style.withColor(finalColor.getRgb()))));
                        }
                        if(option.equalsIgnoreCase("rules")){
                            if(value.startsWith("+")){
                                event.rules().put(value.substring(1));
                            }
                            if(value.startsWith("-")){
                                event.rules().remove(Integer.parseInt(value.substring(1)));
                            }
                            event.rules(source.getPlayerOrThrow());
                        }
                    }
                    return 1;
                }))))));

    }
}
