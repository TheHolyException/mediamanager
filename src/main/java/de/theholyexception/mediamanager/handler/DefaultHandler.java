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

@Slf4j
public class DefaultHandler extends Handler {

    private final Map<UUID, TableItemDTO> urls = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private final Map<String, Target> targets = new HashMap<>();

    private long dockerMemoryLimit;
    private long dockerMemoryUsage;
    private final ExecutorHandler downloadHandler;
    private final ExecutorHandler titleResolverHandler;

    private File downloadFolder;
    private SettingProperty<Integer> spDownloadThreads;
    private SettingProperty<Integer> spVoeThreads;
    private SettingProperty<Integer> spRetryMinutes;
    private final FileLogger fLogger = new FileLogger("DefaultHandler");
    private boolean downloadLogFiles;
    private File downloadLogFolder;

    private TimerTask task;

    private JSONObject torNetworkStatus;

    public DefaultHandler(TargetSystem targetSystem) {
        super(targetSystem);
        downloadHandler = new ExecutorHandler(Executors.newFixedThreadPool(1));
        downloadHandler.setThreadNameFactory(cnt -> "DownloadThread-" + cnt);
        titleResolverHandler = new ExecutorHandler(Executors.newFixedThreadPool(5));
        titleResolverHandler.setThreadNameFactory(cnt -> "TitleResolver-" + cnt);
    }

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

        if (!downloadTempFolder.mkdirs())
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

        try {
            HTTPResult response = SmartHTTP.request(new HTTPRequestOptions("http://google.com").setTimeout(5000));
            result.put("google", "CONNECTED");
        } catch (Exception ex) {
            result.put("google", "NOT CONNECTED");
        }

        torNetworkStatus = result;
    }

    @SuppressWarnings("unchecked")
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
