package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.*;
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
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import java.io.File;
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
    private final AtomicInteger downloadHandlerFactoryCounter = new AtomicInteger(0);
    private final ExecutorHandler downloadHandler;
    private final ExecutorHandler titleResolverHandler;

    private File downloadFolder;
    private SettingProperty<Integer> spDownloadThreads;
    private SettingProperty<Integer> spVoeThreads;
    private final FileLogger fLogger = new FileLogger("DefaultHandler");


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
        spDownloadThreads.addSubscriber(value -> {
            downloadHandlerFactoryCounter.set(0);
            downloadHandler.updateExecutorService(Executors.newFixedThreadPool(value));
        });

        spVoeThreads = Settings.getSettingProperty("VOE_THREADS", 1, systemSettings);
        spVoeThreads.addSubscriber(VOEDownloadEngine::setThreads);

        FFmpeg.setFFmpegPath(getTomlConfig().getString("general.ffmpeg"));


        downloadFolder = new File(getTomlConfig().getString("general.tmpDownloadFolder"));
        if (!downloadFolder.exists() && !downloadFolder.mkdirs())
            log.error("Could not create download folder");

        loadTargets();
    }

    @Override
    public void initialize() {
        startSystemDataFetcher();
    }

    private void loadTargets() {
        TomlArray targetArray = getTomlConfig().getArray("target");
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
            WebSocketResponse response = scheduleDownload(socket, item);
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
    private WebSocketResponse scheduleDownload(WebSocketBasic socket, JSONObjectContainer content) {
        String url = content.get("url", String.class);
        if (url == null || url.isEmpty())
            return WebSocketResponse.ERROR.setMessage("Invalid URL " + url);

        String targetPath = content.get("target", String.class);
        log.debug("Resolving target: " + targetPath.split("/")[0]);
        Target target = targets.get(targetPath.split("/")[0]);
        log.debug("Target resolved!: " + target);

        if (target == null || target.path() == null)
            return WebSocketResponse.ERROR.setMessage("Invalid URL " + url);

        String subDirectory = targetPath.replace(targetPath.split("/")[0] + "/", "");
        if (target.subFolders() && subDirectory.isEmpty()) {
            String aniworldUrl = content.get("aniworld-url", String.class);
            if (aniworldUrl != null && !aniworldUrl.isEmpty()) {
                subDirectory = AniworldHelper.getAnimeTitle(aniworldUrl);
                WebSocketUtils.sendWebsSocketResponse(socket, WebSocketResponse.WARN.setMessage("Subdirectory not specified, using " + subDirectory + " instead"), TargetSystem.DEFAULT, "put");
            } else {
                return WebSocketResponse.ERROR.setMessage("No subdirectory specified");
            }
        }

        final File outputFolder;
        if (target.subFolders() && subDirectory != null)
            outputFolder = new File(target.path(), subDirectory);
        else
            outputFolder = new File(target.path());

        log.debug("Output Folder: " + outputFolder.getAbsolutePath());
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            log.error("Failed to create output folder " + outputFolder.getAbsolutePath());
            return WebSocketResponse.ERROR.setMessage("Failed to create output folder " + outputFolder.getAbsolutePath());
        }

        TableItemDTO tableItem = new TableItemDTO(content);
        urls.put(UUID.fromString(content.get("uuid", String.class)), tableItem);
        changeObject(content,
                "state", "Committed",
                "sortIndex", tableItem.getSortIndex());

        int animeId = content.get("animeId", -1, Integer.class);

        if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("PUT,"+url+","+tableItem.getSortIndex());}}

        var updateEvent = new DownloadStatusUpdateEvent() {
            @Override
            public void onProgressUpdate(double v) {
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
                //if (log.isDebugEnabled()) {
                //    log.debug(s);
                //}
            }

            @Override
            public void onWarn(String s) {
                log.warn(s);
            }

            @Override
            public void onError(String s) {
                if (tableItem.isRunning() && !tableItem.isDeleted()) {
                    changeObject(content, "state", "Error: " + s);
                }
            }

            @Override
            public void onLogFile(String s, byte[] bytes) {
                // Not implemented
            }

            @Override
            public void onException(Throwable error) {
                if (log.isDebugEnabled()) {
                    log.debug("Download failed!", error);
                }
                if (tableItem.isRunning() && !tableItem.isDeleted()) {
                    tableItem.setFailed(true);
                    changeObject(content, "state", "Error: " + error.getMessage());
                }
            }
        };

        JSONObject options = content.getObjectContainer("options").getRaw();

        // Getting the right downloader
        Downloader downloader = DownloaderSelector.selectDownloader(url, downloadFolder, updateEvent, options);
        tableItem.setDownloader(downloader);

        // Start the download
        // This task is only running when the titleTask is completed
        ExecutorTask downloadTask = new ExecutorTask(() -> {
            if (tableItem.isDeleted()) {
                if (log.isDebugEnabled()) {synchronized (fLogger) {fLogger.log("DELETED,"+url+","+tableItem.getSortIndex());}}
                return;
            }
            tableItem.setRunning(true);
            tableItem.setExecutingThread(Thread.currentThread());
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
        tableItem.setTask(downloadTask);
        downloadHandler.putTask(downloadTask);

        // Resolve the title
        // After the title is resolved, the download task is scheduled
        titleResolverHandler.putTask(() -> {
            if (tableItem.isDeleted())
                return;
            tableItem.setExecutingThread(Thread.currentThread());
            String title = downloader.resolveTitle();
            changeObject(content, "title", title);
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

}
