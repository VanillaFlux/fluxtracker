package me.quickscythe.fluxtracker.utils.data;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import json2.JSONObject;
import me.quickscythe.fluxcore.utils.CoreUtils;
import me.quickscythe.fluxcore.utils.ImageUtils;
import me.quickscythe.fluxcore.utils.data.StorageManager;
import me.quickscythe.fluxcore.utils.data.api.DataManager;
import me.quickscythe.fluxcore.utils.logger.LoggerUtils;
import net.minecraft.server.network.ServerPlayerEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class MapManager extends DataManager {
    private final File assetsFolder = new File("bluemap/web/assets/players");
    private File mapsFolder;
    private BlueMapAPI mapAPI = null;

    public MapManager() {
        super("maps");
        mapsFolder = new File(StorageManager.getConfigFolder(), getName());

        if (!assetsFolder.exists()) CoreUtils.getLoggerUtils().log("Creating assets folder: " + assetsFolder.mkdir());
        if (!mapsFolder.exists()) CoreUtils.getLoggerUtils().log("Creating maps folder: " + mapsFolder.mkdir());
        getMapAPI();
    }

    public File getMapsFolder() {
        return mapsFolder;
    }

    public File getAssetsFolder() {
        return assetsFolder;
    }

    public BlueMapAPI getMapAPI() {
        if (mapAPI == null) {
            try {
                BlueMapAPI.onEnable(api -> {
                    try {
                        MapManager mapManager = (MapManager) StorageManager.getDataManager("maps");
                        mapAPI = api;
                        CoreUtils.getLoggerUtils().log("Registering BlueMapAPI");

                        CoreUtils.getLoggerUtils().log("Checking for existing maps");
                        for (File file : Objects.requireNonNull(mapManager.getMapsFolder().listFiles())) {
                            JSONObject json = (JSONObject) StorageManager.getStorage().load(file);
                            if (api.getMap(file.getName()).isPresent())
                                api.getMap(file.getName()).get().getMarkerSets().put("offline_players", MarkerGson.INSTANCE.fromJson(json.toString(), MarkerSet.class));
                        }

                        for (BlueMapMap map : Objects.requireNonNull(getMapAPI()).getMaps()) {
                            if (map.getMarkerSets().get("offline_players") == null) {
                                MarkerSet offlinePlayers = MarkerSet.builder().defaultHidden(false).label("Offline Players").build();
                                map.getMarkerSets().put("offline_players", offlinePlayers);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                BlueMapAPI.onDisable(api -> {
                    MapManager mapManager = (MapManager) StorageManager.getDataManager("maps");
                    for (BlueMapMap map : Objects.requireNonNull(getMapAPI()).getMaps()) {
                        JSONObject json = new JSONObject(MarkerGson.INSTANCE.toJson(map.getMarkerSets().get("offline_players")));
                        System.out.println(map.getId() + ": " + json);
                        File file = new File(mapManager.getMapsFolder() + "/" + map.getId());
                        try {
                            if (!file.exists())
                                CoreUtils.getLoggerUtils().log("Creating map (" + map.getName() + ") file: " + file.createNewFile());
                            PrintWriter writer = new PrintWriter(file);
                            writer.println(json);
                            writer.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                    }
                });
            } catch (NoClassDefFoundError e) {
                return null;
            }
        }
        return mapAPI;
    }

    public void updateMapMarkers(ServerPlayerEntity player) {
        try {


            if (getMapAPI() != null)
                getMapAPI().getWorld(player.getServerWorld()).ifPresentOrElse(world -> {
                    CoreUtils.getLoggerUtils().log("Updating map markers...");

                    for (BlueMapMap map : world.getMaps()) {
                        CoreUtils.getLoggerUtils().log(map.getWorld().getId());
                        String name = player.getName().getString();
                        String uuid = player.getUuid().toString();

                        Vector3d loc = new Vector3d(player.getX(), player.getY() + 2, player.getZ());
                        POIMarker marker = new POIMarker(name, loc);
                        LocalDate date = LocalDate.now();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy MM dd");
                        String text = date.format(formatter);
                        LocalDate parsedDate = LocalDate.parse(text, formatter);
                        marker.setDetail(name + "\nLast seen: " + parsedDate);

                        marker.setLabel(name);
                        marker.setIcon("assets/players/" + uuid + ".png", 12, 12);
                        map.getMarkerSets().get("offline_players").getMarkers().put(uuid, marker);
                    }
                }, () -> CoreUtils.getLoggerUtils().log("Couldn't find map for world " + player.getWorld().toString()));
            else
                CoreUtils.getLoggerUtils().log(LoggerUtils.LogLevel.WARNING, "BlueMapAPI is null. Cannot update map markers.");

        } catch (Exception ex) {
            CoreUtils.getLoggerUtils().log(LoggerUtils.LogLevel.ERROR, "Error updating map markers: " + ex.getMessage());
            CoreUtils.getLoggerUtils().getLogger().error("Error updating map markers: ", ex);
//            ex.printStackTrace();
        }
    }

    public void readyAssets(ServerPlayerEntity player) {
        if (getMapAPI() != null) {
            generateIcon(player.getUuid());
            getMapAPI().getWorld(player.getServerWorld()).ifPresent(world -> {
                for (BlueMapMap map : world.getMaps()) {
                    map.getMarkerSets().get("offline_players").remove(player.getUuid().toString());
                }
            });
        }
    }

    public void generateIcon(UUID uuid) {
        MapManager mapManager = (MapManager) StorageManager.getDataManager("maps");
        File player_asset = new File(mapManager.getAssetsFolder() + "/" + uuid + ".png");


        try {
            final SkinProvider skinProvider = Objects.requireNonNull(getMapAPI()).getPlugin().getSkinProvider();
            try {
                final Optional<BufferedImage> oImgSkin = skinProvider.load(uuid);
                if (oImgSkin.isEmpty()) {
                    throw new IOException(uuid + " doesn't have a skin");
                }

                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(player_asset))) {
                    CoreUtils.getLoggerUtils().log("Generating avatar...");
                    final BufferedImage img = ImageUtils.resize(getMapAPI().getPlugin().getPlayerMarkerIconFactory().apply(uuid, oImgSkin.get()));
                    int width = img.getWidth();
                    int height = img.getHeight();
                    int[] pixels = img.getRGB(0, 0, width, height, null, 0, width);

                    for (int i = 0; i < pixels.length; i++) {
                        int p = pixels[i];
                        int a = (p >> 24) & 0xff;
                        int r = (p >> 16) & 0xff;
                        int g = (p >> 8) & 0xff;
                        int b = p & 0xff;

                        int avg = (r + g + b) / 3;
                        p = (a << 24) | (avg << 16) | (avg << 8) | avg;
                        pixels[i] = p;
                    }

                    img.setRGB(0, 0, width, height, pixels, 0, width);
                    ImageIO.write(img, "png", out);
                } catch (IOException e) {
                    throw new IOException("Failed to write " + uuid + "'s head to asset-storage", e);
                }
            } catch (IOException e) {
                throw new IOException("Failed to load skin for player " + uuid, e);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }


    }
}
