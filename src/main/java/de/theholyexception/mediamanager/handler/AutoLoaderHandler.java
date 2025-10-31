package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.datastorage.sql.interfaces.MySQLInterface;
import de.theholyexception.holyapi.di.DIInject;
import de.theholyexception.holyapi.util.ExecutorHandler;
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
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.*;
import io.javalin.websocket.WsContext;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Handler for automatic downloading of anime episodes.
 * Manages subscriptions to anime series, checks for new episodes, and handles
 * the automatic downloading of new content based on user preferences.
 */
@Slf4j
public class AutoLoaderHandler extends Handler {

    private final List<Anime> subscribedAnimes = Collections.synchronizedList(new ArrayList<>());

    private SettingProperty<Boolean> spAutoDownload;
    private final Random random = new Random();
    private Boolean initialized = false;

    private long checkIntervalMin;
    private long checkDelayMs;
    private boolean enabled;
    
    @DIInject
    private MySQLInterface db;

    @DIInject
    private DefaultHandler defaultHandler;

    public AutoLoaderHandler() {
        super(TargetSystem.AUTOLOADER);
    }

    /**
     * Loads and initializes configurations for the auto-loader.
     * Sets up thread pools, loads settings, and configures the auto-download behavior.
     */
    @Override
    public void loadConfigurations() {
        super.loadConfigurations();
        int urlResolverThreads = Math.toIntExact(config.getLong("autoloader.urlResolverThreads", () -> 10));
        AniworldHelper.urlResolver.updateExecutorService(Executors.newFixedThreadPool(urlResolverThreads));

        checkIntervalMin = config.getLong("autoloader.checkIntervalMin", () -> 60);
        checkDelayMs = config.getLong("autoloader.checkDelayMs", () -> 5000);
        enabled = Boolean.TRUE.equals(config.getBoolean("autoloader.enabled"));

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
            Anime.setBaseDirectory(new File(defaultHandler.getTargets().get("stream-animes").path()));

            subscribedAnimes.clear();
            subscribedAnimes.addAll(Anime.loadFromDB(db));

            ExecutorHandler handler = new ExecutorHandler(Executors.newFixedThreadPool(10));
            subscribedAnimes.forEach(a ->
                handler.putTask(() -> {
                    a.loadMissingEpisodes();
                    a.scanDirectoryForExistingEpisodes();
                    log.debug("Unloaded episodes for " + a.getTitle() + " : " + a.getUnloadedEpisodeCount(false));
                    if (a.isDeepDirty())
                        a.writeToDB(db);
                }, 1)
            );
            handler.awaitGroup(1);
            db.getExecutorHandler().awaitGroup(-1);

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
     * @param ctx The WebSocket connection that received the command
     * @param command The command to execute (e.g., "getData", "subscribe", "unsubscribe")
     * @param content JSON data associated with the command
     * @throws WebSocketResponseException if the command is invalid or processing fails
     */
    @Override
    public void handleCommand(WsContext ctx, String command, JSONObjectContainer content) {
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
            case "getData" -> cmdGetData(ctx);
            case "subscribe" -> cmdSubscribe(content);
            case "unsubscribe" -> cmdUnsubscribe(content);
            case "modify" -> cmdModify(content);
            case "runDownload" -> cmdRunDownload(content);
            case "getAlternateProviders" -> cmdGetAlternateProviders(ctx, content);
            case "pause" -> cmdPause(content);
            case "resume" -> cmdResume(content);
            case "scan" -> cmdScan(content);
            default ->
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid command " + command));
        }
    }

    /**
     * Handles the 'getData' command to retrieve the current state of subscribed anime.
     * Sends the list of all subscribed anime with their current status to the client.
     *
     * @param ctx The WebSocket connection to send the data to
     */
    private void cmdGetData(WsContext ctx) {
        WebSocketUtils.sendAutoLoaderItem(ctx, subscribedAnimes);
    }

