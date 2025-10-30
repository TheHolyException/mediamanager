package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.handler.AutoLoaderHandler;
import de.theholyexception.mediamanager.handler.DefaultHandler;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.util.*;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.downloaders.DownloadStatusUpdateEvent;
import me.kaigermany.downloaders.Downloader;
import me.kaigermany.downloaders.DownloaderSelector;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static de.theholyexception.mediamanager.webserver.WebSocketUtils.changeObject;

@Slf4j
public class DownloadTask implements Comparable<DownloadTask> {

    private static final AtomicInteger counter = new AtomicInteger(0);
    private static File debugLogFolder = new File("./debug-logs");
    private static DefaultHandler defaultHandler;
    private static AutoLoaderHandler autoLoaderHandler;
    @Getter
    private static ExecutorHandler downloadHandler;
    private static ExecutorHandler titleResolveHandler;


    private static boolean initialized = false;

    private static File outputTempFolder;
    private static boolean useDirectMemory;

    private static boolean untrustedCertificates;

    private static boolean enableValidator;
    private static final List<Target> validatorTargets = new ArrayList<>();
    private static double validatorLengthThreshold;

    private static boolean enableDebugFileLogging = false;

    static {
        if (!debugLogFolder.exists()) {
            debugLogFolder.mkdirs();
        }
    }

    public static void initialize(TomlParseResult config) {
        defaultHandler = MediaManager.getInstance().getDependencyInjector().resolve(DefaultHandler.class);
        autoLoaderHandler = MediaManager.getInstance().getDependencyInjector().resolve(AutoLoaderHandler.class);

        downloadHandler = new ExecutorHandler(Executors.newFixedThreadPool(5));
        downloadHandler.setThreadNameFactory(cnt -> "DownloadThread-" + cnt);
        titleResolveHandler = new ExecutorHandler(Executors.newFixedThreadPool(5));
        titleResolveHandler.setThreadNameFactory(cnt -> "TitleResolver-" + cnt);

        if (defaultHandler == null || autoLoaderHandler == null)
            throw new IllegalStateException("DefaultHandler or AutoLoaderHandler is not initialized");

        outputTempFolder = new File(config.getString("downloader.tmpDownloadFolder", () -> "./tmp"));
        if (!outputTempFolder.exists() && !outputTempFolder.mkdirs())
            log.error("Failed to create tmp download folder");

        useDirectMemory = config.getBoolean("downloader.useDirectMemory", () -> false);

        untrustedCertificates = config.getBoolean("downloader.untrustedCertificates", () -> false);

        enableValidator = config.getBoolean("validator.enabled", () -> false);
        if (enableValidator) {
            validatorLengthThreshold = Integer.parseInt(config.getString("validator.videoLengthThreshold", () -> "50%").replace("%", ""))/100d;
            String targetsCSV = config.getString("validator.targets", () -> "");
            String[] virtualTargets = targetsCSV.split(",");
            for (String virtualTarget : virtualTargets) {
                validatorTargets.add(defaultHandler.getTargets().get(virtualTarget));
                log.info("Enabled validating for " + virtualTarget);
            }
        }
        enableDebugFileLogging = config.getBoolean("general.logDebugDownloaderFiles", () -> false);

        initialized = true;
    }

    @Getter
    private final String url;
    @Getter
    private final UUID uuid;
    @Getter
    private final JSONObjectContainer jsonObject;
    @Getter
    private ExecutorTask downloadExecutorTask;
    @Getter
    private final Downloader downloader;
    @Setter
    private boolean isDeleted = false;
    @Getter
    private boolean isRunning = false;
    @Getter
    private boolean isFailed = false;
    @Getter
    private boolean validationError = false;
    private final int sortIndex = counter.getAndIncrement();
    @Getter
    private long lastUpdate;
    @Setter
    @Getter
    private long downloadStartTime = 0;
    @Setter
    @Getter 
    private double lastProgress = 0.0;
    private final JSONObjectContainer autoloaderData;
    private boolean skipValidation = false;
    private Target target;

