package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.di.DIInject;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.holyapi.util.GUIUtils;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.models.TableItemDTO;
import de.theholyexception.mediamanager.models.Target;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.util.*;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.websocket.WsContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.downloaders.DownloadStatusUpdateEvent;
import me.kaigermany.downloaders.Downloader;
import me.kaigermany.downloaders.DownloaderSelector;
import me.kaigermany.downloaders.FFmpeg;
import me.kaigermany.ultimateutils.StaticUtils;
import me.kaigermany.ultimateutils.networking.smarthttp.HTTPRequestOptions;
import me.kaigermany.ultimateutils.networking.smarthttp.HTTPResult;
import me.kaigermany.ultimateutils.networking.smarthttp.SmartHTTP;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static de.theholyexception.mediamanager.webserver.WebSocketUtils.*;

/**
 * Default handler implementation that manages core functionality of the MediaManager application.
 * Handles download management, file operations, and system settings through WebSocket commands.
 * This handler is responsible for the main download queue, target management, and system monitoring.
 */
@Slf4j
public class DefaultHandler extends Handler {

    /** Map of active download URLs with their corresponding metadata */
    private final Map<UUID, TableItemDTO> urls = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private final Map<String, Target> targets = new HashMap<>();

    private long dockerMemoryLimit;
    private long dockerMemoryUsage;
    private final ExecutorHandler downloadHandler;
    private final ExecutorHandler titleResolverHandler;

    private File downloadFolder;
    
    /** Setting for number of parallel downloads */
    private SettingProperty<Integer> spDownloadThreads;
    
    /** Setting for number of VOE download threads */
    private SettingProperty<Integer> spThreads;
    
    /** Setting for retry delay in minutes */
    private SettingProperty<Integer> spRetryMinutes;

    private final FileLogger fLogger = new FileLogger("DefaultHandler");
    private boolean downloadLogFiles;
    private File downloadLogFolder;
    private boolean untrustedCertificates;
    private boolean enableValidation;
    private List<Target> validatorTargets;
    private double validatorVideoLengthThreshold;

    private TimerTask task;

    private JSONObject torNetworkStatus;
    
    private final Queue<JSONObject> memoryHistory = new LinkedList<>();
    private final Queue<JSONObject> downloadHistory = new LinkedList<>();
    private final Queue<JSONObject> threadHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 120; // Keep last 720 data points (60 minutes at 5-second intervals)

    @DIInject
    private AutoLoaderHandler autoLoaderHandler;

    /**
     * Creates a new DefaultHandler instance for the specified target system.
     *
     */
    public DefaultHandler() {
        super(TargetSystem.DEFAULT);
        downloadHandler = new ExecutorHandler(Executors.newFixedThreadPool(1));
        downloadHandler.setThreadNameFactory(cnt -> "DownloadThread-" + cnt);
        titleResolverHandler = new ExecutorHandler(Executors.newFixedThreadPool(5));
        titleResolverHandler.setThreadNameFactory(cnt -> "TitleResolver-" + cnt);
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
        spDownloadThreads.addSubscriber(value ->
        downloadHandler.updateExecutorService(Executors.newFixedThreadPool(value)));

        spThreads = Settings.getSettingProperty("THREADS", 1, systemSettings);

        spRetryMinutes = Settings.getSettingProperty("RETRY_MINUTES", 0, systemSettings);
        spRetryMinutes.addSubscriber(value -> {
            if (task != null)
                task.cancel();
            if (value != 0) {
                log.info("Enabling retry timer");
                task = new TimerTask() {
                    @Override
                    public void run() {
                        urls.forEach((uuid, tableItemDTO) -> {
                            if (tableItemDTO.getJsonObject().get("state", String.class).toLowerCase().startsWith("error")
                                && System.currentTimeMillis()-tableItemDTO.getLastUpdate() < value*60_000) {
                                log.info("Rescheduling download for {}", tableItemDTO.getUrl());
                                scheduleDownload(null, tableItemDTO.getJsonObject());
                            }
                        });
                    }
                };
                new Timer().schedule(task, value*60_000, value*60_000);
            } else {
                log.info("Disabled retry timer");
            }
        });

        FFmpeg.setFFmpegPath(config.getString("general.ffmpeg"));

        downloadFolder = new File(config.getString("general.tmpDownloadFolder", () -> "./tmp"));
        if (!downloadFolder.exists() && !downloadFolder.mkdirs())
            log.error("Could not create download folder");

        this.downloadLogFiles = config.getBoolean("general.logDebugDownloaderFiles", () -> false);
        this.untrustedCertificates = config.getBoolean("general.untrustedCertificates", () -> false);

        loadTargets();
        this.enableValidation = config.getBoolean("validator.enabled", () -> false);
        this.validatorTargets = new ArrayList<>();
        if (this.enableValidation) {
            validatorVideoLengthThreshold = Integer.parseInt(config.getString("validator.videoLengthThreshold", () -> "50%").replace("%", ""))/100d;
            String targetsCSV = config.getString("validator.targets", () -> "");
            String[] virtualTargets = targetsCSV.split(",");
            for (String virtualTarget : virtualTargets) {
                this.validatorTargets.add(targets.get(virtualTarget));
                log.info("Enabled validating for " + virtualTarget);
            }
        }
    }