    /**
     * Handles the 'subscribe' command to add a new anime to the subscription list.
     * Validates the URL, resolves the anime title, and adds it to the database.
     *
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
        anime.writeToDB(db);

        db.getExecutorHandler().awaitGroup(-1);

        // Inform everyone about the new item
        WebSocketUtils.sendAutoLoaderItem(null, subscribedAnimes);
        throw new WebSocketResponseException(WebSocketResponse.OK);
    }

    /**
     * Handles the 'unsubscribe' command to remove an anime from the subscription list.
     * Removes the specified anime from the database and notifies all connected clients.
     *
     * @param content JSON data containing the ID of the anime to unsubscribe from
     * @throws WebSocketResponseException if the anime ID is invalid
     */
    @SuppressWarnings("unchecked")
    private void cmdUnsubscribe(JSONObjectContainer content) {
        int id = content.get("id", Integer.class);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        if (optAnime.isPresent()) {
            Anime anime = optAnime.get();
            db.executeSafe("delete from anime where nKey = ?", id);
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
     * Handles the 'modify' command to update an existing anime subscription.
     * Updates the anime's properties like directory. Note: language and excluded seasons
     * cannot be modified as they are immutable properties set during construction.
     *
     * @param content JSON data containing the ID and updated properties of the anime
     * @throws WebSocketResponseException if the anime ID is invalid
     */
    private void cmdModify(JSONObjectContainer content) {
        log.info("cmdModify called with content: {}", content.getRaw());
        int id = content.get("id", Integer.class);
        log.info("Looking for anime with ID: {}", id);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        
        if (optAnime.isEmpty()) {
            log.error("Anime with ID {} not found in subscribedAnimes list", id);
            log.info("Available anime IDs: {}", subscribedAnimes.stream().map(Anime::getId).toList());
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to modify subscription " + id + " - anime not found!"));
        }
        
        log.info("Found anime: {}", optAnime.get().getTitle());
        
        Anime anime = optAnime.get();
        boolean modified = false;
        
        // Update language ID if provided
        if (content.getRaw().containsKey("languageId")) {
            int newLanguageId = content.get("languageId", Integer.class);
            if (anime.getLanguageId() != newLanguageId) {
                anime.setLanguageId(newLanguageId, true);
                modified = true;
                log.info("Updated language ID for '{}' from {} to {}", anime.getTitle(), anime.getLanguageId(), newLanguageId);
            }
        }
        
        // Update excluded seasons if provided
        if (content.getRaw().containsKey("excludedSeasons")) {
            List<Integer> newExcludedSeasons = new ArrayList<>();
            String excludedSeasonsString = content.get("excludedSeasons", String.class);
            if (excludedSeasonsString != null && !excludedSeasonsString.isEmpty()) {
                String[] excludedSeasons = excludedSeasonsString.split(",");
                for (String excludedSeason : excludedSeasons) {
                    try {
                        newExcludedSeasons.add(Integer.parseInt(excludedSeason.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid excluded season number: " + excludedSeason);
                    }
                }
            }
            
            if (!anime.getExcludedSeasons().equals(newExcludedSeasons)) {
                anime.setExcludedSeasons(newExcludedSeasons, true);
                modified = true;
                log.info("Updated excluded seasons for '{}' to: {}", anime.getTitle(), newExcludedSeasons);
            }
        }
        
        // Update directory if provided
        if (content.getRaw().containsKey("directory")) {
            String newDirectory = content.get("directory", null, String.class);
            if (newDirectory != null && newDirectory.isEmpty()) newDirectory = null;
            
            String currentDirectory = anime.getDirectory() != null ? anime.getDirectory().getName() : null;
            if ((currentDirectory == null && newDirectory != null) || 
                (currentDirectory != null && !currentDirectory.equals(newDirectory))) {
                anime.setDirectoryPath(newDirectory, true);
                modified = true;
            }
        }
        
        if (modified) {
            log.info("Changes detected, applying modifications for: {}", anime.getTitle());
            // Rescan episodes after modifications
            anime.loadMissingEpisodes();
            anime.scanDirectoryForExistingEpisodes();
            anime.writeToDB(db);
            
            // Notify all clients about the update
            WebSocketUtils.sendAutoLoaderItem(null, subscribedAnimes);
            
            log.info("Successfully modified subscription for: {}", anime.getTitle());
        } else {
            log.info("No changes detected for: {}", anime.getTitle());
        }
        
        log.info("cmdModify completed successfully");
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

        log.debug("Running download for: {}", optAnime.get().getTitle());
        runDownload(optAnime.get());
        throw new WebSocketResponseException(WebSocketResponse.OK);
    }

    /**
     * Handles the 'getAlternateProviders' command to retrieve alternative streaming sources for an episode.
     * Useful when the primary source is unavailable or has issues.
     *
     * @param ctx The WebSocket connection to send the provider list to
     * @param content JSON data containing the episode information
     */
    @SuppressWarnings("unchecked")
    private void cmdGetAlternateProviders(WsContext ctx, JSONObjectContainer content) {
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
        WebSocketUtils.sendPacket("getAlternateProvidersResponse", TargetSystem.AUTOLOADER, payload, ctx);
    }

    /**
     * Handles the 'pause' command to pause an anime subscription.
     * When paused, the anime will not be included in automatic scanning and downloads.
     *
     * @param content JSON data containing the ID of the anime to pause
     * @throws WebSocketResponseException if the anime ID is invalid
     */
    private void cmdPause(JSONObjectContainer content) {
        int id = content.get("id", Integer.class);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        if (optAnime.isEmpty()) {
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Anime with id " + id + " not found!"));
        }

        Anime anime = optAnime.get();
        if (!anime.isPaused()) {
            anime.setPaused(true, true);
            anime.writeToDB(db);
            
            log.info("Paused anime subscription: {}", anime.getTitle());
            
            // Notify all clients about the update
            WebSocketUtils.sendAutoLoaderItem(null, subscribedAnimes);
        }
        
        throw new WebSocketResponseException(WebSocketResponse.OK);
    }

    /**
     * Handles the 'resume' command to resume a paused anime subscription.
     * Once resumed, the anime will be included in automatic scanning and downloads again.
     *
     * @param content JSON data containing the ID of the anime to resume
     * @throws WebSocketResponseException if the anime ID is invalid
     */
    private void cmdResume(JSONObjectContainer content) {
        int id = content.get("id", Integer.class);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        if (optAnime.isEmpty()) {
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Anime with id " + id + " not found!"));
        }

        Anime anime = optAnime.get();
        if (anime.isPaused()) {
            anime.setPaused(false, true);
            anime.writeToDB(db);
            
            log.info("Resumed anime subscription: {}", anime.getTitle());
            
            // Notify all clients about the update
            WebSocketUtils.sendAutoLoaderItem(null, subscribedAnimes);
        }
        
        throw new WebSocketResponseException(WebSocketResponse.OK);
    }

    /**
     * Handles the 'scan' command to manually scan for new episodes of a specific anime.
     * Performs the same scanning logic as the automatic scanner but for a single anime.
     *
     * @param content JSON data containing the ID of the anime to scan
     * @throws WebSocketResponseException if the anime ID is invalid
     */
    private void cmdScan(JSONObjectContainer content) {
        int id = content.get("id", Integer.class);
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
        if (optAnime.isEmpty()) {
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Anime with id " + id + " not found!"));
        }

        Anime anime = optAnime.get();
        
        log.info("Manual scan requested for anime: {}", anime.getTitle());
        
        try {
            // Check for language updates on existing episodes
            for (Season season : anime.getSeasonList()) {
                for (Episode episode : season.getEpisodeList()) {
                    if (episode.getLanguageIds().contains(anime.getLanguageId()))
                        continue;
                    episode.activeScanLanguageIDs();
                }
            }

            // Scan for new episodes that are not in our data structure
            anime.loadMissingEpisodes();
            
            // Scan directory for existing downloaded episodes
            anime.scanDirectoryForExistingEpisodes();

            if (anime.isDeepDirty()) {
                anime.writeToDB(db);
            }
            
            // Update the last scan time
            anime.updateLastUpdate();

            log.info("Manual scan completed for anime: {} - Found {} unloaded episodes", 
                    anime.getTitle(), anime.getUnloadedEpisodeCount(true));

            // Notify all clients about the update
            WebSocketUtils.sendAutoLoaderItem(null, subscribedAnimes);
            
        } catch (Exception ex) {
            log.error("Error during manual scan for anime: {}", anime.getTitle(), ex);
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Scan failed: " + ex.getMessage()));
        }
        
        throw new WebSocketResponseException(WebSocketResponse.OK);
    }

    @OpenApi(
        summary = "Get all subscriptions",
        description = "Retrieves the list of all anime subscriptions with their current status",
        operationId = "getSubscriptions", 
        path = "/api/autoloader/subscriptions",
        tags = {"AutoLoader"},
        methods = HttpMethod.GET,
        responses = {
            @OpenApiResponse(status = "200", description = "List of subscriptions"),
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void getSubscriptions(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }
        
        JSONArray animeArray = new JSONArray();
        synchronized (subscribedAnimes) {
            for (Anime anime : subscribedAnimes) {
                animeArray.add(anime.toJSONObject());
            }
        }
        
        ctx.json(animeArray);
    }

    @OpenApi(
        summary = "Subscribe to anime",
        description = "Adds a new anime to the subscription list",
        operationId = "subscribe",
        path = "/api/autoloader/subscriptions", 
        tags = {"AutoLoader"},
        methods = HttpMethod.POST,
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(from = JSONObjectContainer.class),
            description = "Subscription data including URL, language ID, directory, and excluded seasons"
        ),
        responses = {
            @OpenApiResponse(status = "201", description = "Successfully subscribed"),
            @OpenApiResponse(status = "400", description = "Invalid request data"),
            @OpenApiResponse(status = "409", description = "Already subscribed to this URL"),
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void subscribe(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }

        try {
            JSONObjectContainer content = (JSONObjectContainer) JSONReader.readString(ctx.body());
            
            String url = content.get("url", String.class);
            int languageId = content.get("languageId", Integer.class);
            String directory = content.get("directory", null, String.class);
            if (directory != null && directory.isEmpty()) directory = null;

            if (subscribedAnimes.stream().anyMatch(a -> a.getUrl().equals(url))) {
                ctx.status(HttpStatus.CONFLICT);
                ctx.json(Map.of("error", "Already subscribed to " + url));
                return;
            }

            log.debug("Adding subscriber " + url);
            String title = AniworldHelper.getAnimeTitle(url);
            log.debug("Resolved title: " + title);

            if (title == null) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(Map.of("error", "Cannot resolve anime title from " + url));
                return;
            }

            List<Integer> excludedSeasonList = new ArrayList<>();
            String excludedSeasonsString = content.get("excludedSeasons", String.class);
            if (excludedSeasonsString != null && !excludedSeasonsString.isEmpty()) {
                String[] excludedSeasons = excludedSeasonsString.split(",");
                for (String excludedSeason : excludedSeasons) {
                    try {
                        excludedSeasonList.add(Integer.parseInt(excludedSeason.trim()));
                    } catch (NumberFormatException e) {
                        ctx.status(HttpStatus.BAD_REQUEST);
                        ctx.json(Map.of("error", "Invalid excluded season number: " + excludedSeason));
                        return;
                    }
                }
            }

            Anime anime = new Anime(languageId, title, url, excludedSeasonList);
            anime.setDirectoryPath(directory, true);
            anime.loadMissingEpisodes();
            anime.scanDirectoryForExistingEpisodes();
            subscribedAnimes.add(anime);
            anime.writeToDB(db);

            db.getExecutorHandler().awaitGroup(-1);

            // Notify all WebSocket clients that subscriptions have changed
            notifyDataChanged("subscriptions");
            
            ctx.status(HttpStatus.CREATED);
            ctx.json(anime.toJSONObject());
        } catch (Exception ex) {
            log.error("Error subscribing to anime", ex);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("error", ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Modify subscription",
        description = "Updates properties of an existing anime subscription",
        operationId = "modifySubscription",
        path = "/api/autoloader/subscriptions/{id}",
        tags = {"AutoLoader"},
        methods = HttpMethod.PUT,
        pathParams = @OpenApiParam(name = "id", description = "The subscription ID", required = true),
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(from = JSONObjectContainer.class),
            description = "Updated subscription properties"
        ),
        responses = {
            @OpenApiResponse(status = "200", description = "Successfully modified"),
            @OpenApiResponse(status = "404", description = "Subscription not found"),
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void modifySubscription(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }

        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            JSONObjectContainer content = (JSONObjectContainer) JSONReader.readString(ctx.body());
            
            Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
            if (optAnime.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", "Subscription with id " + id + " not found"));
                return;
            }
            
            Anime anime = optAnime.get();
            boolean modified = false;
            
            if (content.getRaw().containsKey("languageId")) {
                int newLanguageId = content.get("languageId", Integer.class);
                if (anime.getLanguageId() != newLanguageId) {
                    anime.setLanguageId(newLanguageId, true);
                    modified = true;
                }
            }
            
            if (content.getRaw().containsKey("excludedSeasons")) {
                List<Integer> newExcludedSeasons = new ArrayList<>();
                String excludedSeasonsString = content.get("excludedSeasons", String.class);
                if (excludedSeasonsString != null && !excludedSeasonsString.isEmpty()) {
                    String[] excludedSeasons = excludedSeasonsString.split(",");
                    for (String excludedSeason : excludedSeasons) {
                        try {
                            newExcludedSeasons.add(Integer.parseInt(excludedSeason.trim()));
                        } catch (NumberFormatException e) {
                            ctx.status(HttpStatus.BAD_REQUEST);
                            ctx.json(Map.of("error", "Invalid excluded season number: " + excludedSeason));
                            return;
                        }
                    }
                }
                
                if (!anime.getExcludedSeasons().equals(newExcludedSeasons)) {
                    anime.setExcludedSeasons(newExcludedSeasons, true);
                    modified = true;
                }
            }
            
            if (content.getRaw().containsKey("directory")) {
                String newDirectory = content.get("directory", null, String.class);
                if (newDirectory != null && newDirectory.isEmpty()) newDirectory = null;
                
                String currentDirectory = anime.getDirectory() != null ? anime.getDirectory().getName() : null;
                if ((currentDirectory == null && newDirectory != null) || 
                    (currentDirectory != null && !currentDirectory.equals(newDirectory))) {
                    anime.setDirectoryPath(newDirectory, true);
                    modified = true;
                }
            }
            
            if (modified) {
                anime.loadMissingEpisodes();
                anime.scanDirectoryForExistingEpisodes();
                anime.writeToDB(db);
                
                // Notify all WebSocket clients that subscriptions have changed
                notifyDataChanged("subscriptions");
            }
            
            ctx.json(anime.toJSONObject());
        } catch (NumberFormatException ex) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("error", "Invalid subscription ID"));
        } catch (Exception ex) {
            log.error("Error modifying subscription", ex);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("error", ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Unsubscribe from anime",
        description = "Removes an anime from the subscription list",
        operationId = "unsubscribe",
        path = "/api/autoloader/subscriptions/{id}",
        tags = {"AutoLoader"},
        methods = HttpMethod.DELETE,
        pathParams = @OpenApiParam(name = "id", description = "The subscription ID", required = true),
        responses = {
            @OpenApiResponse(status = "204", description = "Successfully unsubscribed"),
            @OpenApiResponse(status = "404", description = "Subscription not found"),
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void unsubscribe(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }

        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
            
            if (optAnime.isPresent()) {
                Anime anime = optAnime.get();
                db.executeSafe("delete from anime where nKey = ?", id);
                subscribedAnimes.remove(anime);
                
                // Notify all WebSocket clients that subscriptions have changed
                notifyDataChanged("subscriptions");
                
                ctx.status(HttpStatus.NO_CONTENT);
            } else {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", "Subscription with id " + id + " not found"));
            }
        } catch (NumberFormatException ex) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("error", "Invalid subscription ID"));
        } catch (Exception ex) {
            log.error("Error unsubscribing", ex);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("error", ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Download episodes",
        description = "Manually triggers downloads for all unloaded episodes of a subscription",
        operationId = "runDownload",
        path = "/api/autoloader/subscriptions/{id}/download",
        tags = {"AutoLoader"},
        methods = HttpMethod.POST,
        pathParams = @OpenApiParam(name = "id", description = "The subscription ID", required = true),
        responses = {
            @OpenApiResponse(status = "200", description = "Download initiated"),
            @OpenApiResponse(status = "404", description = "Subscription not found"),
            @OpenApiResponse(status = "503", description = "AutoLoader disabled or not initialized")
        }
    )
    private void runDownload(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }

        if (!enabled) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is disabled"));
            return;
        }

        try {
            int animeId = Integer.parseInt(ctx.pathParam("id"));
            Optional<Anime> optAnime = subscribedAnimes.stream().filter(a -> a.getId() == animeId).findFirst();
            
            if (optAnime.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", "Anime with id " + animeId + " not found"));
                return;
            }

            runDownload(optAnime.get());
            ctx.json(Map.of("message", "Download initiated for " + optAnime.get().getTitle()));
        } catch (NumberFormatException ex) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("error", "Invalid subscription ID"));
        } catch (Exception ex) {
            log.error("Error running download", ex);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("error", ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Get alternate providers",
        description = "Retrieves alternative streaming providers for a specific episode",
        operationId = "getAlternateProviders",
        path = "/api/autoloader/subscriptions/{id}/providers",
        tags = {"AutoLoader"},
        methods = HttpMethod.GET,
        pathParams = @OpenApiParam(name = "id", description = "The subscription ID", required = true),
        queryParams = {
            @OpenApiParam(name = "seasonId", description = "The season ID", required = true),
            @OpenApiParam(name = "episodeId", description = "The episode ID", required = true)
        },
        responses = {
            @OpenApiResponse(status = "200", description = "Alternative providers list"),
            @OpenApiResponse(status = "400", description = "Missing required parameters"),
            @OpenApiResponse(status = "404", description = "Subscription, season, or episode not found"),
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void getAlternateProviders(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }

        try {
            int animeId = Integer.parseInt(ctx.pathParam("id"));
            String seasonIdParam = ctx.queryParam("seasonId");
            String episodeIdParam = ctx.queryParam("episodeId");
            
            if (seasonIdParam == null || episodeIdParam == null) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(Map.of("error", "Missing required query parameters: seasonId and episodeId"));
                return;
            }
            
            int seasonId = Integer.parseInt(seasonIdParam);
            int episodeId = Integer.parseInt(episodeIdParam);
            
            // Create the autoloader data structure that the existing method expects
            JSONObjectContainer autoloaderData = new JSONObjectContainer();
            autoloaderData.set("animeId", animeId);
            autoloaderData.set("seasonId", seasonId);
            autoloaderData.set("episodeId", episodeId);
            
            // Use the existing method to get providers
            Map<AniworldProvider, String> urls = getAlternativeProviders(autoloaderData);
            
            // Convert to JSON response format
            JSONArray providersArray = new JSONArray();
            for (Map.Entry<AniworldProvider, String> entry : urls.entrySet()) {
                JSONObject providerObj = new JSONObject();
                providerObj.put("name", entry.getKey().getDisplayName());
                providerObj.put("url", entry.getValue());
                providersArray.add(providerObj);
            }
            
            ctx.json(Map.of("providers", providersArray));
            
        } catch (NumberFormatException ex) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("error", "Invalid ID parameter - must be a number"));
        } catch (Exception ex) {
            log.error("Error getting alternate providers", ex);
            if (ex.getMessage().contains("not found")) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", ex.getMessage()));
            } else {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
                ctx.json(Map.of("error", ex.getMessage()));
            }
        }
    }

