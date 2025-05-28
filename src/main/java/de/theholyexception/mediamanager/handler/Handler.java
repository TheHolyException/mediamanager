package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.util.TargetSystem;
import lombok.Getter;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;

public abstract class Handler {

    protected JSONObjectContainer handlerConfiguration;
    @Getter
    private final TargetSystem targetSystem;

    protected Handler(TargetSystem targetSystem) {
        this.targetSystem = targetSystem;
    }

    public abstract void handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content);

    public void loadConfigurations() {
    }
    public abstract void initialize();

}