    public DownloadTask(JSONObjectContainer content) {
        if (!initialized)
            throw new IllegalStateException("DownloadTask is not initialized");

        jsonObject = content;
        setState(content.get("state", String.class));
        url = content.get("url", String.class);
        uuid = UUID.fromString(content.get("uuid", String.class));
        lastUpdate = System.currentTimeMillis();

        if (url == null || url.isEmpty())
            throw new IllegalArgumentException("URL is null or empty");

        WebSocketUtils.changeObject(content, "state", "Committed", "sortIndex", sortIndex);

        autoloaderData = content.getObjectContainer("autoloaderData");
        JSONObject options = content.getObjectContainer("options").getRaw();
        options.put("useDirectMemory", useDirectMemory+"");
        if (untrustedCertificates)
            options.put("disableCertificateCheck", "true");
        skipValidation = Boolean.parseBoolean((String)options.get("skipValidation"));

        File downloadTempFolder = new File(outputTempFolder, UUID.randomUUID().toString());
        if (!downloadTempFolder.mkdirs() || !downloadTempFolder.exists())
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to create temp folder"));

        downloader = DownloaderSelector.selectDownloader(url, downloadTempFolder,
            createDownloadStatusUpdateEvent(), options);

        resolveTarget();
    }

    public void resolveTitle() {
        String title = downloader.resolveTitle();
        changeObject(jsonObject, "title", title);
    }

    public void start(int threads) {
        titleResolveHandler.putTask(() -> {
            if (isDeleted)
                return;
            String title = downloader.resolveTitle();
            if (title != null)
                changeObject(jsonObject, "title", title);
        });

        downloadExecutorTask = new ExecutorTask(() -> download(threads));
        downloadHandler.putTask(downloadExecutorTask);
        isRunning = false;
    }

    public void download(int threads) {
        downloader.setNumThreads(threads);
        downloader.setProxy(ProxyHandler.getNextProxy());
        File outputFolder = resolveOutputFolder();

        if (isDeleted) {
            // TODO Debug logging -> DELETED
            return;
        }

        isRunning = true;

        // TODO Debug logging -> STARTED
        File outputFile = downloader.start();

        if (downloader.isCanceled()) {
            // TODO Debug logging -> CANCELED
            isRunning = false;
            return;
        }

        if (isFailed) {
            // TODO Debug logging -> FAILED
            isRunning = false;
            log.warn("Download failed for {}", this);
            return;
        }

        if (!validate(outputFile))
            return;

        setState("Completed");
        // TODO Debug logging -> DONE

        File targetFile = new File(outputFolder, outputFile.getName());
        log.debug("Moving file from " + outputFile.getAbsolutePath() + " to " + targetFile);
        try {
            Files.move(outputFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Failed to move video file", ex);
        }
        // TODO Debug logging -> MOVED
        handleAutoloader();
    }

    private boolean validate(File outputFile) {
        try  {
            ValidatorResponse response = validateVideoFile(outputFile);
            if (response != ValidatorResponse.VALID) {
                validationError = true;
                log.warn("Validation Error: {}", response.getDescription());
                setState("Validation Error: " + response.getDescription());
                // TODO Debug logging -> VALIDATION ERROR
                return false;
            }
        } catch (IOException ex) {
            log.error("Failed to validate video file", ex);
        }
        return true;
    }

    private ValidatorResponse validateVideoFile(File file) throws IOException {
        if (skipValidation)
            return ValidatorResponse.VALID;

        // Resolve target
        if (target == null) {
            log.error("Failed to resolve target in experimental Validator, skipping validator");
            return ValidatorResponse.VALID;
        }

        // Target is not relevant
        if (!validatorTargets.contains(target))
            return ValidatorResponse.VALID;

        // Return valid when the file is not mp4
        // Currently the validation only supports mp4 files
        if (!file.getName().endsWith(".mp4"))
            return ValidatorResponse.VALID;

        // Return valid when no other files are found
        File[] files = file.getParentFile().listFiles();
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
                if ((Math.max(avg,fileLength)/Math.min(avg,fileLength))-1 < validatorLengthThreshold) {
                    totalLength += fileLength;
                } else
					log.warn("Video length is not consistent for {} ignoring it for validation.", f.getName());
            }
            cnt++;
        }

        double avg = (double)totalLength/(double)cnt;
        if ((Math.max(avg,videoLength)/Math.min(avg,videoLength))-1 < validatorLengthThreshold) {
            return ValidatorResponse.VIDEO_LENGTH;
        }

