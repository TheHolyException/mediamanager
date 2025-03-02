package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.TargetSystem;
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

import static de.theholyexception.mediamanager.webserver.WebSocketUtils.*;

@Slf4j
public class DefaultHandler extends Handler {

    private final Map<UUID, TableItemDTO> urls = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private final Map<String, Target> targets = new HashMap<>();

    private long dockerMemoryLimit;
    private long dockerMemoryUsage;
    private final ExecutorHandler executorHandler;
    private File downloadFolder;

    private SettingProperty<Integer> spDownloadThreads;
    private SettingProperty<Integer> spVoeThreads;

    public DefaultHandler(TargetSystem targetSystem) {
        super(targetSystem);
        executorHandler = new ExecutorHandler(Executors.newFixedThreadPool(1));
    }

    @Override
    public void loadConfigurations() {
        log.info("Loading Configurations");
        String systemSettings = "systemSettings";
        spDownloadThreads = Settings.getSettingProperty("PARALLEL_DOWNLOADS", 1, systemSettings);
        spVoeThreads = Settings.getSettingProperty("VOE_THREADS", 1, systemSettings);
        SettingProperty<String> spFFMPEGPath = Settings.getSettingProperty("FFMPEG", "ffmpeg", systemSettings);
        SettingProperty<String> spDownloadTempPath = Settings.getSettingProperty("DOWNLOAD_FOLDER", "./tmp", systemSettings);

        spDownloadThreads.addSubscriber(value -> executorHandler.updateExecutorService(Executors.newFixedThreadPool(value)));
        spVoeThreads.addSubscriber(VOEDownloadEngine::setThreads);
        spFFMPEGPath.addSubscriber(FFmpeg::setFFmpegPath);
        spDownloadTempPath.addSubscriber(value -> {
            downloadFolder = new File(value);
            if (!downloadFolder.exists())
                downloadFolder.mkdirs();
        });
        loadTargets();
    }

    @Override
    public void initialize() {
        cmdStartSystemDataFetcher();
    }

    private void loadTargets() {
        JSONArrayContainer targetsConfig = MediaManager.getInstance().getConfiguration().getJson().getArrayContainer("targets");
        for (Object object : targetsConfig.getRaw()) {
            JSONObject target = (JSONObject) object;
            Target tar = new Target(
                    target.get("identifier").toString()
                    , target.get("path").toString()
                    , target.containsKey("subFolders") && Boolean.parseBoolean(target.get("subFolders").toString()));
            targets.put(target.get("identifier").toString(), tar);
        }
    }

    @Override
    public WebSocketResponse handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        return switch (command) {
            case "syn" -> cmdSyncData(socket);

            case "put" -> cmdPutData(content);

            case "del" -> cmdDelete(content);

            case "del-all" -> cmdDeleteAll();

            case "setting" -> cmdChangeSetting(content);

            case "requestSubfolders" -> cmdRequestSubfolders(socket, content);

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
        cmdSendSystemInformation(socket);
        return null;
    }