    @OpenApi(
        summary = "Pause subscription",
        description = "Pauses an anime subscription to stop automatic scanning and downloads",
        operationId = "pauseSubscription",
        path = "/api/autoloader/subscriptions/{id}/pause",
        tags = {"AutoLoader"},
        methods = HttpMethod.POST,
        pathParams = @OpenApiParam(name = "id", description = "The subscription ID", required = true),
        responses = {
            @OpenApiResponse(status = "200", description = "Subscription paused"),
            @OpenApiResponse(status = "404", description = "Subscription not found"),
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void pauseSubscription(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }

        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
            
            if (optAnime.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", "Anime with id " + id + " not found"));
                return;
            }

            Anime anime = optAnime.get();
            if (!anime.isPaused()) {
                anime.setPaused(true, true);
                anime.writeToDB(db);
                log.info("Paused anime subscription: {}", anime.getTitle());
                
                // Notify all WebSocket clients that subscriptions have changed
                notifyDataChanged("subscriptions");
            }
            
            ctx.json(Map.of("message", "Subscription paused", "anime", anime.toJSONObject()));
        } catch (NumberFormatException ex) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("error", "Invalid subscription ID"));
        } catch (Exception ex) {
            log.error("Error pausing subscription", ex);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("error", ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Resume subscription",
        description = "Resumes a paused anime subscription to restart automatic scanning and downloads",
        operationId = "resumeSubscription",
        path = "/api/autoloader/subscriptions/{id}/resume",
        tags = {"AutoLoader"},
        methods = HttpMethod.POST,
        pathParams = @OpenApiParam(name = "id", description = "The subscription ID", required = true),
        responses = {
            @OpenApiResponse(status = "200", description = "Subscription resumed"),
            @OpenApiResponse(status = "404", description = "Subscription not found"),
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void resumeSubscription(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }

        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
            
            if (optAnime.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", "Anime with id " + id + " not found"));
                return;
            }

            Anime anime = optAnime.get();
            if (anime.isPaused()) {
                anime.setPaused(false, true);
                anime.writeToDB(db);
                log.info("Resumed anime subscription: {}", anime.getTitle());
                
                // Notify all WebSocket clients that subscriptions have changed
                notifyDataChanged("subscriptions");
            }
            
            ctx.json(Map.of("message", "Subscription resumed", "anime", anime.toJSONObject()));
        } catch (NumberFormatException ex) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("error", "Invalid subscription ID"));
        } catch (Exception ex) {
            log.error("Error resuming subscription", ex);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("error", ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Scan subscription",
        description = "Manually scans for new episodes of a specific anime subscription",
        operationId = "scanSubscription",
        path = "/api/autoloader/subscriptions/{id}/scan",
        tags = {"AutoLoader"},
        methods = HttpMethod.POST,
        pathParams = @OpenApiParam(name = "id", description = "The subscription ID", required = true),
        responses = {
            @OpenApiResponse(status = "200", description = "Scan completed"),
            @OpenApiResponse(status = "404", description = "Subscription not found"),
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void scanSubscription(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
            return;
        }

        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == id).findFirst();
            
            if (optAnime.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(Map.of("error", "Anime with id " + id + " not found"));
                return;
            }

            Anime anime = optAnime.get();
            log.info("Manual scan requested for anime: {}", anime.getTitle());
            
            // Check for language updates on existing episodes
            for (Season season : anime.getSeasonList()) {
                for (Episode episode : season.getEpisodeList()) {
                    if (episode.getLanguageIds().contains(anime.getLanguageId()))
                        continue;
                    episode.activeScanLanguageIDs();
                }
            }

            // Scan for new episodes that are not in our data structure
            anime.loadMissingEpisodes();
            
            // Scan directory for existing downloaded episodes
            anime.scanDirectoryForExistingEpisodes();

            if (anime.isDeepDirty()) {
                anime.writeToDB(db);
            }
            
            // Update the last scan time
            anime.updateLastUpdate();

            int unloadedCount = anime.getUnloadedEpisodeCount(true);
            log.info("Manual scan completed for anime: {} - Found {} unloaded episodes", 
                    anime.getTitle(), unloadedCount);
            
            // Notify all WebSocket clients that subscriptions have changed
            notifyDataChanged("subscriptions");
            
            ctx.json(Map.of(
                "message", "Scan completed", 
                "anime", anime.toJSONObject(),
                "unloadedEpisodes", unloadedCount
            ));
        } catch (NumberFormatException ex) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("error", "Invalid subscription ID"));
        } catch (Exception ex) {
            log.error("Error scanning subscription", ex);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Notifies all WebSocket clients that subscription data has changed.
     * This can be used to notify clients when any data has been updated via REST API
     * so they can refetch the updated data.
     *
     * @param dataType The type of data that changed (e.g., "subscriptions")
     */
    private void notifyDataChanged(String dataType) {
        JSONObject notification = new JSONObject();
        notification.put("dataType", dataType);
        notification.put("timestamp", System.currentTimeMillis());
        WebSocketUtils.sendPacket("data-changed", TargetSystem.AUTOLOADER, notification, null);
    }
    //endregion

    //region REST API
    @Override
    public void registerAPI(Javalin app) {
        app.get("/api/autoloader/subscriptions", this::getSubscriptions);
        app.post("/api/autoloader/subscriptions", this::subscribe);
        app.put("/api/autoloader/subscriptions/{id}", this::modifySubscription);
        app.delete("/api/autoloader/subscriptions/{id}", this::unsubscribe);
        app.post("/api/autoloader/subscriptions/{id}/download", this::runDownload);
        app.get("/api/autoloader/subscriptions/{id}/providers", this::getAlternateProviders);
        app.post("/api/autoloader/subscriptions/{id}/pause", this::pauseSubscription);
        app.post("/api/autoloader/subscriptions/{id}/resume", this::resumeSubscription);
        app.post("/api/autoloader/subscriptions/{id}/scan", this::scanSubscription);
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
                        // Skip paused anime
                        if (anime.isPaused()) {
                            log.debug("Skipping paused anime: {}", anime.getTitle());
                            continue;
                        }

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
                            anime.writeToDB(db);

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
                unloadedEpisode.loadVideoURL(anime.getLanguageId(), () -> {
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

                    log.debug("Scheduling download internally: {}", data);
                    defaultHandler.scheduleDownloads(List.of(data));
                });
            } catch (Exception ex) {
                log.error("", ex);
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Download failed: " + ex.getMessage()));
            } finally {
                unloadedEpisode.setDownloading(false);
            }
        }

        anime.writeToDB(db);
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
