package de.theholyexception.mediamanager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import de.theholyexception.holyapi.datastorage.file.ConfigJSON;
import de.theholyexception.holyapi.datastorage.file.FileConfiguration;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.datastorage.sql.interfaces.MySQLInterface;
import de.theholyexception.holyapi.util.DataUtils;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.holyapi.util.ResourceUtilities;
import de.theholyexception.mediamanager.handler.AniworldHandler;
import de.theholyexception.mediamanager.handler.AutoLoaderHandler;
import de.theholyexception.mediamanager.handler.DefaultHandler;
import de.theholyexception.mediamanager.handler.Handler;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.Season;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.util.*;
import de.theholyexception.mediamanager.webserver.WebServer;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.StaticUtils;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketEvent;
import org.slf4j.LoggerFactory;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;

@Slf4j
public class MediaManager {

    @Getter
    private static MediaManager instance;
    private static final ExecutorHandler executorHandler;

    /**
     * Main entry point for the MediaManager application.
     * Initializes a new instance of MediaManager.
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        new MediaManager();
    }

    /**
     * Retrieves the TOML configuration for the application.
     *
     * @return The TOML configuration parse result
     */
    public static TomlParseResult getTomlConfig() {
        return instance.tomlConfig;
    }

    /**
     * Changes the log level of the application based on the configuration.
     * The log level is read from the 'general.logLevel' configuration property.
     */
    public void changeLogLevel() {
        Level level = Level.valueOf(tomlConfig.getString("general.logLevel"));
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("ROOT").setLevel(level);
    }

    @Getter
    private boolean isDockerEnvironment = false;
    @Getter
    private final List<WebSocketBasic> clientList = Collections.synchronizedList(new ArrayList<>());
    @Getter
    private ConfigJSON systemSettings;
    @Getter
    private final Map<TargetSystem, Handler> handlers = Collections.synchronizedMap(new HashMap<>());
    private TomlParseResult tomlConfig;
    @Getter
    private DataBaseInterface db;

    private String downloadersVersion = "unknown";
    private String ultimateutilsVersion = "unknown";

    static {
        executorHandler = new ExecutorHandler(Executors.newFixedThreadPool(1));
        executorHandler.setThreadNameFactory(cnt -> "WS-Executor-" + cnt);
    }

	public MediaManager() {
        try {
            Properties versionProps = new Properties();
            versionProps.load(MediaManager.class.getResourceAsStream("/version.properties"));
            downloadersVersion = versionProps.getProperty("downloaders.version", "unknown");
            ultimateutilsVersion = versionProps.getProperty("ultimateutils.version", "unknown");
        } catch (Exception ex) {
            log.warn("Could not load version properties: " + ex.getMessage());
        }
        log.info("Starting MediaManager");
        log.info("\t- Downloaders: {}", downloadersVersion);
        log.info("\t- UltimateUtils: {}", ultimateutilsVersion);
        MediaManager.instance = this;
		List<Runnable> initListeners = Collections.synchronizedList(new ArrayList<>());
		initListeners.add(this::loadHandlers);
        initListeners.add(this::loadConfiguration);
        initListeners.add(this::checkForDockerEnvironment);
        initListeners.add(this::loadWebServer);
        initListeners.add(this::loadDatabase);

        for (int i = 0; i < initListeners.size(); i++) {
            Runnable initListener = initListeners.get(i);
            try {
                initListener.run();
            } catch (InitializationException ex) {
                log.error("Failed to initialize " + ex.getName(), ex);
                System.exit(1000+i);
            } catch (Exception ex) {
                log.error("Failed to initialize " + initListener + " unusual error:", ex);
            }
        }

        handlers.values().forEach(Handler::initialize);
    }

    /**
     * Initializes and registers all the handler components of the application.
     * This includes the default handler, Aniworld handler, and AutoLoader handler.
     * 
     * @throws InitializationException if any handler fails to initialize
     */
    private void loadHandlers() {
        try {
            addHandler(new DefaultHandler(TargetSystem.DEFAULT));
            addHandler(new AniworldHandler(TargetSystem.ANIWORLD));
            addHandler(new AutoLoaderHandler(TargetSystem.AUTOLOADER));
        } catch (Exception ex) {
            throw new InitializationException("Load Handlers", ex.getMessage());
        }
    }

    /**
     * Registers a handler with the application.
     *
     * @param handler The handler to register
     */
    private void addHandler(Handler handler) {
        handlers.put(handler.getTargetSystem(), handler);
    }

