package de.theholyexception.mediamanager.webserver;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.TargetSystem;
import de.theholyexception.mediamanager.models.TableItemDTO;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;

@Slf4j
public class WebSocketUtils {
    private WebSocketUtils() {}

    @SuppressWarnings("unchecked")
    public static void sendObject(WebSocketBasic socket, List<JSONObject> objects) {
        JSONObject response = new JSONObject();
        JSONArray dataset = new JSONArray();
        dataset.addAll(objects);
        response.put("data", dataset);
        sendPacket("syn", TargetSystem.DEFAULT, response, socket);
    }

    @SuppressWarnings("unchecked")
    public static void sendWarn(WebSocketBasic socket, String sourceType, String message) {
        JSONObject body = new JSONObject();
        body.put("level", "warn");
        body.put("sourceType", sourceType);
        body.put("message", message);
        sendPacket("response", TargetSystem.DEFAULT, body, socket);
    }

    @SuppressWarnings("unchecked")
    public static void sendSettings(WebSocketBasic socket) {
        JSONObject dataset = new JSONObject();
        JSONArray array = new JSONArray();
        for (Map.Entry<String, SettingProperty<?>> entry : Settings.SETTING_PROPERTIES.entrySet()) {
            if (entry.getValue().getMetadata().forClient()) {
                JSONObject data = new JSONObject();
                data.put("key", entry.getKey());
                data.put("val", entry.getValue().getValue());
                array.add(data);
            }
        }
        dataset.put("settings", array);
        sendPacket("setting", TargetSystem.DEFAULT, dataset, socket);
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
    public static void deleteObjectToAll(List<TableItemDTO> objects) {
        JSONObject body = new JSONObject();
        JSONArray list = new JSONArray();
        for (TableItemDTO object : objects)
            list.add(object.getUrl());
        body.put("list", list);
        sendPacket("del", TargetSystem.DEFAULT, body, null);
    }

    @SuppressWarnings("unchecked")
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
    public static void sendPacket(String cmd, TargetSystem targetSystem, JSONObject content, WebSocketBasic socket) {
        JSONObject packet = new JSONObject();
        packet.put("cmd", cmd);
        packet.put("targetSystem", targetSystem.toString());
        packet.put("content", content);
        if (socket != null) // Broadcast when target websocket is null
            socket.send(packet.toJSONString());
        else {
            for (WebSocketBasic webSocketBasic : new ArrayList<>(MediaManager.getInstance().getClientList())) {
                sendPacket(cmd, targetSystem, content, webSocketBasic);
            }
        }
    }

    public static void sendWebsSocketResponse(WebSocketBasic socket, WebSocketResponse response, TargetSystem targetSystem, String sourceCommand) {
        if (sourceCommand != null) response.getResponse().set("sourceCommand", sourceCommand);
        sendPacket("response", targetSystem, response.getResponse().getRaw(), socket);
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

    public static void sendAutoLoaderItem(WebSocketBasic socket, Anime anime) {
         sendAutoLoaderItem(socket, Arrays.stream(new Anime[]{anime}).toList());
    }
    public static void sendAutoLoaderItem(WebSocketBasic socket, List<Anime> animes) {
        JSONObjectContainer response = new JSONObjectContainer();
        JSONArrayContainer items = new JSONArrayContainer();
        for (Anime anime : animes) {
            items.add(anime.toJSONObject());   
        }
        response.set("items", items);
        sendPacket("syn", TargetSystem.AUTOLOADER, response.getRaw(), socket);
    }
}
