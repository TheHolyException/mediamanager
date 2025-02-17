package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.datastorage.sql.interfaces.MySQLInterface;
import de.theholyexception.holyapi.util.ResourceUtilities;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.configuration.ConfigJSON;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.SettingMetadata;
import de.theholyexception.mediamanager.models.aniworld.Season;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.StaticUtils;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class AutoLoaderHandler extends Handler {

    private JSONObjectContainer subscriptions;
    private SettingProperty<Integer> spCheckIntervalMin;
    private final AtomicInteger checkIntervalMS = new AtomicInteger();
    private boolean passiveMode;
    private final List<Anime> subscribedAnimes = Collections.synchronizedList(new ArrayList<>());
    private SettingProperty<Integer> spURLResolverThreads;
    private ConfigJSON dataFile;
    private DataBaseInterface db;
    private DefaultHandler defaultHandler;

    public AutoLoaderHandler(String targetSystem) {
        super(targetSystem);
    }

    @Override
    public void loadConfigurations() {
        super.loadConfigurations();

        spURLResolverThreads = new SettingProperty<>(new SettingMetadata("urlResolverThreads", false));
        spURLResolverThreads.addSubscriber(value -> Episode.urlResolver.updateExecutorService(Executors.newFixedThreadPool(value)));
        spURLResolverThreads.setValue(handlerConfiguration.get("urlResolverThreads", 10, Integer.class));

        String dataFilePath = handlerConfiguration.get("dataFile", "./autoloader.data.json", String.class);
        this.dataFile = new ConfigJSON(new File(dataFilePath));
        this.dataFile.createNewIfNotExists();
        this.dataFile.loadConfig();

        subscriptions = dataFile.getJson().getObjectContainer("subscriptions", new JSONObjectContainer());
        spCheckIntervalMin = new SettingProperty<>(new SettingMetadata("checkIntervalMin", false));
        spCheckIntervalMin.addSubscriber(value -> {
            if (!subscriptions.getRaw().isEmpty())
                checkIntervalMS.set(value*60*1000 / subscriptions.getRaw().size());
            else
                checkIntervalMS.set(Integer.MAX_VALUE);
        });
        spCheckIntervalMin.setValue(handlerConfiguration.get("checkIntervalMin", 5, Integer.class));

        passiveMode = handlerConfiguration.get("passiveMode", Boolean.class);
    }

    @Override
    public void initialize() {
        defaultHandler = (DefaultHandler) MediaManager.getInstance().getHandlers().get("default");
        Anime.setBaseDirectory(new File(defaultHandler.getTargets().get("stream-animes").path()));

        loadDatabase();

        try {
            subscribedAnimes.clear();
            subscribedAnimes.addAll(Anime.loadFromDB(db));
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }

        printTableInfo("anime", "season", "episode");
        //addSubscriber("https://aniworld.to/anime/stream/dr-stone", 1);
        subscribedAnimes.forEach(Anime::scanDirectoryForExistingEpisodes);
        subscribedAnimes.forEach(a -> System.out.println("unloaded: " + a.getUnloadedEpisodeCount()));
        subscribedAnimes.forEach(a -> {
            if (a.isDeepDirty())
                a.writeToDB(db);
        });
        db.getExecutorHandler().awaitGroup(-1);
        System.out.println(subscribedAnimes.get(0).getEpisodeCount());

        startThread();
        //readTestFromDB();
    }

    private void readTestFromDB() {
        System.out.println("Loading animes:");
        List<Anime> dbanimes = new ArrayList<>();
        try {
            dbanimes = Anime.loadFromDB(db);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        Anime a = dbanimes.get(0);
        System.out.println(dbanimes.size());
        System.out.println(a.getSeasonList().size());
        System.out.println(a.getEpisodeCount());

        System.out.println(a.getDirectory());
        System.out.println("Unloaded: " + a.getUnloadedEpisodeCount());
    }

    @Override
    public WebSocketResponse handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        return switch (command) {
            case "getData" -> cmdGetData(socket);
            case "subscribe" -> cmdSubscribe(content);
            case "unsubscribe" -> cmdUnsubscribe(content);
            case "test" -> WebSocketResponse.ERROR.setMessage("Test Erfolgreich!");
            default -> {
                log.error("Invalid command " + command);
                yield WebSocketResponse.ERROR.setMessage("Invalid command " + command);
            }
        };
    }

    private WebSocketResponse cmdGetData(WebSocketBasic socket) {
        WebSocketUtils.sendAutoLoaderItem(socket, subscribedAnimes);
        return null;
    }

    private WebSocketResponse cmdSubscribe(JSONObjectContainer content) {
        String url = content.get("url", String.class);
        int languageId = content.get("languageId", Integer.class);

        if (subscribedAnimes.stream().anyMatch(a -> a.getUrl().equals(url)))
            return WebSocketResponse.ERROR.setMessage("Failed to subscribe to " + url + " this url is already subscribed!");

        log.debug("Adding subscriber " + url);
        String title = AniworldHelper.getAnimeTitle(url);
        log.debug("Resolved title: " + title);

        if (title == null)
            return WebSocketResponse.ERROR.setMessage("Failed to subscribe to " + url +  " cannot parse title!");

        Anime anime = new Anime(languageId, title, url);
        anime.loadMissingEpisodes();
        subscribedAnimes.add(anime);
        spCheckIntervalMin.trigger();
        anime.writeToDB(db);

        db.getExecutorHandler().awaitGroup(-1);

        // Inform everyone about the new item
        WebSocketUtils.sendAutoLoaderItem(null, anime);
        return WebSocketResponse.OK;
    }

    private WebSocketResponse cmdUnsubscribe(JSONObjectContainer content) {
        int id = content.get("id", Integer.class);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        if (optAnime.isPresent()) {
            spCheckIntervalMin.trigger();
            subscribedAnimes.remove(optAnime.get());
        } else {
            return WebSocketResponse.ERROR.setMessage("Tried to remove anime with id " + id + " but this does not exist.");
        }
        return WebSocketResponse.OK;
    }

    private void startThread() {
        Thread t = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    int checkInterval = checkIntervalMS.get();
                    ResultSet rs = db.executeQuery("select * from anime");
                    while (rs.next()) {
                        int id = rs.getInt("nKey");
                        Optional<Anime> optAnime = subscribedAnimes.stream().filter(a -> a.getId() == id).findFirst();
                        Anime anime;

                        if (optAnime.isEmpty())
                            anime = new Anime(rs);
                        else
                            anime = optAnime.get();

                        log.info("Scanning following anime: " + anime.getTitle());

                        anime.loadMissingEpisodes();

                        if (!passiveMode) {
                            for (Episode unloadedEpisode : anime.getUnloadedEpisodes()) {
                                JSONObjectContainer data = new JSONObjectContainer();
                                data.set("uuid", UUID.randomUUID());
                                data.set("state", "new");
                                data.set("url", unloadedEpisode.getUrl());
                                data.set("target", "stream-animes/"+anime.getDirectory().getName());

                                JSONObjectContainer options = new JSONObjectContainer();
                                options.set("useDirectMemory", false);
                                options.set("enableSessionRecovery", false);
                                options.set("enableSeasonAndEpisodeRenaming", true);

                                data.set("options", options);
                                defaultHandler.cmdPutData(data);
                            }
                        }

                        if (anime.isDirty()) {
                            anime.writeToDB(db);
                        }

                        log.info("Scan done, next in " + (checkInterval/1000) + "s");
                        Thread.sleep(checkInterval);
                    }
                    rs.close();
                    Thread.sleep(checkInterval);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    try {Thread.sleep(2000);} catch (Exception ee) {ee.printStackTrace();}
                    //log.error(ex.getMessage());
                }
            }
        });
        t.setName("AutoLoader");
        t.start();
    }

    private void loadDatabase() {
        try {
            JSONObjectContainer mysqlConfig = handlerConfiguration.getObjectContainer("mysql");
            db = new MySQLInterface(
                    mysqlConfig.get("host", String.class),
                    mysqlConfig.get("port", Integer.class),
                    mysqlConfig.get("username", String.class),
                    mysqlConfig.get("password", String.class),
                    mysqlConfig.get("database", String.class));
            db.asyncDataSettings(1);
            db.connect();

            executeDatabaseScripts();

            Anime.setCurrentID(getCurrentId("anime"));
            Season.setCurrentID(getCurrentId("season"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void executeDatabaseScripts() throws IOException {
        File sqlRootFolder = ResourceUtilities.getResourceFile("sql/");

        File[] subFolders = sqlRootFolder.listFiles();
        if (subFolders == null) return;

        for (File subFolder : subFolders) {
            if (!subFolder.isDirectory()) continue;
            File[] sqlFiles = subFolder.listFiles();
            if (sqlFiles == null) continue;

            for (File sqlFile : sqlFiles) {
                if (!sqlFile.getName().endsWith(".sql")) continue;
                log.info("Executing SQL Script: " + sqlFile.getName());
                try (FileInputStream fis = new FileInputStream(sqlFile)) {
                    db.executeAsync(-2, new String(StaticUtils.readAllBytes(fis)));
                }
            }
        }
        db.getExecutorHandler().awaitGroup(-2, 20);
    }

    private void printTableInfo(String... tables) {
        for (String table : tables) {
            try {
                ResultSet rs = db.executeQuery("select count(*) from " + table);
                rs.next();
                log.debug("Table " + table + " row count: " + rs.getInt(1));
                rs.close();
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

    private int getCurrentId(String table) {
        int result = 0;
        try {
            ResultSet rs = db.executeQuery("select nKey from " + table + " order by nKey desc");
            if (rs.next())
                result = rs.getInt(1);
            rs.close();
        } catch (SQLException ex) {
            log.error(ex.getMessage());
        }
        return result;
    }

}
