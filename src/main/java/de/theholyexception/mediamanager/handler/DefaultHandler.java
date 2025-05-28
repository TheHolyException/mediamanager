package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.holyapi.util.GUIUtils;
import de.theholyexception.mediamanager.*;
import de.theholyexception.mediamanager.models.TableItemDTO;
import de.theholyexception.mediamanager.models.Target;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.util.*;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.downloaders.DownloadStatusUpdateEvent;
import me.kaigermany.downloaders.Downloader;
import me.kaigermany.downloaders.DownloaderSelector;
import me.kaigermany.downloaders.FFmpeg;
import me.kaigermany.downloaders.voe.VOEDownloadEngine;
import me.kaigermany.ultimateutils.StaticUtils;
import me.kaigermany.ultimateutils.networking.smarthttp.HTTPRequestOptions;
import me.kaigermany.ultimateutils.networking.smarthttp.HTTPResult;
import me.kaigermany.ultimateutils.networking.smarthttp.SmartHTTP;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
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

import static de.theholyexception.mediamanager.MediaManager.getTomlConfig;
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
    private SettingProperty<Integer> spVoeThreads;
    
    /** Setting for retry delay in minutes */
    private SettingProperty<Integer> spRetryMinutes;

    private final FileLogger fLogger = new FileLogger("DefaultHandler");
    private boolean downloadLogFiles;
    private File downloadLogFolder;

    private TimerTask task;

    private JSONObject torNetworkStatus;

    /**
     * Creates a new DefaultHandler instance for the specified target system.
     * 
     * @param targetSystem The target system this handler is responsible for
     */
    public DefaultHandler(TargetSystem targetSystem) {
        super(targetSystem);
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

        spVoeThreads = Settings.getSettingProperty("VOE_THREADS", 1, systemSettings);
        spVoeThreads.addSubscriber(VOEDownloadEngine::setThreads);

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

        FFmpeg.setFFmpegPath(getTomlConfig().getString("general.ffmpeg"));


        downloadFolder = new File(getTomlConfig().getString("general.tmpDownloadFolder", () -> "./tmp"));
        if (!downloadFolder.exists() && !downloadFolder.mkdirs())
            log.error("Could not create download folder");

        this.downloadLogFiles = MediaManager.getTomlConfig().getBoolean("general.logDebugDownloaderFiles", () -> false);

        loadTargets();
    }

    /**
     * Initializes the handler after configurations are loaded.
     * Starts system data fetcher and sets up download log folder if enabled.
     */
    @Override
    public void initialize() {
        startSystemDataFetcher();

        if (this.downloadLogFiles) {
            String folderName = MediaManager.getTomlConfig().getString("general.logDebugDownloaderFolder", () -> "./downloader-logs");
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
        TomlArray targetArray = getTomlConfig().getArray("target");
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
     * @param socket The WebSocket connection that received the command
     * @param command The command to execute (e.g., "syn", "put", "del")
     * @param content JSON data associated with the command
     * @throws WebSocketResponseException if the command is invalid, processing fails or just a feedback for the client
     */
    @Override
    public void handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        switch (command) {
            case "syn" -> cmdSyncData(socket);
            case "put" -> cmdPutData(socket, content);
            case "del" -> cmdDelete(content);
            case "del-all" -> cmdDeleteAll(socket);
            case "setting" -> cmdChangeSetting(socket, content);
            case "requestSubfolders" -> cmdRequestSubFolders(socket, content);
            case "testDelay" -> {
                log.debug("Test delay");
                Utils.sleep(5000);
                throw new WebSocketResponseException(WebSocketResponse.OK.setMessage("Test delay done!"));
            }
            case "ping" -> sendPacket("pong", TargetSystem.DEFAULT, content.getRaw(), socket);
            case "systemInfo" -> sendSystemInformation(socket);
            default ->
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid command " + command));
        }
    }

    /**
     * Handles the 'syn' command to synchronize data with the client.
     * Sends the current state of downloads, settings, system information, and target folders
     * to the requesting client.
     *
     * @param socket The WebSocket connection to send the synchronized data to
     */
    private void cmdSyncData(WebSocketBasic socket) {
        List<JSONObjectContainer> jsonData = urls.values().stream()
                .sorted(TableItemDTO::compareTo)
                .map(TableItemDTO::getJsonObject)
                .toList();
        sendObject(socket, jsonData.stream().map(JSONObjectContainer::getRaw).toList());
        sendSettings(socket);
        sendSystemInformation(socket);
        sendTargetFolders(socket);
    }

    /**
     * Handles the 'put' command to add new download items to the queue.
     * Processes each item in the provided list and schedules downloads.
     *
     * @param socket The WebSocket connection that sent the command
     * @param content JSON data containing an array of download items to add
     */
    protected void cmdPutData(WebSocketBasic socket, JSONObjectContainer content) {
        for (Object data : content.getArrayContainer("list").getRaw()) {
            JSONObjectContainer item = (JSONObjectContainer) JSONReader.readString(data.toString());
            try {
                scheduleDownload(socket, item);
            } catch (WebSocketResponseException ex) {
                WebSocketUtils.sendWebsSocketResponse(socket, ex.getResponse(), TargetSystem.DEFAULT, "put");
            }
        }
    }

    /**
     * Handles the 'del' command to remove a download from the queue.
     * 
     * @param content JSON data containing the UUID of the download to remove
     * @throws WebSocketResponseException if the download ID is invalid or removal fails
     */
    private void cmdDelete(JSONObjectContainer content) {
        UUID uuid = UUID.fromString(content.get("uuid", String.class));

        List<TableItemDTO> toDelete = new ArrayList<>();
        TableItemDTO item = urls.get(uuid);
        if (item == null)
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to find object, is it already deleted?"));

        WebSocketResponse response = deleteObject(item, toDelete);
        if (response != null)
            throw new WebSocketResponseException(response);

        // Skip if nothing to delete
        if (toDelete.isEmpty())
            return;

        deleteObjectToAll(toDelete);
        urls.remove(uuid);
    }

    /**
     * Handles the 'del-all' command to clear all downloads from the queue.
     * 
     * @param socket The WebSocket connection that sent the command
     */
    private void cmdDeleteAll(WebSocketBasic socket) {
        List<TableItemDTO> toDelete = new ArrayList<>();
        for (TableItemDTO value : new HashMap<>(urls).values()) {
            WebSocketResponse response = deleteObject(value, toDelete);
            if (response != null)
                WebSocketUtils.sendWebsSocketResponse(socket, response, TargetSystem.DEFAULT, "del-all");
        }

        // Skip if nothing to delete
        if (toDelete.isEmpty())
            return;

        deleteObjectToAll(toDelete);
        for (TableItemDTO tableItemDTO : toDelete)
            urls.remove(tableItemDTO.getUuid());
    }

    /**
     * Handles the 'setting' command to update application settings.
     * 
     * @param basic The WebSocket connection that sent the command
     * @param content JSON data containing the settings to update
     */
    private void cmdChangeSetting(WebSocketBasic basic, JSONObjectContainer content) {
        JSONArrayContainer settings = content.getArrayContainer("settings");
        for (Object o : settings.getRaw()) {
            try {
                JSONObject setting = (JSONObject) o;
                String key = (String) setting.get("key");
                String val = (String) setting.get("value");
                switch (key) {
                    case "VOE_THREADS" -> spVoeThreads.setValue(Integer.parseInt(val));
                    case "PARALLEL_DOWNLOADS" -> spDownloadThreads.setValue(Integer.parseInt(val));
                    case "RETRY_MINUTES" -> spRetryMinutes.setValue(Integer.parseInt(val));
                    default -> throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid setting " + key));
                }
                log.info("Changed setting {} to: {}", key, val);
            } catch (Exception ex) {
                WebSocketUtils.sendPacket("response", TargetSystem.DEFAULT, WebSocketResponse.ERROR.setMessage(ex.getMessage()).getResponse().getRaw(), basic);
            }
        }
        sendSettings(null);
    }

    /**
     * Handles the 'requestSubfolders' command to retrieve subfolders for a given target.
     * 
     * @param socket The WebSocket connection to send the subfolder list to
     * @param content JSON data containing the target to scan for subfolders
     */
    @SuppressWarnings("unchecked")
    private void cmdRequestSubFolders(WebSocketBasic socket, JSONObjectContainer content) {
        String targetPath = content.get("selection", String.class);
        Target target = targets.get(targetPath.split("/")[0]);

        if (!target.subFolders())
            return;

        JSONObject response = new JSONObject();
        JSONArray array = new JSONArray();
        File subFolder = new File(target.path());
        if (subFolder.listFiles() != null)
            array.addAll(Arrays.stream(subFolder.listFiles())
                    .filter(File::isDirectory)
                    .map(File::getName)
                    .toList());
        response.put("subfolders", array);

        sendPacket("requestSubfoldersResponse", TargetSystem.DEFAULT, response, socket);
    }
    // endregion commands

    @SuppressWarnings("unchecked")
    /**
     * Schedules a download task based on the provided content.
     * Validates the input and creates a new download task if all requirements are met.
     * 
     * @param socket The WebSocket connection that requested the download (can be null for retries or autoloader)
     * @param content JSON data containing download information
     * @throws WebSocketResponseException if the download cannot be scheduled
     */
    private void scheduleDownload(WebSocketBasic socket, JSONObjectContainer content) {
        File outputFolder = putDownloadResolveOutputFolder(socket, content);

        TableItemDTO tableItem = new TableItemDTO(content);
        urls.put(UUID.fromString(content.get("uuid", String.class)), tableItem);
        changeObject(content,
                "state", "Committed",
                "sortIndex", tableItem.getSortIndex());

        String url = content.get("url", String.class);
        if (url == null || url.isEmpty())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid URL"));

        if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("PUT,"+url+","+tableItem.getSortIndex());}}

        AutoLoaderHandler autoLoaderHandler = ((AutoLoaderHandler)MediaManager.getInstance().getHandlers().get(TargetSystem.AUTOLOADER));
        JSONObjectContainer autoloaderData = content.getObjectContainer("autoloaderData");
        JSONObject options = content.getObjectContainer("options").getRaw();

        options.put("useDirectMemory", getTomlConfig().getBoolean("general.useDirectMemory", () -> false)+"");

        var updateEvent = new DownloadStatusUpdateEvent() {
            @Override
            public void onProgressUpdate(double v) {
                tableItem.update();
                if (tableItem.isDeleted())
                    return;
                if (v >= 1) {
                    changeObject(content, "state", "Completed");
                } else {
                    changeObject(content, "state", "Downloading - " + Math.round(v * 10000.0) / 100.0 + "%");
                }
            }

            @Override
            public void onInfo(String s) {
                // Not needed
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

        // Getting the right downloader
        File downloadTempFolder = new File(downloadFolder, UUID.randomUUID().toString());
        Downloader downloader = DownloaderSelector.selectDownloader(url, downloadTempFolder, updateEvent, options);
        downloader.setProxy(ProxyHandler.getNextProxy());
        tableItem.setDownloader(downloader);

        if (!downloadTempFolder.mkdirs() || !downloadTempFolder.exists())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to create temp folder"));

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
        } finally {
            Utils.safeDelete(downloadTempFolder);
        }
    }

    private void startSystemDataFetcher() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sendSystemInformation(null);
            }
        }, 0, 5000);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                checkTorNetwork();
            }
        }, 0, 30000);
    }

    /**
     * Sends system information to the specified WebSocket client.
     * 
     * @param target The WebSocket connection to send the information to
     */
    private void sendSystemInformation(WebSocketBasic target) {
        try {
            if (MediaManager.getInstance().isDockerEnvironment()) {
                dockerMemoryLimit = Long.parseLong(new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.max").start().getInputStream())).trim());
                dockerMemoryUsage = Long.parseLong(new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.current").start().getInputStream())).trim());
            }
            sendPacket("systemInfo", TargetSystem.DEFAULT, formatSystemInfo(), target);
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * Sends the list of available target folders to the specified WebSocket client.
     * These folders represent the base directories where files can be downloaded to.
     * 
     * @param socket The WebSocket connection to send the folder list to
     */
    private void sendTargetFolders(WebSocketBasic socket) {
        JSONObject response = new JSONObject();
        JSONArray array = new JSONArray();
        for (Target value : targets.values()) {
            JSONObject segment = new JSONObject();
            segment.put("identifier", value.identifier());
            segment.put("displayName", value.displayName());
            array.add(segment);
        }
        response.put("targets", array);
        sendPacket("targetFolders", TargetSystem.DEFAULT, response, socket);
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

    @SuppressWarnings("unchecked")
    /**
     * Collects and formats system information into a JSON object.
     * 
     * @return JSONObject containing system metrics including memory usage, thread pool information, etc.
     */
    private JSONObject formatSystemInfo() {
        JSONObject response = new JSONObject();

        JSONObject torNetwork = torNetworkStatus;
        response.put("torNetwork", torNetwork);

        JSONObject memory = new JSONObject();
        memory.put("current", GUIUtils.formatStorageSpace(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
        memory.put("heap", GUIUtils.formatStorageSpace(Runtime.getRuntime().totalMemory()));
        memory.put("max", GUIUtils.formatStorageSpace(Runtime.getRuntime().maxMemory()));
        response.put("memory", memory);

        JSONObject docker = new JSONObject();
        docker.put("memoryLimit", GUIUtils.formatStorageSpace(dockerMemoryLimit));
        docker.put("memoryUsage", GUIUtils.formatStorageSpace(dockerMemoryUsage));
        response.put("docker", docker);

        ThreadPoolExecutor threadPoolExecutor = ((ThreadPoolExecutor) downloadHandler.getExecutorService());
        JSONObject threadPool = new JSONObject();

        Map<String, AtomicInteger> map = new HashMap<>();
        for (Thread downloadThread : downloadHandler.getThreadList())
            map.computeIfAbsent(downloadThread.getState().toString(), k -> new AtomicInteger(0)).incrementAndGet();

		threadPool.putAll(map);
        threadPool.put("max", threadPoolExecutor.getMaximumPoolSize());
        threadPool.put("completed", threadPoolExecutor.getCompletedTaskCount());
        response.put("threadPool", threadPool);

        JSONObject aniworld = new JSONObject();
        AniworldHelper.getStatistics().forEach((k, v) -> aniworld.put(k, v.get()));
        response.put("aniworld", aniworld);

        return response;
    }

    /**
     * Resolves and validates the output folder for a download based on the provided content.
     * Creates the directory structure if it doesn't exist.
     * 
     * @param socket The WebSocket connection that requested the download
     * @param content JSON data containing the target path and other download parameters
     * @return The resolved output folder as a File object
     * @throws WebSocketResponseException if the target path is invalid or inaccessible
     */
    private File putDownloadResolveOutputFolder(WebSocketBasic socket, JSONObjectContainer content) {
        String targetPath = content.get("target", String.class);
        log.debug("Resolving target: " + targetPath.split("/")[0]);
        Target target = targets.get(targetPath.split("/")[0]);
        log.debug("Target resolved!: " + target);

        if (target == null || target.path() == null)
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid Target " + content.get("target", String.class)));

        String subDirectory = targetPath.replace(targetPath.split("/")[0] + "/", "");
        if (target.subFolders() && subDirectory.isEmpty()) {
            String aniworldUrl = content.get("aniworld-url", String.class);
            if (aniworldUrl != null && !aniworldUrl.isEmpty()) {
                subDirectory = AniworldHelper.getAnimeTitle(aniworldUrl);
                WebSocketUtils.sendWebsSocketResponse(socket, WebSocketResponse.WARN.setMessage("Subdirectory not specified, using " + subDirectory + " instead"), TargetSystem.DEFAULT, "put");
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

}
