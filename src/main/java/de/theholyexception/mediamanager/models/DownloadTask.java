package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.MediaManagerConfig;
import de.theholyexception.mediamanager.handler.AutoLoaderHandler;
import de.theholyexception.mediamanager.handler.DefaultHandler;
import de.theholyexception.mediamanager.logging.DownloadLogger;
import de.theholyexception.mediamanager.logging.DownloadLoggerFactory;
import de.theholyexception.mediamanager.logging.LoggerCallback;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.util.*;
import de.theholyexception.mediamanager.util.WebSocketResponse;
import de.theholyexception.mediamanager.util.WebSocketUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.downloaders.DownloadStatusUpdateEvent;
import me.kaigermany.downloaders.Downloader;
import me.kaigermany.downloaders.DownloaderSelector;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static de.theholyexception.mediamanager.util.WebSocketUtils.changeObject;

@Slf4j
public class DownloadTask implements Comparable<DownloadTask> {

    // region static-code
    private static final SimpleDateFormat LOG_FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private static final String PACKET_KEY_STATE = "state";
    private static final AtomicInteger SORT_INDEX_COUNTER = new AtomicInteger(0);

    private static DefaultHandler defaultHandler;
    private static AutoLoaderHandler autoLoaderHandler;

    @Getter
    private static ExecutorHandler downloadHandler;
    @Getter
    private static ExecutorHandler titleResolveHandler;

    private static boolean initialized = false;

    private static File outputTempFolder;

    private static final List<Target> validatorTargets = new ArrayList<>();
    private static double validatorLengthThreshold;


    public static void initialize() {
        defaultHandler = MediaManager.getInstance().getDependencyInjector().resolve(DefaultHandler.class);
        autoLoaderHandler = MediaManager.getInstance().getDependencyInjector().resolve(AutoLoaderHandler.class);

        downloadHandler = new ExecutorHandler(Executors.newFixedThreadPool(5));
        downloadHandler.setThreadNameFactory(cnt -> "DownloadThread-" + cnt);
        titleResolveHandler = new ExecutorHandler(Executors.newFixedThreadPool(5));
        titleResolveHandler.setThreadNameFactory(cnt -> "TitleResolver-" + cnt);

        if (defaultHandler == null || autoLoaderHandler == null)
            throw new IllegalStateException("DefaultHandler or AutoLoaderHandler is not initialized");

        outputTempFolder = new File(MediaManagerConfig.Downloader.tmpDownloadFolder);
        if (!outputTempFolder.exists() && !outputTempFolder.mkdirs())
            log.error("Failed to create tmp download folder");

        if (MediaManagerConfig.Validator.enabled) {
            validatorLengthThreshold = MediaManagerConfig.Validator.videoLengthThreshold;
            String targetsCSV = MediaManagerConfig.Validator.targets;
            String[] virtualTargets = targetsCSV.split(",");
            for (String virtualTarget : virtualTargets) {
                validatorTargets.add(defaultHandler.getTargets().get(virtualTarget));
				log.info("Enabled validating for {}", virtualTarget);
            }
        }

        initialized = true;
    }
    // endregion static-code

    //region properties
    @Getter
    private final String url;
    @Getter
    private final UUID uuid;
    @Getter
    private final JSONObjectContainer content;
    private JSONObject options;
    private JSONObjectContainer autoloaderData;
    @Getter
    private ExecutorTask downloadExecutorTask;
    @Getter
    private Downloader downloader;
    @Setter
    private boolean isDeleted = false;
    @Getter
    private boolean isRunning = false;
    @Getter
    private boolean isFailed = false;
    private String lastFailedCause;
    private final int sortIndex = SORT_INDEX_COUNTER.getAndIncrement();
    @Getter
    private long lastUpdate;
    @Setter
    @Getter
    private long downloadStartTime = 0;
    @Setter
    @Getter 
    private double lastProgress = 0.0;
    private boolean skipValidation;
    private Target target;
    private final DownloadStatusUpdateEvent downloadStatusUpdateEvent;
    @Getter
    private int errorCount;
    @Getter
    private long retryTimestamp;

    @Getter
    private DownloadLogger outputLog;
    @Getter
    private DownloadLogger detailedLog;

    private boolean hadSeverError = false;
    private boolean hadWarning = false;
    //endregion properties

