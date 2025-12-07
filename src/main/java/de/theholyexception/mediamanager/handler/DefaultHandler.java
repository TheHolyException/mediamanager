package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.models.DownloadTask;
import de.theholyexception.mediamanager.models.Target;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.util.InitializationException;
import de.theholyexception.mediamanager.util.TargetSystem;
import de.theholyexception.mediamanager.util.WebResponseException;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.websocket.WsContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.downloaders.FFmpeg;
import me.kaigermany.ultimateutils.StaticUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

import static de.theholyexception.mediamanager.webserver.WebSocketUtils.*;

/**
 * Default handler implementation that manages core functionality of the MediaManager application.
 * Handles download management, file operations, and system settings through WebSocket commands.
 * This handler is responsible for the main download queue, target management, and system monitoring.
 */
@Slf4j
public class DefaultHandler extends Handler {

    /** Map of active download URLs with their corresponding metadata */
    @Getter
    private final Map<UUID, DownloadTask> urls = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private final Map<String, Target> targets = new HashMap<>();
    
    /** Setting for number of parallel downloads */
    private SettingProperty<Integer> spDownloadThreads;
    
    /** Setting for number of VOE download threads */
    private SettingProperty<Integer> spThreads;
    
    /** Setting for retry delay calculation formula */
    private SettingProperty<String> spRetryDelayFormula;
    
    /** Setting for maximum retry delay in minutes */
    private SettingProperty<Integer> spMaxRetryDelayMinutes;

    private TimerTask retryTask;

    /**
     * Creates a new DefaultHandler instance for the specified target system.
     *
     */
    public DefaultHandler() {
        super(TargetSystem.DEFAULT);
    }

    @Override
    public void initialize() {
        compressLogFiles();
        retryTask = new TimerTask() {
            @Override
            public void run() {
                List<String> processedUrls = new ArrayList<>();
                for (DownloadTask downloadTask : urls.values()) {
                    if (processedUrls.contains(downloadTask.getUrl())) {
						log.warn("Found duplicated retry schedule: {}, disabling retry.", downloadTask.getUrl());
                        downloadTask.disableRetry();
                        continue;
                    }
                    if (!downloadTask.isFailed())
                        continue;
                    if (downloadTask.getErrorCount() <= 0)
                        continue;

                    if (downloadTask.getRetryTimestamp() < System.currentTimeMillis()) {
                        log.info("Rescheduling download for {}", downloadTask.getUrl());
                        processedUrls.add(downloadTask.getUrl());
                        downloadTask.start(spThreads.getValue());
                    }
                }
            }
        };
        new Timer("Retry Scheduler").schedule(retryTask, 30000, 30000);
    }

    /**
     * Loads and initializes configurations for the handler.
     * Sets up thread pools, download settings, and scheduled tasks.
     */
    @Override
    public void loadConfigurations() {
        log.info("Loading Configurations");
        String systemSettings = "systemSettings";

        spDownloadThreads = Settings.getSettingProperty("PARALLEL_DOWNLOADS", 1, systemSettings);

        spThreads = Settings.getSettingProperty("THREADS", 1, systemSettings);
        
        spRetryDelayFormula = Settings.getSettingProperty("RETRY_DELAY_FORMULA", "errorCount * errorCount * 600", systemSettings);
        
        spMaxRetryDelayMinutes = Settings.getSettingProperty("MAX_RETRY_DELAY_MINUTES", 1440, systemSettings);

        FFmpeg.setFFmpegPath(config.getString("general.ffmpeg"));

        loadTargets();

        DownloadTask.initialize(config);
        spDownloadThreads.addSubscriber(value -> DownloadTask.getDownloadHandler().updateExecutorService(Executors.newFixedThreadPool(value)));
    }
    
    /**
     * Gets the retry delay formula setting value.
     * @return The current retry delay formula
     */
    public String getRetryDelayFormula() {
        return spRetryDelayFormula.getValue();
    }
    
