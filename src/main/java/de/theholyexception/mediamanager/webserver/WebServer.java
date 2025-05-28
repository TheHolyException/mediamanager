package de.theholyexception.mediamanager.webserver;

import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HTTP server that handles both static file serving and WebSocket connections.
 * This server manages incoming client connections and delegates them to appropriate handlers.
 */
@Slf4j
public class WebServer extends Thread {

    private ServerSocket serverSocket;
    private final Configuration configuration;
    private final List<Connection> connectionList = Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates and starts a new WebServer instance with the specified configuration.
     * The server will start in a new thread and begin accepting connections immediately.
     *
     * @param configuration The configuration containing port, host, webroot, and WebSocket settings
     * @throws IllegalStateException if the server cannot be started
     */
    public WebServer(Configuration configuration) {
        log.debug("Starting WebServer with following configuration: " + configuration);
        this.configuration = configuration;
        File webRoot = new File(configuration.webroot());

        if (!webRoot.exists() && !webRoot.mkdirs()) {
            log.error("Failed to create webroot directory");
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(configuration.host());
            serverSocket = new ServerSocket(configuration.port(), 50, address);
            log.info("Starting WebServer on " + address.getHostAddress() + ":" + configuration.port());

            this.start();
        } catch (IOException ex) {
            log.error("Failed to start WebServer", ex);
        }
    }

    /**
     * Main server loop that accepts incoming client connections.
     * Each connection is handled in a separate thread.
     * This method runs until the server is interrupted.
     */
    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                connectionList.add(new Connection(serverSocket.accept(), configuration));
            } catch (Exception ex) {
                // Can be ignored
            }
        }
    }

    /**
     * Configuration record for WebServer settings.
     * 
     * @param port The port number to listen on
     * @param host The host address to bind to
     * @param webroot The root directory for serving static files
     * @param receptionist The WebSocket event handler for managing WebSocket connections
     */
    public record Configuration(int port, String host, String webroot, WebSocketEvent receptionist) {}

}
