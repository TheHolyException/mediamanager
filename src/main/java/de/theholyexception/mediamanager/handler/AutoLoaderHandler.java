package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.datastorage.sql.interfaces.MySQLInterface;
import de.theholyexception.holyapi.util.DataUtils;
import de.theholyexception.holyapi.util.ResourceUtilities;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.TargetSystem;
import de.theholyexception.mediamanager.Utils;
import de.theholyexception.mediamanager.models.SettingMetadata;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.aniworld.Season;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class AutoLoaderHandler extends Handler {

    private SettingProperty<Integer> spCheckIntervalMin;
    private SettingProperty<Integer> spCheckDelayMs;
    private final List<Anime> subscribedAnimes = Collections.synchronizedList(new ArrayList<>());
    private DataBaseInterface db;
    private DefaultHandler defaultHandler;

    private SettingProperty<Boolean> spAutoDownload;
    private SettingProperty<Boolean> spEnabled;
    private final Random random = new Random();

    public AutoLoaderHandler(TargetSystem targetSystem) {
        super(targetSystem);
    }

    private static AtomicInteger threadCounter = new AtomicInteger(0);
    @Override
    public void loadConfigurations() {
        super.loadConfigurations();

        SettingProperty<Integer> spURLResolverThreads = new SettingProperty<>(new SettingMetadata("urlResolverThreads", false));
        spURLResolverThreads.addSubscriber(value -> {
            threadCounter.set(0);
            AniworldHelper.urlResolver.updateExecutorService(Executors.newFixedThreadPool(value, r -> {
                Thread thread = new Thread(r);
                thread.setName( "URL-Resolver-" + threadCounter.getAndAdd(1));
                return thread;
            }));
        });
        spURLResolverThreads.setValue(handlerConfiguration.get("urlResolverThreads", 10, Integer.class));

        spCheckIntervalMin = new SettingProperty<>(new SettingMetadata("checkIntervalMin", false));
        spCheckIntervalMin.setValue(handlerConfiguration.get("checkIntervalMin", 5, Integer.class));
        spCheckDelayMs = new SettingProperty<>(new SettingMetadata("checkDelayMs", false));
        spCheckDelayMs.setValue(handlerConfiguration.get("checkDelayMs", 5, Integer.class));
        spEnabled = new SettingProperty<>(new SettingMetadata("enabled", false));
        spEnabled.setValue(handlerConfiguration.get("enabled", true, Boolean.class));

        spAutoDownload = Settings.getSettingProperty("AUTO_DOWNLOAD", false, "systemSettings");
    }

    @Override
    public void initialize() {
        defaultHandler = (DefaultHandler) MediaManager.getInstance().getHandlers().get(TargetSystem.DEFAULT);
        Anime.setBaseDirectory(new File(defaultHandler.getTargets().get("stream-animes").path()));

        loadDatabase();

        subscribedAnimes.clear();
        subscribedAnimes.addAll(Anime.loadFromDB(db));

        if (log.isDebugEnabled())
            printTableInfo("anime", "season", "episode");

        subscribedAnimes.forEach(a -> {
            a.loadMissingEpisodes();
            a.scanDirectoryForExistingEpisodes();
            log.debug("Unloaded episodes for " + a.getTitle() + " : " + a.getUnloadedEpisodeCount(false));
            if (a.isDeepDirty())
                a.writeToDB(db);
        });
        db.getExecutorHandler().awaitGroup(-1);

        if (spEnabled.getValue())
            startThread();
    }

    //region commands
    @Override
    public WebSocketResponse handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        return switch (command) {
            case "getData" -> cmdGetData(socket);
            case "subscribe" -> cmdSubscribe(content);
            case "unsubscribe" -> cmdUnsubscribe(content);
            case "runDownload" -> cmdRunDownload(content);
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
        String directory = content.get("directory", null, String.class);
        if (directory.isEmpty()) directory = null;

        if (subscribedAnimes.stream().anyMatch(a -> a.getUrl().equals(url)))
            return WebSocketResponse.ERROR.setMessage("Failed to subscribe to " + url + " this url is already subscribed!");

        log.debug("Adding subscriber " + url);
        String title = AniworldHelper.getAnimeTitle(url);
        log.debug("Resolved title: " + title);

        if (title == null)
            return WebSocketResponse.ERROR.setMessage("Failed to subscribe to " + url +  " cannot parse title!");

        Anime anime = new Anime(languageId, title, url);
        anime.setDirectoryPath(directory, true);
        anime.loadMissingEpisodes();
        anime.scanDirectoryForExistingEpisodes();
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

    private WebSocketResponse cmdRunDownload(JSONObjectContainer content) {
        if (!spEnabled.getValue()) return WebSocketResponse.ERROR.setMessage("AutoLoader is disabled!");

        int animeId = content.get("id", Integer.class);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(a -> a.getId() == animeId).findFirst();
        if (optAnime.isEmpty()) return WebSocketResponse.ERROR.setMessage("Anime with id " + animeId + " not found!");

        runDownload(optAnime.get());
        return WebSocketResponse.OK;
    }
    //endregion

    private void startThread() {
        Thread t = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {

                    for (Anime anime : subscribedAnimes) {
                        log.info("Scanning anime: " + anime.getTitle());

                        anime.loadMissingEpisodes();

                        if (Boolean.TRUE.equals(spAutoDownload.getValue())) {
                            runDownload(anime);
                        }

                        Utils.sleep(spCheckDelayMs.getValue());
                    }

                    long sleepTime = spCheckIntervalMin.getValue()*1000L*60L;

                    sleepTime = random.nextLong(sleepTime * 3 / 4, sleepTime * 5 / 4);
                    log.info("Scan done, next in " + (sleepTime/1000) + "s");
                    Utils.sleep(sleepTime);
                } catch (Exception ex) {
                    log.error("", ex);
                    Utils.sleep(2000);
                }
            }
        });
        t.setName("AutoLoader");
        t.start();
    }

    private void runDownload(Anime anime) {
        for (Episode unloadedEpisode : anime.getUnloadedEpisodes()) {
            unloadedEpisode.setDownloading(true);
            try {
                unloadedEpisode.loadVideoURL(1, () -> {
                    JSONObjectContainer data = new JSONObjectContainer();
                    data.set("uuid", UUID.randomUUID().toString());
                    data.set("state", "new");
                    data.set("url", unloadedEpisode.getVideoUrl());
                    data.set("target", "stream-animes/"+anime.getDirectory().getName());
                    data.set("created", System.currentTimeMillis());
                    data.set("animeId", anime.getId());

                    JSONObjectContainer options = new JSONObjectContainer();
                    options.set("useDirectMemory", "false");
                    options.set("enableSessionRecovery", "false");
                    options.set("enableSeasonAndEpisodeRenaming", "true");

                    data.set("options", options.getRaw());
                    log.debug("Starting download of " + unloadedEpisode.getTitle());
                    defaultHandler.cmdPutData(data);
                });
            } catch (Exception ex) {
                log.error("", ex);
            } finally {
                unloadedEpisode.setDownloading(false);
            }
        }

        anime.writeToDB(db);
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
            db.asyncDataSettings(2);
            db.connect();

            if (Boolean.TRUE.equals(handlerConfiguration.get("executeDBScripts", false, Boolean.class))) {
                executeDatabaseScripts();
            }

            Anime.setCurrentID(getCurrentId("anime"));
            Season.setCurrentID(getCurrentId("season"));
        } catch (Exception ex) {
            log.error("", ex);
        }
    }


    private void executeDatabaseScripts() {
        try {
            List<String> files = ResourceUtilities.listResourceFilesRecursive("sql/");
            for (String file : files) {
                if (!file.endsWith(".sql")) continue;
                String content = new String(DataUtils.readAllBytes(ResourceUtilities.getResourceAsStream("sql/"+file)));
                String[] filePath = file.split("/");
                log.debug("Executing SQL Script: " + filePath[filePath.length-1]);
                db.executeAsync(-2, content);
            }
            db.getExecutorHandler().awaitGroup(-2, 20);
        } catch (Exception ex) {
            log.error("Failed to execute SQL Scripts", ex);
        }
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
            log.error("", ex);
        }
        return result;
    }

    public Anime getAnimeByID(int id) {
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        return optAnime.orElse(null);
    }

}
