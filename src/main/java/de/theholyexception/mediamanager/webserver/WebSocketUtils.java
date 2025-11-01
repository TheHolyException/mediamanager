package de.theholyexception.mediamanager.webserver;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.api.WebServer;
import de.theholyexception.mediamanager.models.DownloadTask;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.util.TargetSystem;
import de.theholyexception.mediamanager.util.Utils;
import io.javalin.websocket.WsContext;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.tomlj.TomlParseResult;

import java.nio.channels.ClosedChannelException;
import java.util.*;

@Slf4j
public class WebSocketUtils {
    private WebSocketUtils() {}

    private static Map<String, JSONObject> packetBuffer;
    private static Map<WsContext, Map<String, JSONObject>> directPacketBuffer;
    private static List<String> deleteBuffer = new ArrayList<>();

    public static void initialize(TomlParseResult config) {

        packetBuffer = new HashMap<>();
        directPacketBuffer = new HashMap<>();

        if (Boolean.TRUE.equals(config.getBoolean("general.enablePacketBuffer", () -> true))) {Thread packetThread = new Thread(() -> {
            while (true) {
                try {
                    Utils.sleep(config.getLong("general.packetBufferSleep", () -> 1000));
                    synchronized (packetBuffer) {
                        if (!packetBuffer.isEmpty()) {
                            JSONObject response = new JSONObject();
                            JSONArray dataset = new JSONArray();
                            for (Map.Entry<String, JSONObject> entry : packetBuffer.entrySet()) {
                                dataset.add(entry.getValue());
                            }
                            response.put("data", dataset);
                            sendPacket("syn", TargetSystem.DEFAULT, response, null);
                            packetBuffer.clear();
                        }
                    }
                    synchronized (directPacketBuffer) {
                        if (!directPacketBuffer.isEmpty()) {
                            for (WsContext ctx : directPacketBuffer.keySet()) {
                                JSONObject response = new JSONObject();
                                JSONArray dataset = new JSONArray();
                                for (JSONObject value : directPacketBuffer.get(ctx).values()) {
                                    dataset.add(value);
                                }
                                response.put("data", dataset);
                                sendPacket("syn", TargetSystem.DEFAULT, response, ctx);
                            }
                            directPacketBuffer.clear();
                        }
                    }

                    synchronized (deleteBuffer) {
                        if (!deleteBuffer.isEmpty()) {
                            JSONObject body = new JSONObject();
                            JSONArray list = new JSONArray();
							list.addAll(deleteBuffer);
                            body.put("list", list);
                            sendPacket("del", TargetSystem.DEFAULT, body, null);
                            deleteBuffer.clear();
                        }
                    }
                } catch (Exception ex) {
                    log.error("Failed to send bulk packets", ex);
                    synchronized (directPacketBuffer) {
                        directPacketBuffer.clear();
                    }
                    synchronized (packetBuffer) {
                        packetBuffer.clear();
                    }
                }
            }
        });
            packetThread.setName("PacketThread");
            packetThread.start();
        }
    }

