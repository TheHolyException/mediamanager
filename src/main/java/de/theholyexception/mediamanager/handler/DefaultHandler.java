package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.TargetSystem;
import de.theholyexception.mediamanager.Utils;
import de.theholyexception.mediamanager.models.TableItemDTO;
import de.theholyexception.mediamanager.models.Target;
import de.theholyexception.mediamanager.models.aniworld.Anime;
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
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static de.theholyexception.mediamanager.webserver.WebSocketUtils.*;

@Slf4j
public class DefaultHandler extends Handler {

    private final Map<UUID, TableItemDTO> urls = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private final Map<String, Target> targets = new HashMap<>();

    private long dockerMemoryLimit;
    private long dockerMemoryUsage;
    private final AtomicInteger downloadHandlerFactoryCounter = new AtomicInteger(0);
    private final ExecutorHandler downloadHandler;
    private final ExecutorHandler titleResolverHandler;
    private Thread watchDog;

    private File downloadFolder;
    private SettingProperty<Integer> spDownloadThreads;
    private SettingProperty<Integer> spVoeThreads;
    private SettingProperty<Integer> spRequestTimeout;

    public DefaultHandler(TargetSystem targetSystem) {
        super(targetSystem);
        downloadHandler = new ExecutorHandler(Executors.newFixedThreadPool(1));
        downloadHandler.setThreadNameFactory(cnt -> "DownloadThread-" + cnt);
        titleResolverHandler = new ExecutorHandler(Executors.newFixedThreadPool(2));
        titleResolverHandler.setThreadNameFactory(cnt -> "TitleResolver-" + cnt);
    }

    @Override
    public void loadConfigurations() {
        log.info("Loading Configurations");
        String systemSettings = "systemSettings";

        spDownloadThreads = Settings.getSettingProperty("PARALLEL_DOWNLOADS", 1, systemSettings);
        spDownloadThreads.addSubscriber(value -> {
            downloadHandlerFactoryCounter.set(0);
            downloadHandler.updateExecutorService(Executors.newFixedThreadPool(value));
        });

        spVoeThreads = Settings.getSettingProperty("VOE_THREADS", 1, systemSettings);
        spVoeThreads.addSubscriber(VOEDownloadEngine::setThreads);

        SettingProperty<String> spFFMPEGPath = Settings.getSettingProperty("FFMPEG", "ffmpeg", systemSettings);
        spFFMPEGPath.addSubscriber(FFmpeg::setFFmpegPath);

        SettingProperty<String> spDownloadTempPath = Settings.getSettingProperty("DOWNLOAD_FOLDER", "./tmp", systemSettings);
        spDownloadTempPath.addSubscriber(value -> {
            downloadFolder = new File(value);
            if (!downloadFolder.exists() && !downloadFolder.mkdirs())
                log.error("Could not create download folder");
        });

        spRequestTimeout = Settings.getSettingProperty("REQUEST_TIMEOUT", 60000, systemSettings);

        loadTargets();
    }

    @Override
    public void initialize() {
        startSystemDataFetcher();
        startWatchdog();
    }

    private void loadTargets() {
        JSONArrayContainer targetsConfig = MediaManager.getInstance().getConfiguration().getJson().getArrayContainer("targets");
        for (Object object : targetsConfig.getRaw()) {
            JSONObject target = (JSONObject) object;
            Target tar = new Target(
                    target.get("identifier").toString()
                    , target.containsKey("displayName") ? target.get("displayName").toString() : target.get("identifier").toString()
                    , target.get("path").toString()
                    , target.containsKey("subFolders") && Boolean.parseBoolean(target.get("subFolders").toString())
            );
            targets.put(target.get("identifier").toString(), tar);
        }
    }

