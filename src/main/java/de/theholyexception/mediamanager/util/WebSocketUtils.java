package de.theholyexception.mediamanager.util;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.api.WebServer;
import de.theholyexception.mediamanager.models.DownloadTask;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.MediaManagerConfig;
import io.javalin.websocket.WsContext;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class WebSocketUtils {
    private WebSocketUtils() {}

    private static final Map<String, JSONObject> packetBuffer = new HashMap<>();
    private static final List<String> deleteBuffer = new ArrayList<>();

    public static void initialize() {
        if (MediaManagerConfig.General.enablePacketBuffer) {
            Thread packetThread = new Thread(() -> {
                while (true) {
                    try {
                        Utils.sleep(MediaManagerConfig.General.packetBufferSleep);
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

    public static void sendObject(DownloadTask task) {
        synchronized (packetBuffer) {
            packetBuffer.put(task.getUuid().toString(), task.getContent().getRaw());
        }
    }

    public static void sendObject(List<DownloadTask> tasks) {
        synchronized (packetBuffer) {
            for (DownloadTask task : tasks) {
                packetBuffer.put(task.getUuid().toString(), task.getContent().getRaw());
            }
        }
    }

    public static void sendObjectTo(WsContext ctx, List<DownloadTask> tasks) {
        JSONObject response = new JSONObject();
        JSONArray dataset = new JSONArray();
        for (DownloadTask task : tasks) {
            dataset.add(task.getContent());
        }
        response.put("data", dataset);
        sendPacket("syn", TargetSystem.DEFAULT, response, ctx);
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

    public static void deleteObjectToAll(List<DownloadTask> objects) {
        for (DownloadTask object : objects) {
            deleteBuffer.add(object.getUuid().toString());
        }
    }

    public static void changeObject(DownloadTask task, Object key, Object val) {
        JSONObjectContainer container = task.getContent();
        container.set(key, val);
        container.set("modified", System.currentTimeMillis());
        sendObject(task);
    }

    public static void changeObject(DownloadTask task, Object... data) {
        if (data.length % 2 != 0)
            throw new IllegalArgumentException("data must be even");

        JSONObjectContainer content = task.getContent();
        for (int i = 0 ; i < data.length; i += 2)
            content.set(data[i], data[i + 1]);
        content.set("modified", System.currentTimeMillis());
        sendObject(task);
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