    @SuppressWarnings("unchecked")
    protected WebSocketResponse cmdPutData(JSONObjectContainer content) {
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
        if (!outputFolder.exists()) outputFolder.mkdirs();

        TableItemDTO tableItem = new TableItemDTO(content);
        urls.put(UUID.fromString(content.get("uuid", String.class)), tableItem);
        changeObject(content.getRaw(), "state", "Committed");

        int animeId = content.get("animeId", -1, Integer.class);

        var updateEvent = new DownloadStatusUpdateEvent() {
            @Override
            public void onProgressUpdate(double v) {
                if (v >= 1) {
                    changeObject(content.getRaw(), "state", "Completed");
                } else {
                    changeObject(content.getRaw(), "state", "Downloading - " + Math.round(v * 10000.0) / 100.0 + "%");
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
                changeObject(content.getRaw(), "state", "Error: " + s);
            }

            @Override
            public void onLogFile(String s, byte[] bytes) {
                // Not implemented
            }
        };

        JSONObject options = content.getObjectContainer("options").getRaw();

        // Getting the right downloader
        Downloader downloader = DownloaderSelector.selectDownloader(url);

        // Start the download task
        ExecutorTask task = new ExecutorTask(() -> {
            try {
                File file = downloader.start(url, downloadFolder, updateEvent, options);
                File targetFile = new File(outputFolder, file.getName());
                log.debug("Moving file from " + file.getAbsolutePath() + " to " + targetFile);
                Files.move(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

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
        });
        tableItem.setTask(task);
        executorHandler.putTask(task);
        return null;
    }

    private WebSocketResponse cmdDelete(JSONObjectContainer content) {
        UUID uuid = UUID.fromString(content.get("uuid", String.class));
        TableItemDTO toDelete = urls.get(uuid);
        if (executorHandler.removeTask(toDelete.getTask())) {
            deleteObjectToAll(toDelete);
            urls.remove(uuid);
        } else {
            return WebSocketResponse.WARN.setMessage("Cannot remove task, already downloading!");
        }
        return null;
    }

    private WebSocketResponse cmdDeleteAll() {
        urls.values().forEach(WebSocketUtils::deleteObjectToAll);
        urls.clear();
        return null;
    }

    private WebSocketResponse cmdChangeSetting(JSONObjectContainer content) {
        String key = content.get("key", String.class);
        String val = content.get("val", String.class);

        log.info("Change setting " + key + " to: " + val);

        switch (key) {
            case "VOE_THREADS" -> spVoeThreads.setValue(Integer.parseInt(val));
            case "PARALLEL_DOWNLOADS" -> spDownloadThreads.setValue(Integer.parseInt(val));
            default -> WebSocketResponse.ERROR.setMessage("Unsupported setting: " + key);
        }
        sendSettings(null);
        return null;
    }

    @SuppressWarnings("unchecked")
    private WebSocketResponse cmdRequestSubfolders(WebSocketBasic socket, JSONObjectContainer content) {
        String targetPath = content.get("selection", String.class);
        Target target = targets.get(targetPath.split("/")[0]);

        if (!target.subFolders()) {
            sendWarn(socket, content.get("type", String.class), "No sub-folders are configured for " + target.identifier());
            return WebSocketResponse.WARN.setMessage("No sub-folders are configured for " + target.identifier());
        }

        JSONObject response = new JSONObject();
        JSONArray array = new JSONArray();
        File subFolder = new File(target.path());
        if (subFolder.listFiles() != null)
            array.addAll(Arrays.stream(subFolder.listFiles()).map(File::getName).toList());
        response.put("subfolders", array);
        sendPacket("requestSubfoldersResponse", TargetSystem.DEFAULT, response, socket);
        return null;
    }

    private void cmdStartSystemDataFetcher() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                cmdSendSystemInformation(null);
            }
        }, 0, 5000);
    }

    private void cmdSendSystemInformation(WebSocketBasic target) {
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
    private JSONObject formatSystemInfo() {
        JSONObject response = new JSONObject();
        response.put("heap", String.format("Java Runtime: %s/%s/%s"
                , StaticUtils.toHumanReadableFileSize(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
                , StaticUtils.toHumanReadableFileSize(Runtime.getRuntime().totalMemory())
                , StaticUtils.toHumanReadableFileSize(Runtime.getRuntime().maxMemory())
        ));
        if (MediaManager.getInstance().isDockerEnvironment()) {
            response.put("containerHeap", String.format("Docker Container: %s/%s"
                    , StaticUtils.toHumanReadableFileSize(dockerMemoryUsage)
                    , StaticUtils.toHumanReadableFileSize(dockerMemoryLimit)
            ));
        }
        ThreadPoolExecutor threadPoolExecutor = ((ThreadPoolExecutor) executorHandler.getExecutorService());
        response.put("handler", String.format("""
                        Active Count: %s
                        Core Size: %s
                        Pool Size: %s
                        Pool Size (Max): %s
                        Completed Tasks: %s
                        """
                , threadPoolExecutor.getActiveCount()
                , threadPoolExecutor.getCorePoolSize()
                , threadPoolExecutor.getPoolSize()
                , threadPoolExecutor.getMaximumPoolSize()
                , threadPoolExecutor.getCompletedTaskCount()
        ));
        return response;
    }

}