    // region commands
    @Override
    public WebSocketResponse handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        return switch (command) {
            case "syn" -> cmdSyncData(socket);

            case "put" -> cmdPutData(socket, content);

            case "del" -> cmdDelete(content);

            case "del-all" -> cmdDeleteAll();

            case "setting" -> cmdChangeSetting(content);

            case "requestSubfolders" -> cmdRequestSubfolders(socket, content);
            case "testDelay" -> {
                log.debug("Test delay");
                Utils.sleep(5000);
                yield WebSocketResponse.OK.setMessage("Test delay done!");
            }
            case "ping" -> {
                sendPacket("pong", TargetSystem.DEFAULT, content.getRaw(), socket);
                yield null;
            }
            case "systemInfo" -> {
                sendSystemInformation(socket);
                yield null;
            }

            default -> {
                log.error("Invalid command " + command);
                yield WebSocketResponse.ERROR.setMessage("Invalid command " + command);
            }
        };
    }

    private WebSocketResponse cmdSyncData(WebSocketBasic socket) {
        List<JSONObjectContainer> jsonData = urls.values().stream()
                .sorted(TableItemDTO::compareTo)
                .map(TableItemDTO::getJsonObject)
                .toList();
        sendObject(socket, jsonData.stream().map(JSONObjectContainer::getRaw).toList());
        sendSettings(socket);
        sendSystemInformation(socket);
        sendTargetFolders(socket);
        return null;
    }

    protected WebSocketResponse cmdPutData(WebSocketBasic socket, JSONObjectContainer content) {
        for (Object data : content.getArrayContainer("list").getRaw()) {
            JSONObjectContainer item = (JSONObjectContainer) JSONReader.readString(data.toString());
            WebSocketResponse response = scheduleDownload(item);
            if (socket != null && response != null)
                WebSocketUtils.sendWebsSocketResponse(socket, response, TargetSystem.DEFAULT, "put");
        }
        return null;
    }

    private WebSocketResponse cmdDelete(JSONObjectContainer content) {
        UUID uuid = UUID.fromString(content.get("uuid", String.class));

        List<TableItemDTO> toDelete = new ArrayList<>();
        TableItemDTO item = urls.get(uuid);
        if (item == null)
            return WebSocketResponse.ERROR.setMessage("Failed to find object, is it already deleted?");

        WebSocketResponse response = deleteObject(item, toDelete);
        if (response != null)
            return response;

        if (toDelete.isEmpty())
            return null;

        deleteObjectToAll(toDelete);
        urls.remove(uuid);
        return null;
    }

    private WebSocketResponse cmdDeleteAll() {
        List<TableItemDTO> toDelete = new ArrayList<>();
        new HashMap<>(urls).values().forEach(object -> deleteObject(object, toDelete));
        if (toDelete.isEmpty())
            return null;

        deleteObjectToAll(toDelete);
        for (TableItemDTO tableItemDTO : toDelete)
            urls.remove(tableItemDTO.getUuid());
        return null;
    }

    private WebSocketResponse cmdChangeSetting(JSONObjectContainer content) {
        JSONArrayContainer settings = content.getArrayContainer("settings");
        for (Object o : settings.getRaw()) {
            JSONObject setting = (JSONObject) o;
            String key = (String) setting.get("key");
            String val = (String) setting.get("value");
            log.info("Change setting " + key + " to: " + val);
            switch (key) {
                case "VOE_THREADS" -> spVoeThreads.setValue(Integer.parseInt(val));
                case "PARALLEL_DOWNLOADS" -> spDownloadThreads.setValue(Integer.parseInt(val));
                default -> WebSocketResponse.ERROR.setMessage("Unsupported setting: " + key);
            }
        }
        sendSettings(null);
        return null;
    }

    @SuppressWarnings("unchecked")
    private WebSocketResponse cmdRequestSubfolders(WebSocketBasic socket, JSONObjectContainer content) {
        String targetPath = content.get("selection", String.class);
        Target target = targets.get(targetPath.split("/")[0]);

        if (!target.subFolders()) {
            return null;
        }

        JSONObject response = new JSONObject();
        JSONArray array = new JSONArray();
        File subFolder = new File(target.path());
        if (subFolder.listFiles() != null)
            array.addAll(Arrays.stream(subFolder.listFiles()).filter(File::isDirectory).map(File::getName).toList());
        response.put("subfolders", array);
        sendPacket("requestSubfoldersResponse", TargetSystem.DEFAULT, response, socket);
        return null;
    }
    // endregion commands

    @SuppressWarnings("unchecked")
    private WebSocketResponse scheduleDownload(JSONObjectContainer content) {
        String url = content.get("url", String.class);
        if (url == null || url.isEmpty())
            return WebSocketResponse.ERROR.setMessage("Invalid URL " + url);

        String targetPath = content.get("target", String.class);
        log.debug("Resolving target: " + targetPath.split("/")[0]);
        Target target = targets.get(targetPath.split("/")[0]);
        log.debug("Target resolved!: " + target);

        if (target == null || target.path() == null)
            return WebSocketResponse.ERROR.setMessage("Invalid URL " + url);

        File outputFolder = new File(target.path(), targetPath.replace(targetPath.split("/")[0] + "/", ""));
        log.debug("Output Folder: " + outputFolder.getAbsolutePath());
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            log.error("Failed to create output folder " + outputFolder.getAbsolutePath());
            return WebSocketResponse.ERROR.setMessage("Failed to create output folder " + outputFolder.getAbsolutePath());
        }

        TableItemDTO tableItem = new TableItemDTO(content);
        urls.put(UUID.fromString(content.get("uuid", String.class)), tableItem);
        changeObject(content, "state", "Committed");

        int animeId = content.get("animeId", -1, Integer.class);

        var updateEvent = new DownloadStatusUpdateEvent() {
            @Override
            public void onProgressUpdate(double v) {
                if (tableItem.isDeleted())
                    return;
                tableItem.update();
                if (v >= 1) {
                    changeObject(content, "state", "Completed");
                } else {
                    changeObject(content, "state", "Downloading - " + Math.round(v * 10000.0) / 100.0 + "%");
                }
            }

            @Override
            public void onInfo(String s) {
                // Not implemented
            }

            @Override
            public void onWarn(String s) {
                log.warn(s);
            }

            @Override
            public void onError(String s) {
                if (tableItem.isRunning() && !tableItem.isDeleted()) {
                    tableItem.update();
                    changeObject(content, "state", "Error: " + s);
                }
            }

            @Override
            public void onLogFile(String s, byte[] bytes) {
                // Not implemented
            }

        };

        JSONObject options = content.getObjectContainer("options").getRaw();

        // Getting the right downloader
        Downloader downloader = DownloaderSelector.selectDownloader(url, downloadFolder, updateEvent, options);
        tableItem.setDownloader(downloader);

        // Start the download
        // This task is only running when the titleTask is completed
        ExecutorTask downloadTask = new ExecutorTask(() -> {
            tableItem.setRunning(true);
            tableItem.setExecutingThread(Thread.currentThread());
            try {
                File file = downloader.start();
                if (downloader.isCanceled()) {
                    return;
                }
                tableItem.update();

                File targetFile = new File(outputFolder, file.getName());
                log.debug("Moving file from " + file.getAbsolutePath() + " to " + targetFile);
                Files.move(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Check if we have an animeId
                // the animeId is intended to only be used for AutoLoader
                // it is used to rescan existing anime and send the count of missing episodes to the client
                if (animeId != -1) {
                    Anime anime = ((AutoLoaderHandler)MediaManager.getInstance().getHandlers().get(TargetSystem.AUTOLOADER)).getAnimeByID(animeId);
                    anime.scanDirectoryForExistingEpisodes();
                    WebSocketUtils.sendAutoLoaderItem(null, anime);
                }
            } catch (Exception ex) {
                log.error("Failed to download", ex);
                if (!ex.getMessage().contains("File.getName()"))
                    updateEvent.onError(ex.getMessage());
            }
            tableItem.setRunning(false);
        });

        // Resolve the title
        // After the title is resolved, the download task is scheduled
        titleResolverHandler.putTask(() -> {
            tableItem.setExecutingThread(Thread.currentThread());
            tableItem.setResolving(true);
            String title = downloader.resolveTitle();
            changeObject(content, "title", title);
        }).onComplete(() -> {
            tableItem.setResolving(false);
            if (tableItem.isDeleted())
                return;
            // Schedule the download task
            tableItem.setTask(downloadTask);
            downloadHandler.putTask(downloadTask);
        });

        return null;
    }

    private void startSystemDataFetcher() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sendSystemInformation(null);
            }
        }, 0, 5000);
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
                    return WebSocketResponse.WARN.setMessage("Already running, download cannot be canceled!");
                }
            } else {
                if (!downloadHandler.abortTask(toDelete.getTask())) {
                    if (!toDelete.getTask().isCompleted())
                        return WebSocketResponse.WARN.setMessage("Failed to abort task! internal error!");
                }
            }
        }
        removed.add(toDelete);
        toDelete.setDeleted(true);
        return null;
    }

    @SuppressWarnings("unchecked")
    private JSONObject formatSystemInfo() {
        JSONObject response = new JSONObject();
        {
            JSONObject memory = new JSONObject();
            memory.put("current", Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            memory.put("heap", Runtime.getRuntime().totalMemory());
            memory.put("max", Runtime.getRuntime().maxMemory());
            response.put("memory", memory);

        }

        {
            JSONObject docker = new JSONObject();
            docker.put("memoryLimit", dockerMemoryLimit);
            docker.put("memoryUsage", dockerMemoryUsage);
            response.put("docker", docker);
        }

        {
            ThreadPoolExecutor threadPoolExecutor = ((ThreadPoolExecutor) downloadHandler.getExecutorService());
            JSONObject threadPool = new JSONObject();

            Map<String, AtomicInteger> map = new HashMap<>();
            for (Thread downloadThread : downloadHandler.getThreadList())
                map.computeIfAbsent(downloadThread.getState().toString(), k -> new AtomicInteger(0)).incrementAndGet();

            for (Map.Entry<String, AtomicInteger> entry : map.entrySet()) {
                threadPool.put(entry.getKey(), entry.getValue());
            }
            threadPool.put("max", threadPoolExecutor.getMaximumPoolSize());
            threadPool.put("completed", threadPoolExecutor.getCompletedTaskCount());
            response.put("threadPool", threadPool);
        }

        {
            JSONObject aniworld = new JSONObject();
            AniworldHelper.getStatistics().forEach((k, v) -> aniworld.put(k, v.get()));
            response.put("aniworld", aniworld);
        }

        return response;
    }

    private void startWatchdog() {
        watchDog = new Thread(() -> {
            log.info("Starting watchdog");
            while (true) {
                int requestTimeout = spRequestTimeout.getValue();
                for (TableItemDTO value : urls.values()) {
                    if (!value.isRunning() && !value.isResolving()) continue;
                    if (value.checkForTimeout(requestTimeout)) {
                        log.warn("Request timed out: {}", value.getJsonObject().get("url", String.class));
                        if (value.getExecutingThread() != null)
                            value.getExecutingThread().interrupt();
                        changeObject(value.getJsonObject(), "state", "Error: Timeout");
                        value.setResolving(false);
                        value.setRunning(false);
                    }
                }
                Utils.sleep(5000);
            }
        });
        watchDog.setName("Watchdog");
        watchDog.start();
    }

}
