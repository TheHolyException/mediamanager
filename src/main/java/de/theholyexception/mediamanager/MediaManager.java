package de.theholyexception.mediamanager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import de.theholyexception.holyapi.datastorage.file.ConfigJSON;
import de.theholyexception.holyapi.datastorage.file.FileConfiguration;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.handler.AniworldHandler;
import de.theholyexception.mediamanager.handler.AutoLoaderHandler;
import de.theholyexception.mediamanager.handler.DefaultHandler;
import de.theholyexception.mediamanager.handler.Handler;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.webserver.WebServer;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.StaticUtils;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketEvent;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;

@Slf4j
public class MediaManager {

    @Getter
    private static MediaManager instance;

    public static void main(String[] args) {
        new MediaManager();
    }

    public void changeLogLevel() {
        Level level = Level.valueOf(configuration.getJson().get("logLevel", String.class));
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("ROOT").setLevel(level);
    }

    @Getter
    private boolean isDockerEnvironment = false;
    @Getter
    private final List<WebSocketBasic> clientList = Collections.synchronizedList(new ArrayList<>());
    @Getter
    private ConfigJSON configuration;
    @Getter
    private final Map<TargetSystem, Handler> handlers = Collections.synchronizedMap(new HashMap<>());

    private static final ExecutorHandler executorHandler;

    static {
        executorHandler = new ExecutorHandler(Executors.newFixedThreadPool(10));
        executorHandler.setThreadNameFactory(cnt -> "WS-Executor-" + cnt);
    }

    public MediaManager() {
        MediaManager.instance = this;
        loadHandlers();
        loadConfiguration();
        checkForDockerEnvironment();
        loadWebServer();

        handlers.values().forEach(Handler::initialize);
    }

    private void loadHandlers() {
        addHandler(new DefaultHandler(TargetSystem.DEFAULT));
        addHandler(new AniworldHandler(TargetSystem.ANIWORLD));
        addHandler(new AutoLoaderHandler(TargetSystem.AUTOLOADER));
    }

    private void addHandler(Handler handler) {
        handlers.put(handler.getTargetSystem(), handler);
    }

    private void loadConfiguration() {
        configuration = new ConfigJSON(new File("./config.json"));
        configuration.loadConfig();
        changeLogLevel();
        if (!configuration.getFile().exists()) {
            URL url = MediaManager.class.getClassLoader().getResource("config-template.json");
            if (url == null) throw new IllegalStateException("URL is null");
            try (FileInputStream fis = new FileInputStream(new File(url.toURI()))) {
                boolean result = configuration.createNewIfNotExists(fis);
                if (result) log.info("New configuration created");
            } catch (IOException | URISyntaxException ex) {
                log.error(ex.getMessage());
            }
        }
        Settings.init(configuration);
        handlers.values().forEach(Handler::loadConfigurations);
        configuration.saveConfig(FileConfiguration.SaveOption.PRETTY_PRINT);
    }

    private void checkForDockerEnvironment() {
        try {
            String a = new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.max").start().getInputStream()));
            if (!a.isEmpty()) isDockerEnvironment = true;
            return;
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }

        isDockerEnvironment = false;
        log.warn("No docker environment!");
    }

    private void loadWebServer() {
        new WebServer(new WebServer.Configuration(8080, "0.0.0.0", "./www", new WebSocketEvent() {
            @Override
            public void onMessage(String data, WebSocketBasic socket) {
                JSONObjectContainer dataset = (JSONObjectContainer) JSONReader.readString(data);

                String targetSystem = dataset.get("targetSystem", "default", String.class);
                String cmd = dataset.get("cmd", String.class);
                JSONObjectContainer content = dataset.getObjectContainer("content", new JSONObjectContainer());

                Handler handler = handlers.get(TargetSystem.valueOf(targetSystem.toUpperCase()));
                if (handler == null)
                    throw new IllegalStateException("Invalid target-system: " + targetSystem);

                executorHandler.putTask(new ExecutorTask(() -> {
                    WebSocketResponse response;
                    try {
                        response = handler.handleCommand(socket, cmd, content);
                    } catch (Exception ex) {
                        response = WebSocketResponse.ERROR.setMessage(ex.getMessage());
                        log.error("Failed to process command", ex);
                    }
                    if (response != null) {
                        response.getResponse().set("sourceCommand", cmd);
                        WebSocketUtils.sendPacket("response", handler.getTargetSystem(),response.getResponse().getRaw(), socket);
                    }
                }));
            }

            @Override
            public void onOpen(WebSocketBasic socket) {
                clientList.add(socket);
            }

            @Override
            public void onClose(WebSocketBasic socket) {
                clientList.remove(socket);
            }

            @Override
            public void onError(WebSocketBasic socket, String message, Exception exception) {
                clientList.remove(socket);
            }
        }));
    }
}