    /**
     * Gets the maximum retry delay in minutes setting value.
     * @return The current maximum retry delay in minutes
     */
    public int getMaxRetryDelayMinutes() {
        return spMaxRetryDelayMinutes.getValue();
    }

    /**
     * Loads and initializes download targets from the configuration.
     * Targets define the base directories where downloaded files will be stored.
     * 
     * @throws InitializationException if the targets configuration is invalid or missing
     */
    private void loadTargets() {
        TomlArray targetArray = config.getArray("target");
        if (targetArray == null)
            throw new InitializationException("LoadTargets", "Failed to load targets");

        for (int i = 0; i < targetArray.size(); i ++) {
            TomlTable x = targetArray.getTable(i);
            Target tar = new Target(
                    x.getString("identifier"),
                    x.getString("displayName"),
                    x.getString("path"),
					Boolean.TRUE.equals(x.getBoolean("subFolders")));
            targets.put(tar.identifier(), tar);
        }
    }
    
    /**
     * Processes incoming WebSocket commands and routes them to the appropriate handler method.
     * This is the main entry point for all commands processed by this handler.
     *
     * @param ctx The WebSocket connection that received the command
     * @param command The command to execute (e.g., "syn", "put", "del")
     * @param content JSON data associated with the command
     * @throws WebResponseException if the command is invalid, processing fails or just a feedback for the client
     */
    @Override
    public void handleWebSocketPacket(WsContext ctx, String command, JSONObjectContainer content) {
        switch (command) {
            case "syn" -> cmdSyncData(ctx);
            // WebSocket ping
            case "ping" -> sendPacket("pong", TargetSystem.DEFAULT, content.getRaw(), ctx);
            default ->
                throw new WebResponseException(WebSocketResponse.ERROR.setMessage("Invalid command " + command));
        }
    }

    /**
     * Handles the 'syn' command to synchronize data with the client.
     * Sends the current state of downloads, settings, system information, and target folders
     * to the requesting client.
     *
     * @param ctx The WebSocket connection to send the synchronized data to
     */
    private void cmdSyncData(WsContext ctx) {
        List<DownloadTask> taskData = urls.values().stream()
                .sorted(DownloadTask::compareTo)
                .toList();
        sendObjectTo(ctx, taskData);
        sendSettings(ctx);
    }

    public void scheduleDownload(JSONObjectContainer content) {
        String url = content.get("url", String.class);
        if (urls.values().stream().anyMatch(dt -> dt.getUrl().equals(url))) {
			log.error("Duplicated download detected: {}", url);
            return;
        }
        UUID uuid = UUID.fromString(content.get("uuid", String.class));
        DownloadTask downloadTask;
        if (urls.containsKey(uuid)) {
            downloadTask = urls.get(uuid);
            downloadTask.updateContent(content);
        } else {
            downloadTask = new DownloadTask(content);
            urls.put(uuid, downloadTask);
        }
        downloadTask.start(spThreads.getValue());
    }

    /**
     * Deletes a download object.
     * 
     * @param toDelete The download item to delete
     * @param removed List to which the deleted item will be added
     * @return WebSocketResponse indicating success or failure, or null if successful
     */
    private WebSocketResponse deleteObject(DownloadTask toDelete, List<DownloadTask> removed) {
        ExecutorTask task = toDelete.getDownloadExecutorTask();
        if (task != null) {
            if (task.isRunning()) {
                if (!toDelete.getDownloader().cancel()) {
                    return WebSocketResponse.WARN.setMessage("Failed to abort task! Downloader does not allow canceling");
                }
            } else {
                if (!DownloadTask.getDownloadHandler().abortTask(task) && !task.isCompleted()) {
                    return WebSocketResponse.WARN.setMessage("Failed to abort task! internal error!");
                }
            }
        }
        removed.add(toDelete);
        toDelete.setDeleted(true);
        toDelete.close();
        return null;
    }