    @SuppressWarnings("unchecked")
    public static void sendObject(WsContext ctx, List<JSONObject> objects) {
        synchronized (packetBuffer) {
            for (JSONObject object : objects) {
                String uuid = object.get("uuid").toString();
                packetBuffer.put(uuid, object);
            }
        }
        if (ctx != null) {
            synchronized (directPacketBuffer) {
                Map<String, JSONObject> map1 = directPacketBuffer.computeIfAbsent(ctx, k -> new HashMap<>());
                for (JSONObject object : objects) {
                    String uuid = object.get("uuid").toString();
                    map1.put(uuid, object);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void sendWarn(WsContext ctx, String sourceType, String message) {
        JSONObject body = new JSONObject();
        body.put("level", "warn");
        body.put("sourceType", sourceType);
        body.put("message", message);
        sendPacket("response", TargetSystem.DEFAULT, body, ctx);
    }

    @SuppressWarnings("unchecked")
    public static void sendSettings(WsContext ctx) {
        JSONObject dataset = new JSONObject();
        JSONArray array = new JSONArray();
        for (Map.Entry<String, SettingProperty<?>> entry : Settings.SETTING_PROPERTIES.entrySet()) {
            JSONObject data = new JSONObject();
            data.put("key", entry.getKey());
            data.put("val", entry.getValue().getValue());
            array.add(data);
        }
        dataset.put("settings", array);
        sendPacket("setting", TargetSystem.DEFAULT, dataset, ctx);
    }

    public static void sendObjectToAll(JSONObject... objects) {
        sendObjectToAll(Arrays.stream(objects).toList());
    }

    public static void sendObjectToAll(List<JSONObject> objects) {
        try {
            sendObject(null, objects);
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    public static void deleteObjectToAll(List<DownloadTask> objects) {
        for (DownloadTask object : objects) {
            deleteBuffer.add(object.getUuid().toString());
        }
    }

    public static void changeObject(JSONObjectContainer object, Object key, Object value) {
        object.set(key, value);
        object.set("modified", System.currentTimeMillis());
        sendObjectToAll(object.getRaw());
    }

    public static void changeObject(JSONObjectContainer object, Object... data) {
        if (data.length % 2 != 0)
            throw new IllegalArgumentException("data must be even");

        for (int i = 0 ; i < data.length; i += 2)
            object.set(data[i], data[i + 1]);
        object.set("modified", System.currentTimeMillis());
        sendObjectToAll(object.getRaw());
    }


    @SuppressWarnings("unchecked")
    public static void sendPacket(String cmd, TargetSystem targetSystem, JSONObject content, WsContext ctx) {
        JSONObject packet = new JSONObject();
        packet.put("cmd", cmd);
        packet.put("targetSystem", targetSystem.toString());
        packet.put("content", content);
		if (ctx != null) // Broadcast when target websocket is null
			try {
                ctx.send(packet.toJSONString());
            } catch (Exception ex) {
                if (ex instanceof ClosedChannelException) {
                    // Ignore
                } else {
                    log.error("Failed to send packet", ex);
                }
            }
		else {
			WebServer server = MediaManager.getInstance().getDependencyInjector().resolve(WebServer.class);
			for (var ctx2 : server.getActiveConnections()) {
				sendPacket(cmd, targetSystem, content, ctx2);
			}
		}
	}

    public static void sendWebsSocketResponse(WsContext ctx, WebSocketResponse response, TargetSystem targetSystem, String sourceCommand) {
        if (sourceCommand != null) response.getResponse().set("sourceCommand", sourceCommand);
        sendPacket("response", targetSystem, response.getResponse().getRaw(), ctx);
    }

    /**
     * Builds an object to send as response when a client is requesting
     * @param code 2 = OK, 4 = Error
     * @param message Message
     * @return JSONObject for the client
     */
    public static JSONObjectContainer createResponseObject(int code, String message) {
        JSONObjectContainer content = new JSONObjectContainer();
        content.set("code", code);
        content.set("message", message);
        return content;
    }

    private static List<Anime> lastSendAnimes = new ArrayList<>();

    public static void sendAutoLoaderItem(WsContext ctx, Anime anime) {
        List<Anime> packet = new ArrayList<>(lastSendAnimes);
        packet.removeIf(anime2 -> anime2.getId() == anime.getId());
        packet.add(anime);
        lastSendAnimes = packet;
        sendAutoLoaderItem(ctx, packet);
    }

    public static void sendAutoLoaderItem(WsContext ctx, List<Anime> animes) {
        lastSendAnimes = animes;
        JSONObjectContainer response = new JSONObjectContainer();
        JSONArrayContainer items = new JSONArrayContainer();
        for (Anime anime : animes) {
            items.add(anime.toJSONObject());   
        }
        response.set("items", items);
        sendPacket("syn", TargetSystem.AUTOLOADER, response.getRaw(), ctx);
    }
}