    /**
     * Loads and validates the application configuration from various sources.
     * This includes TOML configuration, system settings, and proxy settings.
     *
     * @throws InitializationException if configuration files are missing or invalid
     */
    private void loadConfiguration() throws InitializationException {
        try {
            Path path = Paths.get(("./config/config.toml"));
            tomlConfig = Toml.parse(path);
            StringBuilder errors = new StringBuilder();
            tomlConfig.errors().forEach(error -> errors.append(error.getMessage()));
            if (!errors.isEmpty())
                throw new InitializationException("Failed to parse config.toml", errors.toString());

            int webSocketThreads = Math.toIntExact(getTomlConfig().getLong("webserver.webSocketThreads", () -> 10));
            executorHandler.updateExecutorService(Executors.newFixedThreadPool(webSocketThreads));
        } catch (IOException ex) {
            throw new InitializationException("Failed to load config.toml", ex.getMessage());
        }

        try {
            systemSettings = new ConfigJSON(new File("./config/systemsettings.json"));
            systemSettings.loadConfig();
            changeLogLevel();
            Settings.init(systemSettings);
            handlers.values().forEach(Handler::loadConfigurations);
            systemSettings.saveConfig(FileConfiguration.SaveOption.PRETTY_PRINT);
        } catch (Exception ex) {
            throw new InitializationException("Failed to load systemsettings.json", ex.getMessage());
        }

        ProxyHandler.initialize(tomlConfig);
    }

    /**
     * Detects if the application is running in a Docker container environment.
     * Sets the isDockerEnvironment flag accordingly.
     */
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

    /**
     * Initializes and starts the embedded web server.
     * Configures the server based on settings from the TOML configuration.
     * Sets up WebSocket communication for real-time client updates.
     */
    private void loadWebServer() {
        try {
            new WebServer(new WebServer.Configuration(
                    Math.toIntExact(tomlConfig.getLong("webserver.port", () -> 8080)),
                    tomlConfig.getString("webserver.host", () -> "0.0.0.0"),
                    tomlConfig.getString("webserver.webroot", () -> "./www")
                    , new WebSocketEvent() {
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
                        WebSocketResponse response = null;
                        try {
                            handler.handleCommand(socket, cmd, content);
                        } catch (WebSocketResponseException ex) {
                            response = ex.getResponse();
                        } catch (Exception ex) {
                            log.error("Failed to process command", ex);
                            response = WebSocketResponse.ERROR.setMessage(ex.getMessage());
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
        } catch (Exception ex) {
            throw new InitializationException("Load Webserver", ex.getMessage());
        }
    }

    /**
     * Initializes the database connection and executes any required database scripts.
     * 
     * @throws InitializationException if database connection or script execution fails
     */
    private void loadDatabase() throws InitializationException {
        try {
            db = new MySQLInterface(
                    getTomlConfig().getString("mysql.host", () -> "localhost"),
                    Math.toIntExact(getTomlConfig().getLong("mysql.port", () -> 3306)),
                    getTomlConfig().getString("mysql.username", () -> "mediamanager"),
                    getTomlConfig().getString("mysql.password", () -> "mediamanager"),
                    getTomlConfig().getString("mysql.database", () -> "mediamanager"));
            db.asyncDataSettings(2);
            db.connect();

            if (Boolean.TRUE.equals(getTomlConfig().getBoolean("autoloader.executeDBScripts"))) {
                executeDatabaseScripts();
            }

            Anime.setCurrentID(getCurrentId("anime"));
            Season.setCurrentID(getCurrentId("season"));
        } catch (Exception ex) {
            throw new InitializationException("Load Database", ex.getMessage());
        }
    }

    /**
     * Executes SQL scripts from the resources/sql directory.
     * Scripts are executed in alphabetical order.
     * 
     * @throws InitializationException if any script execution fails
     */
    private void executeDatabaseScripts() throws InitializationException {
        try {
            List<String> files = ResourceUtilities.listResourceFilesRecursive("sql/");
            for (String file : files.stream().sorted().toList()) {
                if (!file.endsWith(".sql")) continue;
                String content = new String(DataUtils.readAllBytes(ResourceUtilities.getResourceAsStream("sql/"+file)));
                String[] filePath = file.split("/");
                log.debug("Executing SQL Script: " + filePath[filePath.length-1]);
                db.executeAsync(-2, content);
            }
            db.getExecutorHandler().awaitGroup(-2, 20);
        } catch (Exception ex) {
            throw new InitializationException("Execute Database Scripts", ex.getMessage());
        }
    }

    /**
     * Retrieves the current maximum ID from the specified database table.
     * Used for generating new unique IDs.
     *
     * @param table The name of the table to query
     * @return The maximum ID found in the table, or 0 if the table is empty
     */
    private int getCurrentId(String table) {
        int result = 0;
        try {
            ResultSet rs = db.executeQuery("select nKey from " + table + " order by nKey desc");
            if (rs.next())
                result = rs.getInt(1);
            rs.close();
        } catch (SQLException ex) {
            log.error("", ex);
        }
        return result;
    }
}
