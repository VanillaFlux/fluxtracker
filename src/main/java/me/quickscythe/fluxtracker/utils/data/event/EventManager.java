package me.quickscythe.fluxtracker.utils.data.event;

import me.quickscythe.fluxcore.utils.CoreUtils;
import me.quickscythe.fluxcore.api.data.StorageManager;
import me.quickscythe.fluxcore.api.data.DataManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class EventManager extends DataManager {

    private static File eventFolder;
    private final List<Event> events = new ArrayList<>();

    public EventManager() {
        super("eventdata");
        eventFolder = new File(StorageManager.getConfigFolder(), getName());

        if (!eventFolder.exists()) CoreUtils.getLoggerUtils().log("Creating event folder: " + eventFolder.mkdir());

    }

    public void reload() {
        events.clear();

        for (File file : Objects.requireNonNull(getEventFolder().listFiles())) {
            if (file.getName().endsWith(".json")) {
                String name = file.getName().replace(".json", "");
                Event event = new Event(name);
                event.reload();
                events.add(event);
            }
        }
    }

    public List<Event> getEvents() {
        return events;
    }

    public void handleJoin(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§6Checking for events..."));
        LinkedList<Text> started_messages = new LinkedList<>();
        for (Event event : events) {
            event.handleJoin(player, started_messages);
        }
        for (Text message : started_messages) {
            player.sendMessage(message);
        }
    }

    public void createEvent(String name) {
        events.add(new Event(name));
    }

    public File getEventFolder() {
        return eventFolder;
    }
}
