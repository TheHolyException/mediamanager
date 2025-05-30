package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.*;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.aniworld.Season;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.util.AniworldProvider;
import de.theholyexception.mediamanager.util.TargetSystem;
import de.theholyexception.mediamanager.util.Utils;
import de.theholyexception.mediamanager.util.WebSocketResponseException;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.Executors;

import static de.theholyexception.mediamanager.MediaManager.getTomlConfig;

/**
 * Handler for automatic downloading of anime episodes.
 * Manages subscriptions to anime series, checks for new episodes, and handles
 * the automatic downloading of new content based on user preferences.
 */
@Slf4j
public class AutoLoaderHandler extends Handler {

    private final List<Anime> subscribedAnimes = Collections.synchronizedList(new ArrayList<>());
    private DefaultHandler defaultHandler;

    private SettingProperty<Boolean> spAutoDownload;
    private final Random random = new Random();
    private Boolean initialized = false;

    private long checkIntervalMin;
    private long checkDelayMs;
    private boolean enabled;

    public AutoLoaderHandler(TargetSystem targetSystem) {
        super(targetSystem);
    }

    /**
     * Loads and initializes configurations for the auto-loader.
     * Sets up thread pools, loads settings, and configures the auto-download behavior.
     */
    @Override
    public void loadConfigurations() {
        super.loadConfigurations();
        int urlResolverThreads = Math.toIntExact(getTomlConfig().getLong("autoloader.urlResolverThreads", () -> 10));
        AniworldHelper.urlResolver.updateExecutorService(Executors.newFixedThreadPool(urlResolverThreads));

        checkIntervalMin = getTomlConfig().getLong("autoloader.checkIntervalMin", () -> 60);
        checkDelayMs = getTomlConfig().getLong("autoloader.checkDelayMs", () -> 5000);
        enabled = Boolean.TRUE.equals(getTomlConfig().getBoolean("autoloader.enabled"));

        spAutoDownload = Settings.getSettingProperty("AUTO_DOWNLOAD", false, "systemSettings");
    }

    /**
     * Initializes the auto-loader after configurations are loaded.
     * Loads subscribed anime from the database, scans for existing episodes,
     * and starts the auto-download thread if enabled.
     */
    @Override
    public void initialize() {
        Thread t = new Thread(() -> {
            defaultHandler = (DefaultHandler) MediaManager.getInstance().getHandlers().get(TargetSystem.DEFAULT);
            Anime.setBaseDirectory(new File(defaultHandler.getTargets().get("stream-animes").path()));


            subscribedAnimes.clear();
            subscribedAnimes.addAll(Anime.loadFromDB(MediaManager.getInstance().getDb()));

            if (log.isDebugEnabled())
                printTableInfo("anime", "season", "episode");

            subscribedAnimes.forEach(a -> {
                a.loadMissingEpisodes();
                a.scanDirectoryForExistingEpisodes();
                log.debug("Unloaded episodes for " + a.getTitle() + " : " + a.getUnloadedEpisodeCount(false));
                if (a.isDeepDirty())
                    a.writeToDB(MediaManager.getInstance().getDb());
            });
            MediaManager.getInstance().getDb().getExecutorHandler().awaitGroup(-1);

            if (enabled)
                startThread();

            log.info("AutoLoader initialized");
            synchronized (this) {
                initialized = true;
                this.notifyAll();
            }
        });
        t.setName("AutoLoaderInit");
        t.start();
    }

