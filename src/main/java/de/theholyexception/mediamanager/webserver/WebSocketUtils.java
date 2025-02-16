package de.theholyexception.mediamanager.webserver;

import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.models.TableItem;
import de.theholyexception.mediamanager.settings.Settings;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class WebSocketUtils {

    @SuppressWarnings("unchecked")
    public static void sendObject(WebSocketBasic socket, List<JSONObject> objects) {
        JSONObject response = new JSONObject();
        JSONArray dataset = new JSONArray();
        dataset.addAll(objects);
        response.put("data", dataset);
        sendPacket("syn", "default", response, socket);
    }

    @SuppressWarnings("unchecked")
    public static void sendWarn(WebSocketBasic socket, String sourceType, String message) {
        JSONObject body = new JSONObject();
        body.put("level", "warn");
        body.put("sourceType", sourceType);
        body.put("message", message);
        sendPacket("response", "default", body, socket);
    }

    @SuppressWarnings("unchecked")
    public static void sendSettings(WebSocketBasic socket) {
        Settings.SETTING_PROPERTIES.entrySet().stream()
                .filter(entry -> entry.getValue().getMetadata().forClient())
                .forEach(entry -> {
                    JSONObject dataset = new JSONObject();
                    dataset.put("key", entry.getKey());
                    dataset.put("val", entry.getValue().getValue());
                    sendPacket("setting", "default", dataset, socket);
                });
    }

    public static void sendObjectToAll(JSONObject... objects) {
        sendObjectToAll(Arrays.stream(objects).toList());
    }

    public static void sendObjectToAll(List<JSONObject> objects) {
        for (WebSocketBasic webSocketBasic : new ArrayList<>(MediaManager.getInstance().getClientList())) {
            try {
                sendObject(webSocketBasic, objects);
            } catch (Exception ex) {
                log.error(ex.getMessage());
                webSocketBasic.close();
                MediaManager.getInstance().getClientList().remove(webSocketBasic);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void deleteObjectToAll(TableItem object) {
        JSONObject body = new JSONObject();
        body.put("uuid", object.getUuid().toString());
        sendPacket("del", "default", body, null);
    }

    @SuppressWarnings("unchecked")
    public static void changeObject(JSONObject object, Object key, Object value) {
        object.put(key, value);
        object.put("modified", System.currentTimeMillis());
        sendObjectToAll(object);
    }

    public static void sendPacket(String cmd, String targetSystem, JSONObject content, WebSocketBasic socket) {
        JSONObject packet = new JSONObject();
        packet.put("cmd", cmd);
        packet.put("targetSystem", targetSystem);
        packet.put("content", content);
        if (socket != null)
            socket.send(packet.toJSONString());
        else {
            for (WebSocketBasic webSocketBasic : new ArrayList<>(MediaManager.getInstance().getClientList())) {
                sendPacket(cmd, targetSystem, content, webSocketBasic);
            }
        }
    }
}