    public DownloadTask(JSONObjectContainer content) {
        if (!initialized)
            throw new IllegalStateException("DownloadTask is not initialized");

        this.content = content;
        updateContent(content);
        uuid = UUID.fromString(content.get("uuid", String.class));
        url = content.get("url", String.class);
        setState(content.get(PACKET_KEY_STATE, String.class));
        lastUpdate = System.currentTimeMillis();

        createLogFile();

        if (url == null || url.isEmpty()) {
            outputLog.write(Level.SEVERE, "URL is null or empty, aborting download!");
            throw new IllegalArgumentException("URL is null or empty, aborting download!");
        }

        WebSocketUtils.changeObject(this, PACKET_KEY_STATE, "Committed", "sortIndex", sortIndex, "hadServerError", false, "hadWarning", false);

        autoloaderData = content.getObjectContainer("autoloaderData");
        options = content.getObjectContainer("options").getRaw();
        options.put("useDirectMemory", MediaManagerConfig.Downloader.useDirectMemory+"");
        if (MediaManagerConfig.Downloader.untrustedCertificates)
            options.put("disableCertificateCheck", "true");
        skipValidation = Boolean.parseBoolean((String)options.get("skipValidation"));

        downloadStatusUpdateEvent = createDownloadStatusUpdateEvent();

        if (!resolveTarget()) {
            outputLog.write(Level.SEVERE, "Failed to resolve target, aborting download!");
            throw new IllegalStateException("Failed to resolve target, aborting download!");
        }
    }

    public void updateContent(JSONObjectContainer content) throws WebResponseException {
        String localUUID = content.get("uuid", String.class);
        if (uuid != null && UUID.fromString(localUUID) != uuid) {
            throw new WebResponseException(WebSocketResponse.ERROR.setMessage("UUID mismatch, please delete and reschedule again!"));
        }
        autoloaderData = content.getObjectContainer("autoloaderData");
        options = content.getObjectContainer("options").getRaw();
        skipValidation = Boolean.parseBoolean((String)options.get("skipValidation"));
    }

    public void start(int threads) {
        getDownloaderInstance();
        if (isRunning) {
            log.error("Download already running!");
            return;
        }
        retryTimestamp = 0;
        titleResolveHandler.putTask(() -> {
            if (isDeleted)
                return;
            resolveTitle();
        });

        downloadExecutorTask = new ExecutorTask(() -> download(threads));
        downloadHandler.putTask(downloadExecutorTask);
        isRunning = false;
    }

    private void getDownloaderInstance() {
        File downloadTempFolder = new File(outputTempFolder, UUID.randomUUID().toString());
        if (!downloadTempFolder.mkdirs() || !downloadTempFolder.exists()) {
            outputLog.write(Level.SEVERE, "Failed to create temp folder, aborting download!");
            throw new IllegalStateException("Failed to create temp folder, aborting download!");
        }
        downloader = DownloaderSelector.selectDownloader(url, downloadTempFolder,
            downloadStatusUpdateEvent, options);
    }

    public void resolveTitle() {
        String title = downloader.resolveTitle();
        if (title == null) {
            outputLog.write(Level.SEVERE, "Failed to resolve title!");
        } else {
            changeObject(this, "title", title);
            updateLogFile(title);
        }
    }

    public void download(int threads) {
        if (isRunning) {
            throw new IllegalStateException("Task is already running");
        }
        downloader.setNumThreads(threads);
        reset();

        Proxy proxy = ProxyHandler.getNextProxy();
        outputLog.write(Level.INFO, "Using proxy: " + proxy);
        downloader.setProxy(proxy);

        File outputFolder = resolveOutputFolder();

        if (outputFolder == null) {
            downloadStatusUpdateEvent.onError("Failed to resolve output folder");
            isFailed = true;
            lastFailedCause = "NO_OUTPUT_FOLDER";
            changeObject(this, PACKET_KEY_STATE, "Error: Failed to resolve output folder");
            onDownloadFailed();
            return;
        }

        if (isDeleted) {
            outputLog.write(Level.INFO, "Download is deleted -> ABORT");
            return;
        }

        isRunning = true;

        outputLog.write(Level.INFO, "Download is started");
        File outputFile;
        try {
            outputFile = downloader.start();
        } catch (Exception ex) {
            isFailed = true;
            lastFailedCause = Utils.getStackTraceAsString(ex);
            outputLog.write(Level.SEVERE, ex.getMessage());
            onDownloadFailed();
            return;
        }

        if (downloader.isCanceled()) {
            outputLog.write(Level.INFO, "Download is canceled");
            isRunning = false;
            return;
        }

        if (isFailed) {
            onDownloadFailed();
            return;
        }

        if (!validate(outputFile))
            return;

        onDownloadCompleted(outputFolder, outputFile);
    }

