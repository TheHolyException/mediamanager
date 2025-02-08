package de.theholyexception.mediamanager;

import de.theholyexception.holyapi.datastorage.json.JSONArrayContainer;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.configuration.ConfigJSON;
import de.theholyexception.mediamanager.models.TableItem;
import de.theholyexception.mediamanager.models.Target;
import de.theholyexception.mediamanager.settings.SettingProperty;
import de.theholyexception.mediamanager.settings.Settings;
import de.theholyexception.mediamanager.webserver.WebServer;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.downloaders.*;
import me.kaigermany.downloaders.dood.DoodDownloader;
import me.kaigermany.downloaders.voe.VOEDownloadEngine;
import me.kaigermany.ultimateutils.StaticUtils;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketEvent;
import org.json.simple.*;
import org.json.simple.parser.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class MediaManager {

    private static final HashMap<String, String> arguments = new HashMap<>();
    private static boolean debug = false;


    public static void main(String[] args) {
        parseArguments(args);
        new MediaManager();
    }


    /**
     * Parses the Start Arguments of the application in a HashMap
     *
     * @param args Start arguments that should be parsed
     */
    private static void parseArguments(String[] args) {
        for (String arg : args) {
            String[] s = arg.toLowerCase().split(":");
            if (s.length != 2) {
                if (s[0].startsWith("-")) {
                    arguments.put(s[0].substring(1), null);
                } else {
                    log.error("Invalid argument found: " + s[0]);
                }
                continue;
            }
            arguments.put(s[0], s[1]);
        }
        log.info("Loaded arguments: " + arguments.toString());
    }

    private void handleArguments() {
        if (arguments.containsKey("ffmpeg")) {
            FFmpeg.setFFmpegPath(arguments.get("ffmpeg"));
            log.info("FFMPEG Path: " + arguments.get("ffmpeg"));
        }

        if (arguments.containsKey("debug")) {
            debug = Boolean.parseBoolean(arguments.get("debug"));
            log.info("Debug mode enabled");
        }
    }

    private final Map<String, Target> targets = new HashMap<>();
    private long dockerMemoryLimit;
    private long dockerMemoryUsage;
    private boolean isDockerEnvironment = false;
    private final Map<UUID, TableItem> urls = Collections.synchronizedMap(new HashMap<>());
    private final List<WebSocketBasic> clientList = Collections.synchronizedList(new ArrayList<>());
    private ConfigJSON configuration;
    private ExecutorHandler executorHandler;
    private SettingProperty<Integer> downloadThreads;
    private SettingProperty<Integer> voeThreads;
    private SettingProperty<String> ffmpegPath;
    private SettingProperty<String> downloadFolderSetting;
    private File downloadFolder;


    public MediaManager() {
        executorHandler = new ExecutorHandler(Executors.newFixedThreadPool(1));
        loadConfiguration();
        handleArguments();
        checkForDockerEnvironment();
        startSystemDataFetcher();

        new WebServer(new WebServer.Configuration(8080, "0.0.0.0", "./www", new WebSocketEvent() {
            @Override
            public void onMessage(String data, WebSocketBasic socket) {
                try {
                    JSONObject dataset = (JSONObject) new JSONParser().parse(data);

                    String targetSystem = dataset.getOrDefault("targetSystem", "defaultSystem").toString();

                    switch (targetSystem) {
                        case "defaultSystem" -> processDefaultPackets(socket, dataset);
                        case "autoLoader" -> throw new IllegalStateException("Not implemented yet");
                        default -> throw new IllegalStateException("Invalid target-system: " + targetSystem);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void onOpen(WebSocketBasic socket) {
                clientList.add(socket);
            }

            @Override
            public void onClose(WebSocketBasic socket) {
                clientList.remove(socket);
            }

            @Override
            public void onError(WebSocketBasic socket, String message, Exception exception) {
                clientList.remove(socket);
            }
        }));
    }

    private void loadConfiguration() {
        configuration = new ConfigJSON(new File("./config.json"));
        configuration.loadConfig();
        if (!configuration.getFile().exists()) {
            URL url = MediaManager.class.getClassLoader().getResource("config-template.json");
            try (FileInputStream fis = new FileInputStream(new File(url.toURI()))) {
                boolean result = configuration.createNewIfNotExists(fis);
                if (result) log.info("New configuration created");
            } catch (IOException | URISyntaxException ex) {
                ex.printStackTrace();
            }
        }
        loadTargets();
        Settings.init(configuration);

        downloadThreads = Settings.getSettingProperty("PARALLEL_DOWNLOADS", 1);
        voeThreads = Settings.getSettingProperty("VOE_THREADS", 1);
        ffmpegPath = Settings.getSettingProperty("FFMPEG", "ffmpeg");
        downloadFolderSetting = Settings.getSettingProperty("DOWNLOAD_FOLDER", "./tmp");


        downloadThreads.addSubscriber(value -> executorHandler.updateExecutorService(Executors.newFixedThreadPool(value)));
        voeThreads.addSubscriber(VOEDownloadEngine::setThreads);
        ffmpegPath.addSubscriber(FFmpeg::setFFmpegPath);
        downloadFolderSetting.addSubscriber(value -> {
            downloadFolder = new File(value);
            if (!downloadFolder.exists())
                downloadFolder.mkdirs();
        });
    }

    private void loadTargets() {
        JSONArrayContainer targetsConfig = configuration.getJson().getArrayContainer("targets");
        for (Object object : targetsConfig.getRaw()) {
            JSONObject target = (JSONObject) object;
            Target tar = new Target(
                      target.get("identifier").toString()
                    , target.get("path").toString()
                    , target.containsKey("subFolders") ? Boolean.valueOf(target.get("subFolders").toString()) : false);
            targets.put(target.get("identifier").toString(), tar);
        }
    }

    private void checkForDockerEnvironment() {
        try {
            String a = new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.max").start().getInputStream()));
            if (!a.isEmpty()) isDockerEnvironment = true;
            return;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        isDockerEnvironment = false;
        log.warn("No docker environment!");
    }

    private void processDefaultPackets(WebSocketBasic socket, JSONObject dataset) {
        switch (dataset.get("type").toString()) {
            case "syn" -> syncData(socket);

            case "put" -> putData(dataset);

            case "del" -> delete(dataset);

            case "del-all" -> deleteAll();

            case "setting" -> changeSetting(dataset);

            case "resolveAniworld" -> resolveAniworld(socket, dataset);

            case "targetSelection" -> targetSelection(socket, dataset);

            default -> log.error("invalid dataset " + dataset.get("type"));
        }
    }

    private void syncData(WebSocketBasic socket) {
        List<JSONObject> jsonData = urls.values().stream()
                .sorted(TableItem::compareTo)
                .map(TableItem::getJsonObject)
                .toList();
        sendObject(socket, jsonData);
        sendSettings(socket);
        sendSystemInformation(socket);
    }

    private void putData(JSONObject dataset) {
        JSONObject content = (JSONObject) dataset.get("content");
        urls.put(UUID.fromString(content.get("uuid").toString()), new TableItem(content));
        changeObject(content, "state", "Committed");

        String url = content.get("url").toString();
        String targetPath = content.get("target").toString();
        log.debug("Resolving target: " + targetPath.split("/")[0]);
        Target target = targets.get(targetPath.split("/")[0]);
        log.debug("Target resolved!: " + target);

        File outputFolder = new File(target.path(), targetPath.replace(targetPath.split("/")[0] + "/", ""));
        log.debug("Output Folder: " + outputFolder.getAbsolutePath());
        if (!outputFolder.exists()) outputFolder.mkdirs();

        var updateEvent = new DownloadStatusUpdateEvent() {
            @Override
            public void onProgressUpdate(double v) {
                if (v >= 1) {
                    changeObject(content, "state", "Completed");
                } else {
                    changeObject(content, "state", "Downloading - " + Math.round(v * 10000.0) / 100.0 + "%");
                }
            }

            @Override
            public void onInfo(String s) {
            }

            @Override
            public void onWarn(String s) {
            }

            @Override
            public void onError(String s) {
                changeObject(content, "state", "Error: " + s);
            }

            @Override
            public void onLogFile(String s, byte[] bytes) {
            }
        };

        JSONObject options = (JSONObject) content.get("options");

        // Getting the right downloader
        Downloader downloader = DownloaderSelector.selectDownloader(url);

        // Start the download task
        executorHandler.putTask(new ExecutorTask(() -> {
            try {
                File file = downloader.start(url, downloadFolder, updateEvent, options);
                File targetFile = new File(outputFolder, file.getName());
                log.debug("Moving file from " + file.getAbsolutePath() + " to " + targetFile);
                Files.move(file.toPath(), targetFile.toPath());
            } catch (Exception ex) {
                ex.printStackTrace();
                updateEvent.onError(ex.getMessage());
            }
        }));
    }

    private void delete(JSONObject dataset) {
        TableItem toDelete = urls.get(UUID.fromString(dataset.get("uuid").toString()));
        deleteObjectToAll(toDelete);
        urls.remove(UUID.fromString(dataset.get("uuid").toString()));
    }

    private void deleteAll() {
        urls.values().forEach(this::deleteObjectToAll);
        urls.clear();
    }

    private void changeSetting(JSONObject dataset) {
        JSONObject setting = (JSONObject) dataset.get("content");
        String key = setting.get("key").toString();
        String val = setting.get("val").toString();

        switch (key) {
            case "VOE_THREADS" -> voeThreads.setValue(Integer.parseInt(val));
            case "PARALLEL_DOWNLOADS" -> downloadThreads.setValue(Integer.parseInt(val));
            default -> throw new IllegalStateException("Unsupported Setting: " + key);
        }
        broadcastData(null, dataset.toJSONString());
    }

    private void resolveAniworld(WebSocketBasic socket, JSONObject dataset) {
        try {
            int language = Integer.parseInt(dataset.get("language").toString());
            List<String> links = AniWorldParser.getLinks(dataset.get("url").toString(), language);
            JSONObject res = new JSONObject();
            JSONArray ds = new JSONArray();
            ds.addAll(links);
            res.put("content", ds);
            res.put("type", "aniworld-links");
            socket.send(res.toJSONString());
        } catch (Exception ex) {
            JSONObject res = new JSONObject();
            res.put("content", "FAILED");
            res.put("error", ex.getMessage());
            res.put("type", "aniworld-links");
            socket.send(res.toJSONString());
        }
    }

    private void targetSelection(WebSocketBasic socket, JSONObject dataset) {
        String targetPath = dataset.get("selection").toString();
        Target target = targets.get(targetPath.split("/")[0]);

        if (!target.subFolders()) {
            sendWarn(socket, dataset.get("type").toString(), "No sub-folders are configured for " + target.identifier());
            return;
        }

        JSONObject response = new JSONObject();
        response.put("type", "targetSelectionResolved");
        JSONArray array = new JSONArray();
        File subFolder = new File(target.path());
        if (subFolder.listFiles() != null)
            array.addAll(Arrays.stream(subFolder.listFiles()).map(File::getName).toList());
        response.put("subfolders", array);
        socket.send(response.toJSONString());
    }


    private void sendObject(WebSocketBasic socket, JSONObject... objects) {
        sendObject(socket, Arrays.stream(objects).toList());
    }

    private void sendObject(WebSocketBasic socket, List<JSONObject> objects) {
        JSONObject body = new JSONObject();
        JSONArray dataset = new JSONArray();
        for (JSONObject object : objects)
            dataset.add(object);
        body.put("content",dataset);
        body.put("type", "syn");
        socket.send(body.toJSONString());
    }

    private void sendWarn(WebSocketBasic socket, String sourceType, String message) {
        JSONObject body = new JSONObject();
        body.put("type", "warn");
        body.put("sourceType", sourceType);
        body.put("message", message);
        socket.send(body.toJSONString());
    }

    private void sendSettings(WebSocketBasic socket) {
        Settings.SETTING_PROPERTIES.entrySet().stream()
                .filter(entry -> entry.getValue().getMetadata().forClient())
                .forEach(entry -> {
            JSONObject body = new JSONObject();
            JSONObject dataset = new JSONObject();
            dataset.put("key", entry.getKey());
            dataset.put("val", entry.getValue().getValue());
            body.put("content",dataset);
            body.put("type", "setting");
            socket.send(body.toJSONString());
        });
    }

    private void sendObjectToAll(JSONObject... objects) {
        sendObjectToAll(Arrays.stream(objects).toList());
    }

    private void sendObjectToAll(List<JSONObject> objects) {
        for (WebSocketBasic webSocketBasic : new ArrayList<>(clientList)) {
            try {
                sendObject(webSocketBasic, objects);
            } catch (Exception ex) {
                ex.printStackTrace();
                webSocketBasic.close();
                clientList.remove(webSocketBasic);
            }
        }
    }

    private void deleteObjectToAll(TableItem object) {
        JSONObject body = new JSONObject();
        body.put("type", "del");
        body.put("uuid", object.getUuid().toString());
        broadcastData(null, body.toJSONString());
    }

    private void changeObject(JSONObject object, Object key, Object value) {
        object.put(key, value);
        object.put("modified", System.currentTimeMillis());
        sendObjectToAll(object);
    }

    private void broadcastData(WebSocketBasic source, String data) {
        for (WebSocketBasic webSocketBasic : new ArrayList<>(clientList)) {
            if (webSocketBasic != null && webSocketBasic == source) continue;
            try {
                webSocketBasic.send(data);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
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
            if (isDockerEnvironment) {
                dockerMemoryLimit = Long.parseLong(new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.max").start().getInputStream())).trim());
                dockerMemoryUsage = Long.parseLong(new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.current").start().getInputStream())).trim());
            }
            if (target == null) {
                broadcastData(null, formatSystemInfo().toJSONString());
            } else {
                target.send(formatSystemInfo().toJSONString());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private JSONObject formatSystemInfo() {
        JSONObject response = new JSONObject();
        response.put("type", "systemInfo");
        response.put("heap", String.format("Java Runtime: %s/%s/%s"
                , StaticUtils.toHumanReadableFileSize(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
                , StaticUtils.toHumanReadableFileSize(Runtime.getRuntime().totalMemory())
                , StaticUtils.toHumanReadableFileSize(Runtime.getRuntime().maxMemory())
        ));
        if (isDockerEnvironment) {
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
