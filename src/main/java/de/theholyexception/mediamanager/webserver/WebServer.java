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

@Slf4j
public class WebServer extends Thread {

    private ServerSocket serverSocket;
    private final Configuration configuration;
    private final List<Connection> connectionList = Collections.synchronizedList(new ArrayList<>());

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

    public record Configuration(int port, String host, String webroot, WebSocketEvent receptionist) {}

}