    public void disableRetry() {
        retryTimestamp = -1;
        errorCount = Integer.MAX_VALUE;
        changeObject(this, PACKET_KEY_STATE, "Error: retry disabled");
    }

    private void reset() {
        isFailed = false;
        isRunning = false;
        isDeleted = false;
        hadSeverError = false;
        hadWarning = false;
    }

    private void onDownloadFailed() {
        outputLog.write(Level.WARNING, "Download has failed, last cause:");
        outputLog.write(Level.WARNING, lastFailedCause);
        errorCount ++;
        long delay = calculateRetryDelay();
        long maxDelayMs = getMaxRetryDelayMs();

        if (delay < maxDelayMs) {
            retryTimestamp = System.currentTimeMillis() + delay;
            changeObject(this, PACKET_KEY_STATE, "Retry scheduled for:\n" + new SimpleDateFormat("HH:mm:ss").format(new Date(retryTimestamp)));
        } else {
            retryTimestamp = -1;
            errorCount = 0;
            changeObject(this, PACKET_KEY_STATE, "Error: Download failed, automatic retry limit reached!");
        }
        isRunning = false;
        outputLog.write(Level.WARNING, "Download failed for " + this);
        log.warn("Download failed for {}", this);
    }

    private void onDownloadCompleted(File outputFolder, File outputFile) {
        setState("Completed");
        outputLog.write(Level.INFO, "Download is done");

        File targetFile = new File(outputFolder, outputFile.getName());
        outputLog.write(Level.INFO, "Moving file from " + outputFile.getAbsolutePath() + " to " + targetFile);
        try {
            Files.move(outputFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            outputLog.write(Level.SEVERE, "Failed to move video file");
        }
        outputLog.write(Level.INFO, "File moved to target destination");
        handleAutoloader();

        if (hadSeverError) {
            log.error("\t");
			log.error("One or multiple errors occurred for the download with the url {}", url);
			log.error("Check the download log file for more information");
            log.error("\t");
        }
    }

    private boolean validate(File outputFile) {
        try  {
            ValidatorResponse response = validateVideoFile(outputFile);
            if (response != ValidatorResponse.VALID) {
                outputLog.write(Level.WARNING, "Validation Error: " + response.getDescription());
                setState("Validation Error: " + response.getDescription());
                return false;
            }
        } catch (IOException ex) {
            log.error("Failed to validate video file", ex);
        }
        return true;
    }

    private ValidatorResponse validateVideoFile(File file) throws IOException {
        if (1 == 1)
            return ValidatorResponse.VALID;

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
                outputLog.write(Level.INFO, "onInfo() \t " + s);
            }

            @Override
            public void onWarn(String s) {
                outputLog.write(Level.WARNING, "onWarn() \t " + s);
            }

            @Override
            public void onError(String s) {
                outputLog.write(Level.SEVERE, "onError() \t " + s);
                if (isRunning && !isDeleted) {
                    String line = s.split("\n")[0];
                    if (line.length() > 50)
                        s = s.substring(0, 50) + "\n" + s.substring(50);
                    changeObject(DownloadTask.this, PACKET_KEY_STATE, "Error: " + s);
                    update();
                }
            }

            @Override
            public void onLogFile(String fileName, byte[] bytes) {
                detailedLog.write(Level.INFO, "onLogFile("+fileName+") \t" + new String(bytes));
            }

            @Override
            public void onException(Throwable error) {
                update();
                outputLog.write(Level.SEVERE, "onException() \t " + Utils.stackTraceToString(error));
                if (isRunning && !isDeleted) {
                    isFailed = true;
                    lastFailedCause = Utils.getStackTraceAsString(error);
                    changeObject(DownloadTask.this, PACKET_KEY_STATE, "Error: " + error.getMessage());
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
                progressText += "\n(ETA: " + etaText + ")";
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
        changeObject(this, PACKET_KEY_STATE, state);
        update();
    }

    private File resolveOutputFolder() {
        String targetPath = content.get("target", String.class);

        if (target == null || target.path() == null) {
            downloadStatusUpdateEvent.onError("Invalid Target " + content.get("target", String.class));
            return null;
        }

        String subDirectory = targetPath.replace(targetPath.split("/")[0] + "/", "");
        if (target.subFolders() && subDirectory.isEmpty()) {
            String aniworldUrl = content.get("aniworld-url", String.class);
            if (aniworldUrl != null && !aniworldUrl.isEmpty()) {
                subDirectory = AniworldHelper.getSubdirectoryFromURL(aniworldUrl);
                downloadStatusUpdateEvent.onInfo("Subdirectory not specified, using " + subDirectory + " instead");
            } else
                downloadStatusUpdateEvent.onError("Subdirectory not specified");
        }

        File outputFolder;
        if (target.subFolders() && subDirectory != null)
            outputFolder = new File(target.path(), Utils.escape(subDirectory));
        else
            outputFolder = new File(target.path());

		log.debug("Output Folder: {}", outputFolder.getAbsolutePath());
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            downloadStatusUpdateEvent.onError("Failed to create output folder " + outputFolder.getAbsolutePath());
            return null;
        }
        return outputFolder;
    }

    public boolean resolveTarget() {
        String targetPath = content.get("target", String.class);
		log.debug("Resolving target: {}", targetPath.split("/")[0]);
        target = defaultHandler.getTargets().get(targetPath.split("/")[0]);
        if (target == null)
            return false;
		log.debug("Target resolved!: {}", target);
        return true;
    }

    private void createLogFile() {
        outputLog = DownloadLoggerFactory.getLogger(LOG_FILE_DATE_FORMAT.format(new Date())+"-"+Utils.escape(url)+".log");
        detailedLog = DownloadLoggerFactory.getLogger(LOG_FILE_DATE_FORMAT.format(new Date())+"-"+Utils.escape(url)+".detailed.log");

        LoggerCallback callback = new LoggerCallback() {
            @Override
            public void onWarn(String message) {
                if (!hadWarning) {
                    hadWarning = true;
                    WebSocketUtils.changeObject(DownloadTask.this, "hadWarning", hadWarning);
                }
            }

            @Override
            public void onError(String message) {
                if (!hadSeverError) {
                    hadSeverError = true;
                    WebSocketUtils.changeObject(DownloadTask.this, "hadServerError", hadSeverError);
                }
            }
        };

        outputLog.setLoggerCallback(callback);
        detailedLog.setLoggerCallback(callback);
    }

    /**
     * Updates the download log file with the specified title.
     */
    private void updateLogFile(String title) {
        String formatedTime = LOG_FILE_DATE_FORMAT.format(new Date());
        String formatedUrl = Utils.escape(url.split("e/")[1]).replace("_","");
        String filename = String.format("%s-%s-%s.log", formatedTime, formatedUrl, title);
        String filenameDetailed = String.format("%s-%s-%s.detailed.log", formatedTime, formatedUrl, title);
        outputLog.changeOutputFilename(filename);
        detailedLog.changeOutputFilename(filenameDetailed);
    }

    public void closeAndCompressLog() {
        outputLog.close();
        detailedLog.close();
    }


    public void close() {
        if (isRunning) {
            log.error("Cannot close download task, it is still running.");
            return;
        }
        closeAndCompressLog();
        downloader = null;
    }

    /**
     * Calculates the retry delay using the configurable formula from system settings.
     * The formula can use the variable 'errorCount' which represents the current error count.
     * @return The calculated delay in milliseconds
     */
    private long calculateRetryDelay() {
        try {
            String formula = defaultHandler.getRetryDelayFormula();
            return Utils.evaluateFormula(formula, errorCount);
        } catch (Exception e) {
            log.warn("Failed to evaluate retry delay formula, using default calculation", e);
            // Fallback to original calculation
            return errorCount * errorCount * 600L * 1000L;
        }
    }
    
    /**
     * Gets the maximum retry delay in milliseconds from system settings.
     * @return The maximum retry delay in milliseconds
     */
    private long getMaxRetryDelayMs() {
        try {
            int maxDelayMinutes = defaultHandler.getMaxRetryDelayMinutes();
            return maxDelayMinutes * 60L * 1000L; // Convert minutes to milliseconds
        } catch (Exception e) {
            log.warn("Failed to get max retry delay setting, using default value", e);
            return 86400000L; // 24 hours as fallback
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownloadTask that = (DownloadTask) o;
        return sortIndex == that.sortIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sortIndex);
    }

    @Override
    public int compareTo(DownloadTask o) {
        Long l1 = (long) sortIndex;
        Long l2 = (long) o.sortIndex;
        return l1.compareTo(l2);
    }

    @Override
    public String toString() {
        return "DownloadTask{" +
            "url='" + url + '\'' +
            ", uuid=" + uuid +
            ", target=" + target +
            '}';
    }
}