    private void compressLogFiles() {
        File[] files = DownloadTask.getDOWNLOADS_LOG_FOLDER().listFiles();
        if (files == null)
            return;
        for (File file : files) {
            if (file.isDirectory() || !file.getName().endsWith(".log"))
                continue;
			log.debug("Compressing log file {}", file.getName());
            File compressedFile = new File(DownloadTask.getDOWNLOADS_LOG_FOLDER(), file.getName()+".gz");
            try (GZIPOutputStream gos = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(compressedFile)))) {
                gos.write(StaticUtils.loadBytes(file));
                Files.delete(file.toPath());
            } catch (IOException ex) {
				log.error("Failed to compress log file {}", file.getName(), ex);
            }
        }
    }

    //region OpenAPI
    @Override
    public void registerAPI(Javalin app) {
        app.get("/api/subfolders/{target}", this::getSubfoldersRequest);
        app.post("/api/downloads", this::addDownloadsRequest);
        app.delete("/api/downloads/{uuid}", this::deleteDownloadRequest);
        app.delete("/api/downloads", this::deleteAllDownloadsRequest);
        app.get("/api/view-log/{uuid}", this::viewLogRequest);
        app.get("/api/targets", this::getTargets);
        app.get("/api/settings", this::getSettingsRequest);
        app.post("/api/settings", this::updateSettingsRequest);
        app.post("/api/system/gc", this::triggerGarbageCollection);
    }

    @OpenApi(
        summary = "Gets the subfolders for the requested target",
        operationId = "getSubfolders",
        path = "/api/subfolders/{target}",
        tags = {"Downloader"},
        methods = HttpMethod.GET,
        pathParams = {
            @OpenApiParam(name = "target", description = "The target to get the subfolders for", required = true)
        },
        responses = {
            @OpenApiResponse(status = "200", description = "Success"),
            @OpenApiResponse(status = "204", description = "No Subfolders"),
            @OpenApiResponse(status = "404", description = "Target not found")
        }
    )
    private void getSubfoldersRequest(Context ctx) {
        String targetString = ctx.pathParam("target");
        Target target = targets.get(targetString.split("/")[0]);

        if (target == null) {
            ctx.status(404);
            return;
        }

        if (!target.subFolders()) {
            ctx.status(204);
            return;
        }

        File folder = new File(target.path());
        File[] folders = folder.listFiles();
        if (folders == null) {
            ctx.status(204);
            return;
        }

        ctx.json(Arrays.stream(folders)
            .filter(File::isDirectory)
            .map(File::getName)
            .toList());
    }

    /**
     * REST API endpoint to add new download items to the queue.
     * Processes each item in the provided list and schedules downloads.
     */
    @OpenApi(
        summary = "Add new download items to the queue",
        operationId = "addDownloads",
        path = "/api/downloads",
        tags = {"Downloader"},
        methods = HttpMethod.POST,
        responses = {
            @OpenApiResponse(status = "200", description = "Downloads added successfully"),
            @OpenApiResponse(status = "400", description = "Invalid request data"),
            @OpenApiResponse(status = "500", description = "Server error")
        }
    )
    private void addDownloadsRequest(Context ctx) {
        try {
            JSONObjectContainer requestData = (JSONObjectContainer) JSONReader.readString(ctx.body());

            for (Object data : requestData.getArrayContainer("list").getRaw()) {
                JSONObjectContainer item = (JSONObjectContainer) JSONReader.readString(data.toString());
                scheduleDownload(item);
            }

            // Return success response
            ctx.status(200);
            ctx.json(Map.of("status", "success", "message", "Downloads added successfully"));
        } catch (WebResponseException ex) {
			log.error("Failed to add downloads, cause: {}", ex.getMessage());
            ctx.status(400);
            ctx.json(Map.of("error", ex.getResponse().getMessage()));
        } catch (Exception ex) {
            log.error("Failed to add downloads via REST API", ex);
            ctx.status(500);
            ctx.json(Map.of("error", "Internal server error: " + ex.getMessage()));
        }
    }

    /**
     * REST API endpoint to delete a specific download from the queue.
     * Removes the download identified by UUID and cancels it if running.
     */
    @OpenApi(
        summary = "Delete a specific download from the queue",
        operationId = "deleteDownload",
        path = "/api/downloads/{uuid}",
        tags = {"Downloader"},
        methods = HttpMethod.DELETE,
        pathParams = {
            @OpenApiParam(name = "uuid", description = "The UUID of the download to delete", required = true)
        },
        responses = {
            @OpenApiResponse(status = "200", description = "Download deleted successfully"),
            @OpenApiResponse(status = "404", description = "Download not found"),
            @OpenApiResponse(status = "400", description = "Invalid UUID format"),
            @OpenApiResponse(status = "500", description = "Server error")
        }
    )
    private void deleteDownloadRequest(Context ctx) {
        try {
            String uuidStr = ctx.pathParam("uuid");
            UUID uuid = UUID.fromString(uuidStr);

            List<DownloadTask> toDelete = new ArrayList<>();
            DownloadTask item = urls.get(uuid);
            if (item == null) {
                ctx.status(404);
                ctx.json(Map.of("error", "Download not found"));
                return;
            }

            WebSocketResponse response = deleteObject(item, toDelete);
            if (response != null) {
                ctx.status(400);
                ctx.json(Map.of("error", response.getResponse().get("message", String.class)));
                return;
            }

            // Skip if nothing to delete
            if (toDelete.isEmpty()) {
                ctx.status(200);
                ctx.json(Map.of("status", "success", "message", "No items to delete"));
                return;
            }

            deleteObjectToAll(toDelete);
            urls.remove(uuid);

            ctx.status(200);
            ctx.json(Map.of("status", "success", "message", "Download deleted successfully"));

        } catch (IllegalArgumentException ex) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid UUID format"));
        } catch (Exception ex) {
            log.error("Failed to delete download via REST API", ex);
            ctx.status(500);
            ctx.json(Map.of("error", "Internal server error: " + ex.getMessage()));
        }
    }

    /**
     * REST API endpoint to view the log file content for a specific download task.
     * Returns the log file content as plain text for viewing in a new tab.
     */
    @OpenApi(
        summary = "View log file content for a specific download task",
        operationId = "viewLog",
        path = "/api/view-log/{uuid}",
        tags = {"Downloader"},
        methods = HttpMethod.GET,
        pathParams = {
            @OpenApiParam(name = "uuid", description = "The UUID of the download task", required = true)
        },
        queryParams = {
            @OpenApiParam(name = "detailed", description = "Set to 'true' to view detailed log file", required = false)
        },
        responses = {
            @OpenApiResponse(status = "200", description = "Log file content displayed successfully"),
            @OpenApiResponse(status = "404", description = "Download task or log file not found"),
            @OpenApiResponse(status = "400", description = "Invalid UUID format"),
            @OpenApiResponse(status = "500", description = "Server error")
        }
    )
    private void viewLogRequest(Context ctx) {
        try {
            String uuidStr = ctx.pathParam("uuid");
            UUID uuid = UUID.fromString(uuidStr);
            boolean detailed = "true".equals(ctx.queryParam("detailed"));

            DownloadTask downloadTask = urls.get(uuid);
            if (downloadTask == null) {
                ctx.status(404);
                ctx.json(Map.of("error", "Download task not found"));
                return;
            }

            File logFile = detailed ? downloadTask.getDetailedLogFile() : downloadTask.getOutputLogFile();
            if (logFile == null || !logFile.exists()) {
                String logType = detailed ? "detailed log" : "log";
                ctx.status(404);
                ctx.json(Map.of("error", logType + " file not found"));
                return;
            }

            // Set headers for viewing content in browser (not download)
            ctx.header("Content-Type", "text/plain; charset=utf-8");
            
            // Send the file content for viewing
            ctx.result(new String(java.nio.file.Files.readAllBytes(logFile.toPath())));

        } catch (IllegalArgumentException ex) {
            ctx.status(400);
            ctx.json(Map.of("error", "Invalid UUID format"));
        } catch (Exception ex) {
            log.error("Failed to view log file via REST API", ex);
            ctx.status(500);
            ctx.json(Map.of("error", "Internal server error: " + ex.getMessage()));
        }
    }

    /**
     * REST API endpoint to delete all downloads from the queue.
     * Removes all downloads and cancels running ones.
     */
    @OpenApi(
        summary = "Delete all downloads from the queue",
        operationId = "deleteAllDownloads",
        path = "/api/downloads",
        tags = {"Downloader"},
        methods = HttpMethod.DELETE,
        responses = {
            @OpenApiResponse(status = "200", description = "All downloads deleted successfully"),
            @OpenApiResponse(status = "500", description = "Server error")
        }
    )
    private void deleteAllDownloadsRequest(Context ctx) {
        try {
            List<DownloadTask> toDelete = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            for (DownloadTask value : new HashMap<>(urls).values()) {
                WebSocketResponse response = deleteObject(value, toDelete);
                if (response != null) {
                    warnings.add("Warning for " + value.getUuid() + ": " + response.getResponse().get("message", String.class));
                }
            }

            // Skip if nothing to delete
            if (toDelete.isEmpty()) {
                ctx.status(200);
                ctx.json(Map.of("status", "success", "message", "No downloads to delete"));
                return;
            }

            deleteObjectToAll(toDelete);
            for (DownloadTask downloadTask : toDelete) {
                urls.remove(downloadTask.getUuid());
            }

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("status", "success");
            responseMap.put("message", "All downloads deleted successfully");
            responseMap.put("deletedCount", toDelete.size());
            if (!warnings.isEmpty()) {
                responseMap.put("warnings", warnings);
            }

            ctx.status(200);
            ctx.json(responseMap);

        } catch (Exception ex) {
            log.error("Failed to delete all downloads via REST API", ex);
            ctx.status(500);
            ctx.json(Map.of("error", "Internal server error: " + ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Gets all defined Targets",
        operationId = "getTargets",
        path = "/api/targets",
        tags = {"Downloader"},
        methods = HttpMethod.GET,
        responses = {
            @OpenApiResponse(status = "200", description = "List of targets")
        }
    )
    private void getTargets(Context ctx) {
        JSONArray results = new JSONArray();
        for (Target value : targets.values()) {
            JSONObject segment = new JSONObject();
            segment.put("identifier", value.identifier());
            segment.put("displayName", value.displayName());
            results.add(segment);
        }
        ctx.json(results);
    }

    @OpenApi(
        summary = "Gets current system settings",
        operationId = "getSettings", 
        path = "/api/settings",
        tags = {"Settings"},
        methods = HttpMethod.GET,
        responses = {
            @OpenApiResponse(status = "200", description = "Current settings")
        }
    )
    private void getSettingsRequest(Context ctx) {
        try {
            JSONArray settings = new JSONArray();
            
            JSONObject threadsetting = new JSONObject();
            threadsetting.put("key", "THREADS");
            threadsetting.put("val", spThreads.getValue());
            settings.add(threadsetting);
            
            JSONObject parallelSetting = new JSONObject();
            parallelSetting.put("key", "PARALLEL_DOWNLOADS");
            parallelSetting.put("val", spDownloadThreads.getValue());
            settings.add(parallelSetting);
            
            JSONObject retryFormulaSetting = new JSONObject();
            retryFormulaSetting.put("key", "RETRY_DELAY_FORMULA");
            retryFormulaSetting.put("val", spRetryDelayFormula.getValue());
            settings.add(retryFormulaSetting);
            
            JSONObject maxRetrySetting = new JSONObject();
            maxRetrySetting.put("key", "MAX_RETRY_DELAY_MINUTES");
            maxRetrySetting.put("val", spMaxRetryDelayMinutes.getValue());
            settings.add(maxRetrySetting);
            
            ctx.status(200);
            ctx.json(Map.of("settings", settings));
            
        } catch (Exception ex) {
            log.error("Failed to get settings via REST API", ex);
            ctx.status(500);
            ctx.json(Map.of("error", "Internal server error: " + ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Updates system settings",
        operationId = "updateSettings",
        path = "/api/settings",
        tags = {"Settings"},
        methods = HttpMethod.POST,
        responses = {
            @OpenApiResponse(status = "200", description = "Settings updated successfully"),
            @OpenApiResponse(status = "400", description = "Invalid request data"),
            @OpenApiResponse(status = "500", description = "Server error")
        }
    )
    private void updateSettingsRequest(Context ctx) {
        try {
            JSONObjectContainer requestData = (JSONObjectContainer) JSONReader.readString(ctx.body());
            JSONArrayContainer settings = requestData.getArrayContainer("settings");
            
            List<String> errors = new ArrayList<>();
            
            for (Object o : settings.getRaw()) {
                try {
                    JSONObject setting = (JSONObject) o;
                    String key = (String) setting.get("key");
                    String val = (String) setting.get("value");
                    
                    switch (key) {
                        case "THREADS" -> spThreads.setValue(Integer.parseInt(val));
                        case "PARALLEL_DOWNLOADS" -> spDownloadThreads.setValue(Integer.parseInt(val));
                        case "RETRY_DELAY_FORMULA" -> spRetryDelayFormula.setValue(val);
                        case "MAX_RETRY_DELAY_MINUTES" -> spMaxRetryDelayMinutes.setValue(Integer.parseInt(val));
                        default -> errors.add("Invalid setting: " + key);
                    }
                    log.info("Changed setting {} to: {}", key, val);
                } catch (NumberFormatException ex) {
                    errors.add("Invalid numeric value for setting");
                } catch (Exception ex) {
                    errors.add("Error processing setting: " + ex.getMessage());
                }
            }
            
            if (!errors.isEmpty()) {
                ctx.status(400);
                ctx.json(Map.of("error", "Settings update failed", "details", errors));
                return;
            }
            
            // Notify all WebSocket clients that settings have changed
            notifyDataChanged("settings");
            
            ctx.status(200);
            ctx.json(Map.of("status", "success", "message", "Settings updated successfully"));
            
        } catch (Exception ex) {
            log.error("Failed to update settings via REST API", ex);
            ctx.status(500);
            ctx.json(Map.of("error", "Internal server error: " + ex.getMessage()));
        }
    }

    @OpenApi(
        summary = "Triggers garbage collection",
        operationId = "triggerGarbageCollection",
        path = "/api/system/gc",
        tags = {"System"},
        methods = HttpMethod.POST,
        responses = {
            @OpenApiResponse(status = "200", description = "Garbage collection triggered successfully"),
            @OpenApiResponse(status = "500", description = "Server error")
        }
    )
    private void triggerGarbageCollection(Context ctx) {
        try {
            log.info("Triggering garbage collection via REST API");
            System.gc();
            
            ctx.status(200);
            ctx.json(Map.of("status", "success", "message", "Garbage collection triggered"));
            
        } catch (Exception ex) {
            log.error("Failed to trigger garbage collection via REST API", ex);
            ctx.status(500);
            ctx.json(Map.of("error", "Internal server error: " + ex.getMessage()));
        }
    }

    /**
     * Sends a universal data change notification to all WebSocket clients.
     * This can be used to notify clients when any data has been updated via REST API
     * so they can refetch the updated data.
     *
     * @param dataType The type of data that changed (e.g., "settings", "downloads", "targets")
     */
    private void notifyDataChanged(String dataType) {
        JSONObject notification = new JSONObject();
        notification.put("dataType", dataType);
        notification.put("timestamp", System.currentTimeMillis());
        sendPacket("data-changed", TargetSystem.DEFAULT, notification, null);
    }
    //endregion OpenAPI

}
