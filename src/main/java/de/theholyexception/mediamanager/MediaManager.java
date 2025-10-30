package de.theholyexception.mediamanager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import de.theholyexception.holyapi.datastorage.file.ConfigJSON;
import de.theholyexception.holyapi.datastorage.file.FileConfiguration;
import de.theholyexception.holyapi.datastorage.sql.interfaces.MySQLInterface;
import de.theholyexception.holyapi.di.ComplexDIContainer;
import de.theholyexception.holyapi.util.DataUtils;
import de.theholyexception.holyapi.util.ResourceUtilities;
import de.theholyexception.mediamanager.api.WebServer;
import de.theholyexception.mediamanager.handler.*;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.Season;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.util.InitializationException;
import de.theholyexception.mediamanager.util.ProxyHandler;
import de.theholyexception.mediamanager.util.TargetSystem;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.StaticUtils;
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

@Slf4j
public class MediaManager {

    @Getter
    private static MediaManager instance;

    @Getter
    private final ComplexDIContainer dependencyInjector;

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
     * Changes the log level of the application based on the configuration.
     * The log level is read from the 'general.logLevel' configuration property.
     */
    public void changeLogLevel() {
        Level level = Level.valueOf(config.getString("general.logLevel"));
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("ROOT").setLevel(level);
    }

    @Getter
    private boolean isDockerEnvironment = false;
    @Getter
    private ConfigJSON systemSettings;
    @Getter
    private final Map<TargetSystem, Handler> handlers = Collections.synchronizedMap(new HashMap<>());
    private TomlParseResult config;

    @Getter
    private String downloadersVersion = "unknown";
    @Getter
    private String ultimateutilsVersion = "unknown";
    @Getter
    private String holyapiVersion = "unknown";

	public MediaManager() {
        dependencyInjector = new ComplexDIContainer().setResolveCircularDependencies(true);
        dependencyInjector.register(MediaManager.class, this);

        try {
            Properties versionProps = new Properties();
            versionProps.load(MediaManager.class.getResourceAsStream("/version.properties"));
            downloadersVersion = versionProps.getProperty("downloaders.version", "unknown");
            ultimateutilsVersion = versionProps.getProperty("ultimateutils.version", "unknown");
            holyapiVersion = versionProps.getProperty("holyapi.version", "unknown");
        } catch (Exception ex) {
            log.warn("Could not load version properties: " + ex.getMessage());
        }
        log.info("Starting MediaManager");
        log.info("\t- Downloaders: {}", downloadersVersion);
        log.info("\t- UltimateUtils: {}", ultimateutilsVersion);
        log.info("\t- HolyAPI: {}", holyapiVersion);

        MediaManager.instance = this;
		List<Runnable> initListeners = Collections.synchronizedList(new ArrayList<>());
        initListeners.add(this::loadConfigFile);
        initListeners.add(this::loadDatabase);
		initListeners.add(this::loadHandlers);
        initListeners.add(this::loadConfiguration);
        initListeners.add(this::checkForDockerEnvironment);
        initListeners.add(this::loadAPI);

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

        WebSocketUtils.initialize(config);
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
            addHandler(DefaultHandler.class);
            addHandler(AniworldHandler.class);
            addHandler(AutoLoaderHandler.class);
            addHandler(StatisticsHandler.class);
        } catch (Exception ex) {
            InitializationException initException = new InitializationException("Handler initialization", ex.getMessage());
            initException.addSuppressed(ex);
            throw initException;
        }
    }

    /**
     * Registers a handler with the application.
     *
     * @param clazz Class of the handler to register
     */
    private <T extends Handler> void addHandler(Class<T> clazz) {
        dependencyInjector.register(clazz, clazz);
        T instance = dependencyInjector.resolve(clazz);
        handlers.put(instance.getTargetSystem(), instance);
    }

    /**
     * Loads the configuration file from the specified path.
     *
     * @throws InitializationException if the configuration file could not be loaded
     */
    private void loadConfigFile() throws InitializationException {
        try {
            Path path = Paths.get(("./config/config.toml"));
            config = Toml.parse(path);
            StringBuilder errors = new StringBuilder();
            config.errors().forEach(error -> errors.append(error.getMessage()));
            if (!errors.isEmpty())
                throw new InitializationException("Failed to parse config.toml", errors.toString());

            dependencyInjector.register(TomlParseResult.class, config);
        } catch (IOException ex) {
            throw new InitializationException("Failed to load config.toml", ex.getMessage());
        }

        try {
            systemSettings = new ConfigJSON(new File("./config/systemsettings.json"));
            systemSettings.loadConfig();
            dependencyInjector.register(ConfigJSON.class, systemSettings);
        } catch (Exception ex) {
            throw new InitializationException("Failed to load systemsettings.json", ex.getMessage());
        }

    }

    /**
     * Loads the configuration from the database.
     * This method is called after the configuration file has been loaded.
     * It loads the database configuration and settings.
     *
     * @throws InitializationException if the database configuration could not be loaded
     */
    private void loadConfiguration() throws InitializationException {
        try {
            changeLogLevel();
            Settings.init(systemSettings);
            handlers.values().forEach(Handler::loadConfigurations);
            systemSettings.saveConfig(FileConfiguration.SaveOption.PRETTY_PRINT);
        } catch (Exception ex) {
            var ex2 = new InitializationException("Failed to load systemsettings.json", ex.getMessage());
            ex2.addSuppressed(ex);
            throw ex2;
        }

        ProxyHandler.initialize(config);
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
    private void loadAPI() throws InitializationException {
        try {
            dependencyInjector.register(WebServer.class, WebServer.class);
            dependencyInjector.resolve(WebServer.class);
        } catch (Exception ex) {
            var ex2 = new InitializationException("Load Webserver", ex.getMessage());
            ex2.addSuppressed(ex);
            throw ex2;
        }
    }

    /**
     * Initializes the database connection and executes any required database scripts.
     * 
     * @throws InitializationException if database connection or script execution fails
     */
    private void loadDatabase() throws InitializationException {
        try {
            MySQLInterface db = new MySQLInterface(
                    config.getString("mysql.host", () -> "localhost"),
                    Math.toIntExact(config.getLong("mysql.port", () -> 3306)),
                config.getString("mysql.username", () -> "mediamanager"),
                config.getString("mysql.password", () -> "mediamanager"),
                config.getString("mysql.database", () -> "mediamanager"));
            db.asyncDataSettings(2);
            db.connect();
            dependencyInjector.register(MySQLInterface.class, db);

            if (Boolean.TRUE.equals(config.getBoolean("autoloader.executeDBScripts"))) {
                executeDatabaseScripts(db);
            }

            Anime.setCurrentID(getCurrentId("anime", db));
            Season.setCurrentID(getCurrentId("season", db));
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
    private void executeDatabaseScripts(MySQLInterface db) throws InitializationException {
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
    private int getCurrentId(String table, MySQLInterface db) {
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
