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
import de.theholyexception.mediamanager.util.WebResponseException;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.*;
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
            subscribedAnimes.forEach(anime ->
                handler.putTask(() -> {
                    anime.loadMissingEpisodes();
                    anime.scanDirectoryForExistingEpisodes();
                    log.debug("Unloaded episodes for " + anime.getTitle() + " : " + anime.getUnloadedEpisodeCount(false));
                    if (anime.isDeepDirty())
                        anime.writeToDB(db);
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

    //region REST API

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
            Anime anime = Anime.createFromContent(ctx);
            if (anime == null) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(Map.of("error", "Invalid request data"));
                return;
            }

            if (subscribedAnimes.stream().anyMatch(a -> a.getUrl().equals(anime.getUrl()))) {
                ctx.status(HttpStatus.CONFLICT);
                ctx.json(Map.of("error", "Already subscribed to " + anime.getUrl()));
                return;
            }

            subscribedAnimes.add(anime);
            anime.writeToDB(db);

            //db.getExecutorHandler().awaitGroup(-1);

            anime.loadMissingEpisodes();
            anime.scanDirectoryForExistingEpisodes();

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
            @OpenApiResponse(status = "503", description = "AutoLoader not initialized")
        }
    )
    private void runDownload(Context ctx) {
        if (!initialized) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            ctx.json(Map.of("error", "AutoLoader is not initialized yet"));
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

            optAnime.get().runDownload(db, defaultHandler);
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
        summary = "Get autoloader status",
        description = "Returns the current status of the autoloader (enabled/disabled and initialization state)",
        operationId = "getAutoloaderStatus",
        path = "/api/autoloader/status",
        tags = {"AutoLoader"},
        methods = HttpMethod.GET,
        responses = {
            @OpenApiResponse(status = "200", description = "Autoloader status information")
        }
    )
    private void getAutoloaderStatus(Context ctx) {
        JSONObject status = new JSONObject();
        status.put("enabled", enabled);
        status.put("initialized", initialized);
        ctx.json(status);
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
            
            // Set scanning state to true
            anime.setScanning(true);
            
            // Notify clients that scanning has started
            notifyDataChanged("subscriptions");


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
            // Set scanning state to false when done
            anime.setScanning(false);

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

    @Override
    public void registerAPI(Javalin app) {
        app.get("/api/autoloader/status", this::getAutoloaderStatus);
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

    //endregion REST API

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
                        
                        // Set scanning state to true
                        anime.setScanning(true);

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
                            anime.runDownload(db, defaultHandler);

                        if (anime.isDeepDirty())
                            anime.writeToDB(db);

                        anime.updateLastUpdate(); // Update the lastUpdate variable
                        // Set scanning state to false when done
                        anime.setScanning(false);

                        Utils.sleep(checkDelayMs);
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
     * @throws WebResponseException if the episode is not found
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
     * @throws WebResponseException if any component of the path is not found
     */
    public Episode getEpisodeByPath(int animeId, int seasonId, int episodeId) {
        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == animeId).findFirst();
        if (optAnime.isEmpty())
            throw new WebResponseException(WebSocketResponse.ERROR.setMessage("Anime with id " + animeId + " not found!"));

        Optional<Season> optSeason = optAnime.get().getSeasonList().stream().filter(season -> season.getId() == seasonId).findFirst();
        if (optSeason.isEmpty())
            throw new WebResponseException(WebSocketResponse.ERROR.setMessage("Season with id " + seasonId + " not found!"));

        Optional<Episode> optEpisode = optSeason.get().getEpisodeList().stream().filter(ep -> ep.getId() == episodeId).findFirst();
        if (optEpisode.isEmpty())
            throw new WebResponseException(WebSocketResponse.ERROR.setMessage("Episode with id " + episodeId + " not found!"));

        return optEpisode.get();
    }

    /**
     * Retrieves alternative streaming providers for a given episode.
     *
     * @param autoloaderData JSON data containing the episode information
     * @return A map of provider names to their respective URLs
     * @throws WebResponseException if the anime or episode is not found
     */
    public Map<AniworldProvider, String> getAlternativeProviders(JSONObjectContainer autoloaderData) {
        int animeId = autoloaderData.get("animeId", Integer.class);
        int seasonId = autoloaderData.get("seasonId", Integer.class);
        int episodeId = autoloaderData.get("episodeId", Integer.class);

        Optional<Anime> optAnime = subscribedAnimes.stream().filter(anime -> anime.getId() == animeId).findFirst();
        if (optAnime.isEmpty())
            throw new WebResponseException(WebSocketResponse.ERROR.setMessage("Anime with id " + animeId + " not found!"));

        Episode episode = getEpisodeByPath(animeId, seasonId, episodeId);
        if (episode.getVideoUrl() == null)
            throw new WebResponseException(WebSocketResponse.ERROR.setMessage("Episode has no video url!"));

        AniworldProvider provider = AniworldProvider.getProvider(episode.getVideoUrl());

        Map<AniworldProvider, String> urls = AniworldHelper.resolveAlternateVideoURLs(episode, optAnime.get().getLanguageId(), provider);
        Map<AniworldProvider, String> episodeAlternateVideoUrls = episode.getAlternateVideoURLs();
        synchronized (episodeAlternateVideoUrls) {
            episodeAlternateVideoUrls.clear();
            episodeAlternateVideoUrls.putAll(urls);
        }
        return urls;
    }
    //endregion OpenAPI

}
