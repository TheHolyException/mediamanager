package de.theholyexception.mediamanager.webserver;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import lombok.Getter;

@Getter
public enum WebSocketResponse {

    OK(WebSocketUtils.createResponseObject(2, "OK")),
    WARN(WebSocketUtils.createResponseObject(3, "WARN")),
    ERROR(WebSocketUtils.createResponseObject(4, "ERROR"));

    private final JSONObjectContainer response;

    WebSocketResponse(JSONObjectContainer response) {
        this.response = response;
    }

    public WebSocketResponse setMessage(String message) {
        if (message == null) return this;
        response.set("message", message);
        return this;
    }
}
