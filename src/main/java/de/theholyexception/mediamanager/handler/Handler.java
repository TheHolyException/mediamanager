package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.TargetSystem;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import lombok.Getter;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;

public abstract class Handler {

    protected JSONObjectContainer handlerConfiguration;
    @Getter
    private final TargetSystem targetSystem;

    protected Handler(TargetSystem targetSystem) {
        this.targetSystem = targetSystem;
    }

    public abstract WebSocketResponse handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content);

    public void loadConfigurations() {
    }
    public abstract void initialize();

}