    /**
     * Initializes the handler after configurations are loaded.
     * Starts system data fetcher and sets up download log folder if enabled.
     */
    @Override
    public void initialize() {
        startSystemDataFetcher();

        if (this.downloadLogFiles) {
            String folderName = config.getString("general.logDebugDownloaderFolder", () -> "./downloader-logs");
            File folder = new File(folderName);
            folder.mkdirs();
            downloadLogFolder = folder;
        }
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

    // region commands
    
    /**
     * Processes incoming WebSocket commands and routes them to the appropriate handler method.
     * This is the main entry point for all commands processed by this handler.
     *
     * @param ctx The WebSocket connection that received the command
     * @param command The command to execute (e.g., "syn", "put", "del")
     * @param content JSON data associated with the command
     * @throws WebSocketResponseException if the command is invalid, processing fails or just a feedback for the client
     */
    @Override
    public void handleCommand(WsContext ctx, String command, JSONObjectContainer content) {
        switch (command) {
            case "syn" -> cmdSyncData(ctx);
            case "setting" -> cmdChangeSetting(ctx, content);
            case "testDelay" -> {
                log.debug("Test delay");
                Utils.sleep(5000);
                throw new WebSocketResponseException(WebSocketResponse.OK.setMessage("Test delay done!"));
            }
            case "triggerGC" -> {
                log.info("Triggering garbage collection");
                System.gc();
                throw new WebSocketResponseException(WebSocketResponse.OK.setMessage("Garbage collection triggered"));
            }
            case "ping" -> sendPacket("pong", TargetSystem.DEFAULT, content.getRaw(), ctx);
            case "systemInfo" -> sendSystemInformation(ctx);
            default ->
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid command " + command));
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
        List<JSONObjectContainer> jsonData = urls.values().stream()
                .sorted(TableItemDTO::compareTo)
                .map(TableItemDTO::getJsonObject)
                .toList();
        sendObject(ctx, jsonData.stream().map(JSONObjectContainer::getRaw).toList());
        sendSettings(ctx);
        sendSystemInformation(ctx);
        sendTargetFolders(ctx);
    }



    /**
     * Handles the 'setting' command to update application settings.
     * 
     * @param ctx The WebSocket connection that sent the command
     * @param content JSON data containing the settings to update
     */
    private void cmdChangeSetting(WsContext ctx, JSONObjectContainer content) {
        JSONArrayContainer settings = content.getArrayContainer("settings");
        for (Object o : settings.getRaw()) {
            try {
                JSONObject setting = (JSONObject) o;
                String key = (String) setting.get("key");
                String val = (String) setting.get("value");
                switch (key) {
                    case "THREADS" -> spThreads.setValue(Integer.parseInt(val));
                    case "PARALLEL_DOWNLOADS" -> spDownloadThreads.setValue(Integer.parseInt(val));
                    case "RETRY_MINUTES" -> spRetryMinutes.setValue(Integer.parseInt(val));
                    default -> throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid setting " + key));
                }
                log.info("Changed setting {} to: {}", key, val);
            } catch (Exception ex) {
                WebSocketUtils.sendPacket("response", TargetSystem.DEFAULT, WebSocketResponse.ERROR.setMessage(ex.getMessage()).getResponse().getRaw(), ctx);
            }
        }
        sendSettings(null);
    }
    // endregion commands

    /**
     * Schedules a download task based on the provided content.
     * Validates the input and creates a new download task if all requirements are met.
     * 
     * @param ctx The WebSocket connection that requested the download (can be null for retries or autoloader)
     * @param content JSON data containing download information
     * @throws WebSocketResponseException if the download cannot be scheduled
     */
    @SuppressWarnings("unchecked")
    private void scheduleDownload(WsContext ctx, JSONObjectContainer content) {
        File outputFolder = resolveOutputFolder(ctx, content);

        TableItemDTO tableItem = new TableItemDTO(content);
        urls.put(UUID.fromString(content.get("uuid", String.class)), tableItem);
        changeObject(content,
                "state", "Committed",
                "sortIndex", tableItem.getSortIndex());

        String url = content.get("url", String.class);
        if (url == null || url.isEmpty())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid URL"));

        if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("PUT,"+url+","+tableItem.getSortIndex());}}

        JSONObjectContainer autoloaderData = content.getObjectContainer("autoloaderData");

        var updateEvent = createDownloadStatusUpdateEvent(tableItem, content);

        JSONObject options = content.getObjectContainer("options").getRaw();
        options.put("useDirectMemory", config.getBoolean("general.useDirectMemory", () -> false)+"");
        if (untrustedCertificates)
            options.put("disableCertificateCheck", "true");
        boolean skipValidation = Boolean.parseBoolean((String)options.get("skipValidation"));

        // Creating the temp folder to download the file into
        File downloadTempFolder = new File(downloadFolder, UUID.randomUUID().toString());
        if (!downloadTempFolder.mkdirs() || !downloadTempFolder.exists())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to create temp folder"));

        // Getting the right downloader
        Downloader downloader = DownloaderSelector.selectDownloader(url, downloadTempFolder, updateEvent, options);
        downloader.setProxy(ProxyHandler.getNextProxy());
        downloader.setNumThreads(spThreads.getValue());
        tableItem.setDownloader(downloader);

        try {
            // Start the download
            // This task is only running when the titleTask is completed
            ExecutorTask downloadTask = new ExecutorTask(() -> {
                // Check if the task has already been deleted, if so we skipp the execution
                if (tableItem.isDeleted()) {
                    if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("DELETED,"+url+","+tableItem.getSortIndex());}}
                    return;
                }
                // Set the information about the thread to the model item
                tableItem.setRunning(true);

                try {
                    if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("START,"+url+","+tableItem.getSortIndex());}}
                    File file = downloader.start();
                    if (downloader.isCanceled()) {
                        if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("CANCELED,"+url+","+tableItem.getSortIndex());}}
                        return;
                    }

                    // Check if an error has occurred during the download
                    // if so, early escape the task so further error won't be overridden the initial error
                    if (tableItem.isFailed()) {
                        tableItem.setRunning(false);
                        log.warn("Download failed!");
                        if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("FAILED,"+url+","+tableItem.getSortIndex());}}
                        return;
                    }

                    if (!skipValidation) {
                        Target target = getTargetFromContainer(content);
                        if (target == null) {
                            log.error("Failed to resolve target in experimental Validator, skipping validator");
                        } else {
                            if (validatorTargets.contains(target)) {
                                ValidatorResponse response = validateVideoFile(file, outputFolder);
                                if (response != ValidatorResponse.VALID) {
                                    tableItem.setValidationError(true);
                                    log.warn("Validation Error: {}", response.getDescription());
                                    changeObject(content, "state", "Validation Error: " + response.getDescription());
                                    if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("VALIDATION ERROR,"+url+","+tableItem.getSortIndex());}}
                                    return;
                                }
                            }
                        }
                    }

                    changeObject(content, "state", "Completed");
                    if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("DONE,"+url+","+tableItem.getSortIndex());}}

                    File targetFile = new File(outputFolder, file.getName());
                    log.debug("Moving file from " + file.getAbsolutePath() + " to " + targetFile);
                    Files.move(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("MOVED,"+url+","+tableItem.getSortIndex());}}

                    // Check if we have an animeId
                    // the animeId is intended to only be used for AutoLoader
                    // it is used to rescan existing anime and send the count of missing episodes to the client
                    if (autoloaderData != null) {
                        int animeId = autoloaderData.get("animeId", Integer.class);
                        Anime anime = autoLoaderHandler.getAnimeByID(animeId);
                        anime.scanDirectoryForExistingEpisodes();
                        WebSocketUtils.sendAutoLoaderItem(null, anime);
                    }
                } catch (Exception ex) {
                    log.error("Failed to download", ex);
                    if (!ex.getMessage().contains("File.getName()"))
                        updateEvent.onError(ex.getMessage());
                } finally {
                    Utils.safeDelete(downloadTempFolder);
                }
                tableItem.setRunning(false);
            });
            tableItem.setTask(downloadTask);
            downloadHandler.putTask(downloadTask);

            // Resolve the title
            // After the title is resolved, the download task is scheduled
            titleResolverHandler.putTask(() -> {
                if (tableItem.isDeleted())
                    return;
                String title = downloader.resolveTitle();
                if (title != null)
                    changeObject(content, "title", title);
            });
        } catch (Exception ex) {
            Utils.safeDelete(downloadTempFolder);
        }
    }

    private DownloadStatusUpdateEvent createDownloadStatusUpdateEvent(TableItemDTO tableItem, JSONObjectContainer content) {
        return new DownloadStatusUpdateEvent() {
            @Override
            public void onProgressUpdate(double v) {
                tableItem.update();
                if (tableItem.isDeleted())
                    return;
                if (v >= 1) {
                    changeObject(content, "state", "Completed");
                } else {
                    long currentTime = System.currentTimeMillis();
                    
                    // Initialize download start time on first progress update
                    if (tableItem.getDownloadStartTime() == 0) {
                        tableItem.setDownloadStartTime(currentTime);
                        tableItem.setLastProgress(v);
                    }
                    
                    String progressText = "Downloading - " + Math.round(v * 10000.0) / 100.0 + "%";
                    
                    // Calculate ETA if we have meaningful progress (>5%) and some time has passed
                    if (v > 0.05 && currentTime - tableItem.getDownloadStartTime() > 10000) { // Wait at least 10 seconds
                        long elapsedTime = currentTime - tableItem.getDownloadStartTime();
                        double progressMade = v;
                        
                        if (progressMade > 0) {
                            double downloadRate = progressMade / (elapsedTime / 1000.0); // progress per second
                            double remainingProgress = 1.0 - v;
                            long etaSeconds = Math.round(remainingProgress / downloadRate);
                            
                            if (etaSeconds > 0 && etaSeconds < 86400) { // Cap at 24 hours
                                String etaText = formatETA(etaSeconds);
                                progressText += " (ETA: " + etaText + ")";
                            }
                        }
                    }
                    
                    changeObject(content, "state", progressText);
                    tableItem.setLastProgress(v);
                }
            }

            @Override
            public void onInfo(String s) {
                if (log.isDebugEnabled())
                    log.debug(s);
            }

            @Override
            public void onWarn(String s) {
                log.warn(s);
            }

            @Override
            public void onError(String s) {
                if (tableItem.isRunning() && !tableItem.isDeleted()) {
                    String line = s.split("\n")[0];
                    if (line.length() > 50)
                        s = s.substring(0, 50) + "\n" + s.substring(50);
                    changeObject(content, "state", "Error: " + s);
                    tableItem.update();
                }
            }

            @Override
            public void onLogFile(String fileName, byte[] bytes) {
                if (downloadLogFiles) {
                    try (FileOutputStream fos = new FileOutputStream(new File(downloadLogFolder, fileName))) {
                        fos.write(bytes);
                    } catch (IOException e) {
                        log.error("Failed to write log file", e);
                    }
                }
            }

            @Override
            public void onException(Throwable error) {
                tableItem.update();
                if (log.isDebugEnabled()) {
                    log.debug("Download failed!", error);
                }
                if (tableItem.isRunning() && !tableItem.isDeleted()) {
                    tableItem.setFailed(true);
                    changeObject(content, "state", "Error: " + error.getMessage());
                }
            }
        };
    }

    private ValidatorResponse validateVideoFile(File file, File outputFolder) throws IOException {
        // Return valid when the file is not mp4
        // Currently the validation only supports mp4 files
        if (!file.getName().endsWith(".mp4"))
            return ValidatorResponse.VALID;

        // Return valid when no other files are found
        File[] files = outputFolder.getParentFile().listFiles();
        if (files == null)
            return ValidatorResponse.VALID;

        List<File> filesInFolder = new ArrayList<>(Arrays.asList(files));
        long videoLength = MP4Utils.getVideoDurationSeconds(file);
        long totalLength = 0;
        int cnt = 0;

        for (File f : filesInFolder) {
            if (!f.getName().endsWith(".mp4"))
                continue;
            long fileLength = MP4Utils.getVideoDurationSeconds(f);
            if (fileLength == -1)
                continue;
            if (cnt == 0)
                totalLength += fileLength;
            else {
                double avg = (double)totalLength/(double)cnt;
                if ((Math.max(avg,fileLength)/Math.min(avg,fileLength))-1 < validatorVideoLengthThreshold) {
                    totalLength += fileLength;
                } else
                    log.warn("Video length is not consistent for " + f.getName() + " ignoring it for validation.");
            }
            cnt++;
        }

        double avg = (double)totalLength/(double)cnt;
        if ((Math.max(avg,videoLength)/Math.min(avg,videoLength))-1 < validatorVideoLengthThreshold) {
            return ValidatorResponse.VIDEO_LENGTH;
        }

        return ValidatorResponse.VALID;
    }

    private void startSystemDataFetcher() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sendSystemInformation(null);
            }
        }, 0, 5000);
        if (config.getBoolean("proxy.enabled", () -> false)) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    checkTorNetwork();
                }
            }, 0, 30000);
        }
    }

    /**
     * Sends system information to the specified WebSocket client.
     */
    private void sendSystemInformation(WsContext ctx) {
        try {
            if (MediaManager.getInstance().isDockerEnvironment()) {
                dockerMemoryLimit = Long.parseLong(new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.max").start().getInputStream())).trim());
                dockerMemoryUsage = Long.parseLong(new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.current").start().getInputStream())).trim());
            }
            sendPacket("systemInfo", TargetSystem.DEFAULT, formatSystemInfo(), ctx);
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    /**
     * Sends the list of available target folders to the specified WebSocket client.
     * These folders represent the base directories where files can be downloaded to.
     * 
     * @param ctx The WebSocket connection to send the folder list to
     */
    @SuppressWarnings("unchecked")
    private void sendTargetFolders(WsContext ctx) {
        JSONObject response = new JSONObject();
        JSONArray array = new JSONArray();
        for (Target value : targets.values()) {
            JSONObject segment = new JSONObject();
            segment.put("identifier", value.identifier());
            segment.put("displayName", value.displayName());
            array.add(segment);
        }
        response.put("targets", array);
        sendPacket("targetFolders", TargetSystem.DEFAULT, response, ctx);
    }

    /**
     * Deletes a download object.
     * 
     * @param toDelete The download item to delete
     * @param removed List to which the deleted item will be added
     * @return WebSocketResponse indicating success or failure, or null if successful
     */
    private WebSocketResponse deleteObject(TableItemDTO toDelete, List<TableItemDTO> removed) {
        if (toDelete.getTask() != null) {
            if (toDelete.getTask().isRunning()) {
                if (!toDelete.getDownloader().cancel()) {
                    return WebSocketResponse.WARN.setMessage("Failed to abort task! Downloader does not allow canceling");
                }
            } else {
                if (!downloadHandler.abortTask(toDelete.getTask()) && !toDelete.getTask().isCompleted()) {
                    return WebSocketResponse.WARN.setMessage("Failed to abort task! internal error!");
                }
            }
        }
        removed.add(toDelete);
        toDelete.setDeleted(true);
        return null;
    }

    /**
     * Checks the status of the Tor network by making a request to the Tor project's check.torproject.org API.
     */
    private void checkTorNetwork() {
        JSONObject result = new JSONObject();

        for (Proxy proxy : ProxyHandler.getProxies()) {
            try {
                HTTPResult response = SmartHTTP.request(new HTTPRequestOptions("https://check.torproject.org/api/ip").setProxy(proxy));
                JSONObject responseJ = (JSONObject) new JSONParser().parse(new String(response.getData()));
                result.put(proxy.address().toString(), responseJ.get("IP").toString());
            } catch (IOException | ParseException ex) {
                log.error(ex.getMessage());
                result.put(proxy.address().toString(), "NOT CONNECTED");
            }
        }

        torNetworkStatus = result;
    }

    /**
     * Collects and formats system information into a JSON object.
     * 
     * @return JSONObject containing system metrics including memory usage, thread pool information, etc.
     */
    @SuppressWarnings("unchecked")
    private JSONObject formatSystemInfo() {
        JSONObject response = new JSONObject();
        long currentTime = System.currentTimeMillis();

        JSONObject torNetwork = torNetworkStatus;
        response.put("torNetwork", torNetwork);

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        
        JSONObject memory = new JSONObject();
        memory.put("current", GUIUtils.formatStorageSpace(usedMemory));
        memory.put("heap", GUIUtils.formatStorageSpace(totalMemory));
        memory.put("max", GUIUtils.formatStorageSpace(maxMemory));
        memory.put("currentBytes", usedMemory);
        memory.put("heapBytes", totalMemory);
        memory.put("maxBytes", maxMemory);
        memory.put("usagePercent", Math.round((double) usedMemory / maxMemory * 100.0));
        response.put("memory", memory);

        // Store memory history
        JSONObject memoryPoint = new JSONObject();
        memoryPoint.put("timestamp", currentTime);
        memoryPoint.put("usagePercent", Math.round((double) usedMemory / maxMemory * 100.0));
        memoryPoint.put("usedBytes", usedMemory);
        memoryHistory.offer(memoryPoint);
        if (memoryHistory.size() > MAX_HISTORY_SIZE) {
            memoryHistory.poll();
        }

        JSONObject docker = new JSONObject();
        docker.put("memoryLimit", GUIUtils.formatStorageSpace(dockerMemoryLimit));
        docker.put("memoryUsage", GUIUtils.formatStorageSpace(dockerMemoryUsage));
        docker.put("memoryLimitBytes", dockerMemoryLimit);
        docker.put("memoryUsageBytes", dockerMemoryUsage);
        if (dockerMemoryLimit > 0) {
            docker.put("usagePercent", Math.round((double) dockerMemoryUsage / dockerMemoryLimit * 100.0));
        }
        response.put("docker", docker);

        ThreadPoolExecutor threadPoolExecutor = ((ThreadPoolExecutor) downloadHandler.getExecutorService());
        JSONObject threadPool = new JSONObject();

        Map<String, AtomicInteger> map = new HashMap<>();
        for (Thread downloadThread : downloadHandler.getThreadList())
            map.computeIfAbsent(downloadThread.getState().toString(), k -> new AtomicInteger(0)).incrementAndGet();

        threadPool.putAll(map);
        threadPool.put("max", threadPoolExecutor.getMaximumPoolSize());
        threadPool.put("active", threadPoolExecutor.getActiveCount());
        threadPool.put("queued", threadPoolExecutor.getQueue().size());
        threadPool.put("completed", threadPoolExecutor.getCompletedTaskCount());
        response.put("threadPool", threadPool);

        // Store thread history (excluding completed counter)
        JSONObject threadPoint = new JSONObject();
        threadPoint.put("timestamp", currentTime);
        threadPoint.putAll(map); // Thread states (NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED)
        threadPoint.put("max", threadPoolExecutor.getMaximumPoolSize());
        threadPoint.put("active", threadPoolExecutor.getActiveCount());
        threadPoint.put("queued", threadPoolExecutor.getQueue().size());
        // Explicitly exclude completed counter from history
        threadHistory.offer(threadPoint);
        if (threadHistory.size() > MAX_HISTORY_SIZE) {
            threadHistory.poll();
        }

        JSONObject system = new JSONObject();
        system.put("availableProcessors", runtime.availableProcessors());
        system.put("totalDownloads", urls.size());
        system.put("activeDownloads", (int) urls.values().stream().filter(TableItemDTO::isRunning).count());
        system.put("failedDownloads", (int) urls.values().stream().filter(TableItemDTO::isFailed).count());
        system.put("completedDownloads", (int) threadPoolExecutor.getCompletedTaskCount());
        response.put("system", system);

        // Store download history
        JSONObject downloadPoint = new JSONObject();
        downloadPoint.put("timestamp", currentTime);
        downloadPoint.put("total", urls.size());
        downloadPoint.put("active", (int) urls.values().stream().filter(TableItemDTO::isRunning).count());
        downloadPoint.put("failed", (int) urls.values().stream().filter(TableItemDTO::isFailed).count());
        downloadPoint.put("completed", (int) threadPoolExecutor.getCompletedTaskCount());
        downloadHistory.offer(downloadPoint);
        if (downloadHistory.size() > MAX_HISTORY_SIZE) {
            downloadHistory.poll();
        }

        JSONObject aniworld = new JSONObject();
        AniworldHelper.getStatistics().forEach((k, v) -> aniworld.put(k, v.get()));
        response.put("aniworld", aniworld);

        // Add historical data
        response.put("memoryHistory", convertHistoryData(memoryHistory, "timestamp", "usagePercent", "usedBytes"));
        response.put("downloadHistory", convertHistoryData(downloadHistory, "timestamp", "total", "active", "failed", "completed"));
        response.put("threadHistory", convertHistoryData(threadHistory, "timestamp", "active", "queued", "RUNNABLE", "WAITING", "NEW", "BLOCKED", "TIMED_WAITING", "TERMINATED", "max"));

        JSONObject version = new JSONObject();
        version.put("downloaders", MediaManager.getInstance().getDownloadersVersion());
        version.put("ultimateutils", MediaManager.getInstance().getUltimateutilsVersion());
        response.put("version", version);

        return response;
    }

    @SuppressWarnings("unchecked")
    private JSONObject convertHistoryData(Queue<JSONObject> history, String... fieldNames) {
        JSONObject convertedData = new JSONObject();
        
        if (history.isEmpty()) {
            for (String fieldName : fieldNames) {
                convertedData.put(fieldName, new JSONArray());
            }
            return convertedData;
        }
        
        Map<String, JSONArray> fieldArrays = new HashMap<>();
        for (String fieldName : fieldNames) {
            fieldArrays.put(fieldName, new JSONArray());
        }
        
        for (JSONObject point : history) {
            for (String fieldName : fieldNames) {
                Object value = point.get(fieldName);
                if (value != null) {
                    fieldArrays.get(fieldName).add(value);
                } else {
                    fieldArrays.get(fieldName).add(0);
                }
            }
        }
        
        convertedData.putAll(fieldArrays);
        return convertedData;
    }

    private Target getTargetFromContainer(JSONObjectContainer content) {
        String targetPath = content.get("target", String.class);
        log.debug("Resolving target: " + targetPath.split("/")[0]);
        Target target = targets.get(targetPath.split("/")[0]);
        log.debug("Target resolved!: " + target);
        return target;
    }

    /**
     * Resolves and validates the output folder for a download based on the provided content.
     * Creates the directory structure if it doesn't exist.
     *
     * @param content JSON data containing the target path and other download parameters
     * @return The resolved output folder as a File object
     * @throws WebSocketResponseException if the target path is invalid or inaccessible
     */
    private File resolveOutputFolder(WsContext ctx, JSONObjectContainer content) {
        String targetPath = content.get("target", String.class);
        Target target = getTargetFromContainer(content);

        if (target == null || target.path() == null)
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid Target " + content.get("target", String.class)));

        String subDirectory = targetPath.replace(targetPath.split("/")[0] + "/", "");
        if (target.subFolders() && subDirectory.isEmpty()) {
            String aniworldUrl = content.get("aniworld-url", String.class);
            if (aniworldUrl != null && !aniworldUrl.isEmpty()) {
                subDirectory = AniworldHelper.getAnimeTitle(aniworldUrl);
                WebSocketUtils.sendWebsSocketResponse(ctx, WebSocketResponse.WARN.setMessage("Subdirectory not specified, using " + subDirectory + " instead"), TargetSystem.DEFAULT, "put");
            } else
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Subdirectory not specified"));
        }

        File outputFolder;
        if (target.subFolders() && subDirectory != null)
            outputFolder = new File(target.path(), Utils.escape(subDirectory));
        else
            outputFolder = new File(target.path());

        log.debug("Output Folder: " + outputFolder.getAbsolutePath());
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            log.error("Failed to create output folder " + outputFolder.getAbsolutePath());
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to create output folder " + outputFolder.getAbsolutePath()));
        }
        return outputFolder;
    }

    /**
     * Formats the estimated time remaining into a human-readable string.
     * 
     * @param etaSeconds The estimated time remaining in seconds
     * @return Formatted ETA string (e.g., "2m 30s", "1h 15m", "45s")
     */
    private String formatETA(long etaSeconds) {
        if (etaSeconds < 60) {
            return etaSeconds + "s";
        } else if (etaSeconds < 3600) {
            long minutes = etaSeconds / 60;
            long seconds = etaSeconds % 60;
            return minutes + "m" + (seconds > 0 ? " " + seconds + "s" : "");
        } else {
            long hours = etaSeconds / 3600;
            long minutes = (etaSeconds % 3600) / 60;
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
    }


    /**
     * Internal method for other handlers to schedule downloads directly.
     * This bypasses WebSocket/REST API layers for internal Java-to-Java communication.
     * 
     * @param downloadItems List of download items to schedule
     */
    public void scheduleDownloads(List<JSONObjectContainer> downloadItems) {
        for (JSONObjectContainer item : downloadItems) {
            try {
                scheduleDownload(null, item); // null context since this is internal
            } catch (WebSocketResponseException ex) {
                log.warn("Failed to schedule internal download: {}", ex.getMessage());
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
    }

    @OpenApi(
        summary = "Gets the subfolders for the requested target",
        operationId = "getSubfolders",
        path = "/api/subfolders/{target}",
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
        methods = HttpMethod.POST,
        responses = {
            @OpenApiResponse(status = "200", description = "Downloads added successfully"),
            @OpenApiResponse(status = "400", description = "Invalid request data"),
            @OpenApiResponse(status = "500", description = "Server error")
        }
    )
    public void addDownloadsRequest(Context ctx) {
        try {
            JSONObjectContainer requestData = (JSONObjectContainer) JSONReader.readString(ctx.body());
            
            for (Object data : requestData.getArrayContainer("list").getRaw()) {
                JSONObjectContainer item = (JSONObjectContainer) JSONReader.readString(data.toString());
                try {
                    scheduleDownload(null, item); // Pass null for WebSocket context since this is REST
                } catch (WebSocketResponseException ex) {
                    // For REST API, return error response instead of WebSocket message
                    ctx.status(400);
                    ctx.json(Map.of("error", ex.getResponse()));
                    return;
                }
            }
            
            // Return success response
            ctx.status(200);
            ctx.json(Map.of("status", "success", "message", "Downloads added successfully"));
            
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
    public void deleteDownloadRequest(Context ctx) {
        try {
            String uuidStr = ctx.pathParam("uuid");
            UUID uuid = UUID.fromString(uuidStr);

            List<TableItemDTO> toDelete = new ArrayList<>();
            TableItemDTO item = urls.get(uuid);
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
     * REST API endpoint to delete all downloads from the queue.
     * Removes all downloads and cancels running ones.
     */
    @OpenApi(
        summary = "Delete all downloads from the queue",
        operationId = "deleteAllDownloads",
        path = "/api/downloads",
        methods = HttpMethod.DELETE,
        responses = {
            @OpenApiResponse(status = "200", description = "All downloads deleted successfully"),
            @OpenApiResponse(status = "500", description = "Server error")
        }
    )
    public void deleteAllDownloadsRequest(Context ctx) {
        try {
            List<TableItemDTO> toDelete = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            for (TableItemDTO value : new HashMap<>(urls).values()) {
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
            for (TableItemDTO tableItemDTO : toDelete) {
                urls.remove(tableItemDTO.getUuid());
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

    //endregion OpenAPI

}