    //region commands
    /**
     * Processes incoming WebSocket commands for the auto-loader.
     * Routes commands to the appropriate handler method based on the command type.
     *
     * @param socket The WebSocket connection that received the command
     * @param command The command to execute (e.g., "getData", "subscribe", "unsubscribe")
     * @param content JSON data associated with the command
     * @throws WebSocketResponseException if the command is invalid or processing fails
     */
    @Override
    public void handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        try {
            while (!initialized) {
                synchronized (this) {
                    this.wait(30000);
                    if (!initialized) {
                        throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("AutoLoader is not initialized yet"));
                    }
                }
            }
        } catch (InterruptedException ex) {
            return;
        }

        switch (command) {
            case "getData" -> cmdGetData(socket);
            case "subscribe" -> cmdSubscribe(content);
            case "unsubscribe" -> cmdUnsubscribe(content);
            case "runDownload" -> cmdRunDownload(content);
            case "getAlternateProviders" -> cmdGetAlternateProviders(socket, content);
            default ->
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid command " + command));
        }
    }

    /**
     * Handles the 'getData' command to retrieve the current state of subscribed anime.
     * Sends the list of all subscribed anime with their current status to the client.
     *
     * @param socket The WebSocket connection to send the data to
     */
    private void cmdGetData(WebSocketBasic socket) {
        WebSocketUtils.sendAutoLoaderItem(socket, subscribedAnimes);
    }

    /**
     * Handles the 'subscribe' command to add a new anime to the subscription list.
     * Validates the URL, resolves the anime title, and adds it to the database.
     *
     * @param content JSON data containing the anime URL, language ID, and other parameters
     * @throws WebSocketResponseException if the URL is invalid or already subscribed
     */
    private void cmdSubscribe(JSONObjectContainer content) {
        String url = content.get("url", String.class);
        int languageId = content.get("languageId", Integer.class);
        String directory = content.get("directory", null, String.class);
        if (directory.isEmpty()) directory = null;

        if (subscribedAnimes.stream().anyMatch(a -> a.getUrl().equals(url)))
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to subscribe to " + url + " this url is already subscribed!"));

        log.debug("Adding subscriber " + url);
        String title = AniworldHelper.getAnimeTitle(url);
        log.debug("Resolved title: " + title);

        if (title == null)
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to subscribe to " + url + " cannot resolve title!"));

        List<Integer> excludedSeasonList = new ArrayList<>();
        String excludedSeasonsString = content.get("excludedSeasons", String.class);
        if (excludedSeasonsString != null && !excludedSeasonsString.isEmpty()) {
            String[] excludedSeasons = content.get("excludedSeasons", String.class).split(",");
            for (String excludedSeason : excludedSeasons)
                excludedSeasonList.add(Integer.parseInt(excludedSeason));
        }



        Anime anime = new Anime(languageId, title, url, excludedSeasonList);
        anime.setDirectoryPath(directory, true);
        anime.loadMissingEpisodes();
        anime.scanDirectoryForExistingEpisodes();
        subscribedAnimes.add(anime);
        anime.writeToDB(MediaManager.getInstance().getDb());

        MediaManager.getInstance().getDb().getExecutorHandler().awaitGroup(-1);

        // Inform everyone about the new item
        WebSocketUtils.sendAutoLoaderItem(null, anime);
        throw new WebSocketResponseException(WebSocketResponse.OK);
    }

    @SuppressWarnings("unchecked")
    /**
     * Handles the 'unsubscribe' command to remove an anime from the subscription list.
     * Removes the specified anime from the database and notifies all connected clients.
     *
     * @param content JSON data containing the ID of the anime to unsubscribe from
     * @throws WebSocketResponseException if the anime ID is invalid
     */
    private void cmdUnsubscribe(JSONObjectContainer content) {
        int id = content.get("id", Integer.class);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        if (optAnime.isPresent()) {
            Anime anime = optAnime.get();
            MediaManager.getInstance().getDb().executeSafe("delete from anime where nKey = ?", id);
            subscribedAnimes.remove(anime);
            WebSocketUtils.sendAutoLoaderItem(null, subscribedAnimes);

            JSONObject payload = new JSONObject();
            payload.put("id", id);
            WebSocketUtils.sendPacket("del", TargetSystem.AUTOLOADER, payload, null);
        } else {
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to unsubscribe from " + id + " this id does not exist!"));
        }
        throw new WebSocketResponseException(WebSocketResponse.OK);
    }

    /**
     * Handles the 'runDownload' command to manually trigger a download for a specific anime.
     * Validates the episode information and adds it to the download queue.
     *
     * @param content JSON data containing the episode and download parameters
     * @throws WebSocketResponseException if the episode information is invalid
     */
    private void cmdRunDownload(JSONObjectContainer content) {
        if (!enabled)
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("AutoLoader is disabled!"));

        int animeId = content.get("id", Integer.class);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(a -> a.getId() == animeId).findFirst();
        if (optAnime.isEmpty())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Anime with id " + animeId + " not found!"));

        runDownload(optAnime.get());
        throw new WebSocketResponseException(WebSocketResponse.OK);
    }

    @SuppressWarnings("unchecked")
    /**
     * Handles the 'getAlternateProviders' command to retrieve alternative streaming sources for an episode.
     * Useful when the primary source is unavailable or has issues.
     *
     * @param socket The WebSocket connection to send the provider list to
     * @param content JSON data containing the episode information
     */
    private void cmdGetAlternateProviders(WebSocketBasic socket, JSONObjectContainer content) {
        Map<AniworldProvider, String> urls = getAlternativeProviders(content);

        JSONObject payload = new JSONObject();
        JSONArray array = new JSONArray();
        for (Map.Entry<AniworldProvider, String> entry : urls.entrySet()) {
            JSONObject object = new JSONObject();
            object.put("name", entry.getKey().getDisplayName());
            object.put("url", entry.getValue());
            array.add(object);
        }
        payload.put("providers", array);
        WebSocketUtils.sendPacket("getAlternateProvidersResponse", TargetSystem.AUTOLOADER, payload, socket);
    }
    //endregion



    /**
     * Starts the background thread that periodically checks for new episodes.
     * The thread runs in a loop, scanning each subscribed anime for new content
     * and initiating downloads when auto-download is enabled.
     */
    private void startThread() {
        Thread t = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    boolean autoLoad = Boolean.TRUE.equals(spAutoDownload.getValue());
                    for (Anime anime : subscribedAnimes) {
                        log.info("Scanning anime: " + anime.getTitle());

                        // Checks the episodes that do not have the requested language
                        // for an update of the language
                        for (Season season : anime.getSeasonList()) {
                            for (Episode episode : season.getEpisodeList()) {
                                if (episode.getLanguageIds().contains(anime.getLanguageId()))
                                    continue;
                                episode.activeScanLanguageIDs();
                            }
                        }

                        // Scans for new episodes that are not in our data structure
                        anime.loadMissingEpisodes();

                        // Actually runs the download
                        if (autoLoad)
                            runDownload(anime);

                        if (anime.isDeepDirty())
                            anime.writeToDB(MediaManager.getInstance().getDb());

                        Utils.sleep(checkDelayMs);

                        anime.updateLastUpdate(); // Update the lastUpdate variable
                    }

                    long sleepTime = checkIntervalMin*1000L*60L;

                    sleepTime = random.nextLong(sleepTime * 3 / 4, sleepTime * 5 / 4);
                    log.info("Scan done, next in " + (sleepTime/1000) + "s");
                    Utils.sleep(sleepTime);
                } catch (Exception ex) {
                    log.error("Failed to scan", ex);
                    log.error("Next Scan in 1h");
                    Utils.sleep(1000*60*60*2);
                }
            }
        });
        t.setName("AutoLoader");
        t.start();
    }

    /**
     * Initiates downloads for all unloaded episodes of a specific anime.
     * Creates download tasks for each missing episode and adds them to the download queue.
     *
     * @param anime The anime for which to download unloaded episodes
     */
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

                    JSONObjectContainer autoloaderData = new JSONObjectContainer();
                    autoloaderData.set("animeId", anime.getId());
                    autoloaderData.set("seasonId", unloadedEpisode.getSeason().getId());
                    autoloaderData.set("episodeId", unloadedEpisode.getId());
                    data.set("autoloaderData", autoloaderData.getRaw());

                    JSONObjectContainer options = new JSONObjectContainer();
                    options.set("useDirectMemory", "false");
                    options.set("enableSessionRecovery", "false");
                    options.set("enableSeasonAndEpisodeRenaming", "true");
                    data.set("options", options.getRaw());

                    JSONObjectContainer payload = new JSONObjectContainer();
                    JSONArrayContainer list = new JSONArrayContainer();
                    list.add(data);
                    payload.set("list", list);
                    defaultHandler.cmdPutData(null, payload);
                });
            } catch (Exception ex) {
                log.error("", ex);
            } finally {
                unloadedEpisode.setDownloading(false);
            }
        }

        anime.writeToDB(MediaManager.getInstance().getDb());
    }

    /**
     * Prints debug information about database tables.
     * Logs the row count for each specified table when debug logging is enabled.
     *
     * @param tables The names of the tables to print information about
     */
    private void printTableInfo(String... tables) {
        for (String table : tables) {
            try {
                ResultSet rs = MediaManager.getInstance().getDb().executeQuery("select count(*) from " + table);
                rs.next();
                log.debug("Table " + table + " row count: " + rs.getInt(1));
                rs.close();
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

    /**
     * Retrieves an anime by its ID from the list of subscribed animes.
     *
     * @param id The ID of the anime to find
     * @return The Anime object if found, or null if no anime with the given ID exists
     */
    public Anime getAnimeByID(int id) {
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        return optAnime.orElse(null);
    }
    /**
     * Retrieves an episode using the data from an autoloader payload.
     *
     * @param autoloaderData JSON data containing anime, season, and episode identifiers
     * @return The matching Episode object
     * @throws WebSocketResponseException if the episode is not found
     */
    public Episode getEpisodeFromAutoloaderData(JSONObjectContainer autoloaderData) {
        int animeId = autoloaderData.get("animeId", Integer.class);
        int seasonId = autoloaderData.get("seasonId", Integer.class);
        int episodeId = autoloaderData.get("episodeId", Integer.class);

        return getEpisodeByPath(animeId, seasonId, episodeId);
    }

    /**
     * Retrieves a specific episode using its path components.
     *
     * @param animeId The ID of the anime
     * @param seasonId The ID of the season
     * @param episodeId The ID of the episode
     * @return The matching Episode object
     * @throws WebSocketResponseException if any component of the path is not found
     */
    public Episode getEpisodeByPath(int animeId, int seasonId, int episodeId) {
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == animeId).findFirst();
        if (optAnime.isEmpty())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Anime with id " + animeId + " not found!"));

        Optional<Season> optSeason = optAnime.get().getSeasonList().stream().filter(season -> season.getId() == seasonId).findFirst();
        if (optSeason.isEmpty())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Season with id " + seasonId + " not found!"));

        Optional<Episode> optEpisode = optSeason.get().getEpisodeList().stream().filter(ep -> ep.getId() == episodeId).findFirst();
        if (optEpisode.isEmpty())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Episode with id " + episodeId + " not found!"));

        return optEpisode.get();
    }

    /**
     * Retrieves alternative streaming providers for a given episode.
     *
     * @param autoloaderData JSON data containing the episode information
     * @return A map of provider names to their respective URLs
     * @throws WebSocketResponseException if the anime or episode is not found
     */
    public Map<AniworldProvider, String> getAlternativeProviders(JSONObjectContainer autoloaderData) {
        int animeId = autoloaderData.get("animeId", Integer.class);
        int seasonId = autoloaderData.get("seasonId", Integer.class);
        int episodeId = autoloaderData.get("episodeId", Integer.class);

        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == animeId).findFirst();
        if (optAnime.isEmpty())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Anime with id " + animeId + " not found!"));

        Episode episode = getEpisodeByPath(animeId, seasonId, episodeId);
        if (episode.getVideoUrl() == null)
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Episode has no video url!"));

        AniworldProvider provider = AniworldProvider.getProvider(episode.getVideoUrl());

        Map<AniworldProvider, String> urls = AniworldHelper.resolveAlternateVideoURLs(episode, optAnime.get().getLanguageId(), provider);
        Map<AniworldProvider, String> episodeAlternateVideoUrls = episode.getAlternateVideoURLs();
        synchronized (episodeAlternateVideoUrls) {
            episodeAlternateVideoUrls.clear();
            episodeAlternateVideoUrls.putAll(urls);
        }
        return urls;
    }

}
