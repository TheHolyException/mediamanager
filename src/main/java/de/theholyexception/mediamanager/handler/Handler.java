package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.di.DIInject;
import de.theholyexception.mediamanager.util.TargetSystem;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import lombok.Getter;
import org.tomlj.TomlParseResult;

/**
 * Abstract base class for all handler implementations in the MediaManager application.
 * Handlers are responsible for processing commands and managing specific functionality
 * within their designated target system.
 */
public abstract class Handler {

    protected JSONObjectContainer handlerConfiguration;

    @Getter
    private TargetSystem targetSystem;

    @DIInject
    protected TomlParseResult config;

    /**
     * Creates a new Handler instance for the specified target system.
     *
     * @param targetSystem The target system this handler will manage
     */
    protected Handler(TargetSystem targetSystem) {
        this.targetSystem = targetSystem;
    }
    protected Handler() {}

    /**
     * Processes an incoming command for this handler's target system.
     *
     * @param ctx The WebSocket connection the command was received from
     * @param command The command to process
     * @param content Additional command parameters and data
     */
    public void handleWebSocketPacket(WsContext ctx, String command, JSONObjectContainer content) {}

    /**
     * Loads and initializes configurations specific to this handler.
     * This method is called during application startup after the main configuration is loaded.
     * The default implementation does nothing.
     */
    public void loadConfigurations() {
    }
    /**
     * Performs any required initialization for this handler.
     * This method is called after all configurations have been loaded.
     * Subclasses must implement this method to perform their specific initialization.
     */
    public void initialize() {}

    /**
     * Registers API endpoints for this handler.
     * This method is called after all configurations have been loaded.
     * Subclasses must implement this method to register their specific API endpoints.
     */
    public void registerAPI(Javalin app) {}

}