        return ValidatorResponse.VALID;
    }

    private void handleAutoloader() {
        if (autoloaderData != null) {
            int animeId = autoloaderData.get("animeId", Integer.class);
            Anime anime = autoLoaderHandler.getAnimeByID(animeId);
            anime.scanDirectoryForExistingEpisodes();
            WebSocketUtils.sendAutoLoaderItem(null, anime);
        }
    }

    /*
        a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object
     */
    @Override
    public int compareTo(DownloadTask o) {
        Long l1 = (long) sortIndex;
        Long l2 = (long) o.sortIndex;
        return l1.compareTo(l2);
    }

    public void update() {
        lastUpdate = System.currentTimeMillis();
    }

    private DownloadStatusUpdateEvent createDownloadStatusUpdateEvent() {
        return new DownloadStatusUpdateEvent() {
            @Override
            public void onProgressUpdate(double v) {
                update();
                if (isDeleted)
                    return;
                if (v >= 1) {
                    setState("Completed");
                } else {
                    long currentTime = System.currentTimeMillis();

                    // Initialize download start time on first progress update
                    if (downloadStartTime == 0) {
                        downloadStartTime = currentTime;
                        lastProgress = v;
                    }

                    String progressText = getProgressText(v, currentTime);

                    setState(progressText);
                    lastProgress = v;
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
                if (isRunning && !isDeleted) {
                    String line = s.split("\n")[0];
                    if (line.length() > 50)
                        s = s.substring(0, 50) + "\n" + s.substring(50);
                    changeObject(jsonObject, "state", "Error: " + s);
                    update();
                }
            }

            @Override
            public void onLogFile(String fileName, byte[] bytes) {
                if (enableDebugFileLogging) {
                    try (FileOutputStream fos = new FileOutputStream(new File(debugLogFolder, fileName))) {
                        fos.write(bytes);
                    } catch (IOException e) {
                        log.error("Failed to write log file", e);
                    }
                }
            }

            @Override
            public void onException(Throwable error) {
                update();
                if (log.isDebugEnabled()) {
                    log.debug("Download failed!", error);
                }
                if (isRunning && !isDeleted) {
                    isFailed = true;
                    changeObject(jsonObject, "state", "Error: " + error.getMessage());
                }
            }
        };
    }

    @NotNull
    private String getProgressText(double v, long currentTime) {
        String progressText = "Downloading - " + Math.round(v * 10000.0) / 100.0 + "%";

        // Calculate ETA if we have meaningful progress (>5%) and some time has passed
        if (v > 0.05 && currentTime - downloadStartTime > 10000) { // Wait at least 10 seconds
            long elapsedTime = currentTime - downloadStartTime;

            double downloadRate = v / (elapsedTime / 1000.0); // progress per second
            double remainingProgress = 1.0 - v;
            long etaSeconds = Math.round(remainingProgress / downloadRate);

            if (etaSeconds > 0 && etaSeconds < 86400) { // Cap at 24 hours
                String etaText = formatETA(etaSeconds);
                progressText += " (ETA: " + etaText + ")";
            }
        }
        return progressText;
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

    private void setState(String state) {
        changeObject(jsonObject, "state", state);
        update();
    }

    /**
     * Resolves and validates the output folder for a download based on the provided content.
     * Creates the directory structure if it doesn't exist.
     *
     * @return The resolved output folder as a File object
     * @throws WebSocketResponseException if the target path is invalid or inaccessible
     */
    private File resolveOutputFolder() {
        String targetPath = jsonObject.get("target", String.class);

        if (target == null || target.path() == null)
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid Target " + jsonObject.get("target", String.class)));

        String subDirectory = targetPath.replace(targetPath.split("/")[0] + "/", "");
        if (target.subFolders() && subDirectory.isEmpty()) {
            String aniworldUrl = jsonObject.get("aniworld-url", String.class);
            if (aniworldUrl != null && !aniworldUrl.isEmpty()) {
                subDirectory = AniworldHelper.getAnimeTitle(aniworldUrl);
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Subdirectory not specified, using " + subDirectory + " instead"));
            } else
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Subdirectory not specified"));
        }

        File outputFolder;
        if (target.subFolders() && subDirectory != null)
            outputFolder = new File(target.path(), Utils.escape(subDirectory));
        else
            outputFolder = new File(target.path());

		log.debug("Output Folder: {}", outputFolder.getAbsolutePath());
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
			log.error("Failed to create output folder {}", outputFolder.getAbsolutePath());
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Failed to create output folder " + outputFolder.getAbsolutePath()));
        }
        return outputFolder;
    }

    public void resolveTarget() {
        String targetPath = jsonObject.get("target", String.class);
		log.debug("Resolving target: {}", targetPath.split("/")[0]);
        target = defaultHandler.getTargets().get(targetPath.split("/")[0]);
        if (target == null) {
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid Target " + jsonObject.get("target", String.class)));
        }
		log.debug("Target resolved!: {}", target);
    }
}
