package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.handler.AutoLoaderHandler;
import de.theholyexception.mediamanager.handler.DefaultHandler;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.util.MP4Utils;
import de.theholyexception.mediamanager.util.ProxyHandler;
import de.theholyexception.mediamanager.util.Utils;
import de.theholyexception.mediamanager.util.ValidatorResponse;
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static de.theholyexception.mediamanager.webserver.WebSocketUtils.changeObject;

@Slf4j
public class DownloadTask implements Comparable<DownloadTask> {

    private static final SimpleDateFormat LOG_FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
    private static final String PACKET_KEY_STATE = "state";
    private static final AtomicInteger SORT_INDEX_COUNTER = new AtomicInteger(0);
    private static final File DEBUG_LOG_FOLDER = new File("./debug-logs");
    private static final File DOWNLOADS_LOG_FOLDER;

    private static DefaultHandler defaultHandler;
    private static AutoLoaderHandler autoLoaderHandler;

    @Getter
    private static ExecutorHandler downloadHandler;
    @Getter
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
        if (!DEBUG_LOG_FOLDER.exists() && !DEBUG_LOG_FOLDER.mkdirs())
            throw new IllegalStateException("Failed to create debug log folder");

        DOWNLOADS_LOG_FOLDER = new File(DEBUG_LOG_FOLDER, "downloads");
        if (!DOWNLOADS_LOG_FOLDER.exists() && !DOWNLOADS_LOG_FOLDER.mkdirs())
            throw new IllegalStateException("Failed to create download log file");
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
    private final JSONObjectContainer content;
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
    private final int sortIndex = SORT_INDEX_COUNTER.getAndIncrement();
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
    private final DownloadStatusUpdateEvent downloadStatusUpdateEvent;
    @Getter
    private int errorCount;
    @Getter
    private long retryTimestamp;

    @Getter
    private File logFile;
    private BufferedOutputStream logFileFOS;
    private boolean hadServerError = false;
    private boolean hadWarning = false;

    public DownloadTask(JSONObjectContainer content) {
        if (!initialized)
            throw new IllegalStateException("DownloadTask is not initialized");

        this.content = content;
        setState(content.get(PACKET_KEY_STATE, String.class));
        url = content.get("url", String.class);
        uuid = UUID.fromString(content.get("uuid", String.class));
        lastUpdate = System.currentTimeMillis();

        createLogFile();

        if (url.equalsIgnoreCase("warning")) {
            writeLogLine(Level.WARNING, "Test Warning");
        }

        if (url == null || url.isEmpty()) {
            writeLogLine(Level.SEVERE, "URL is null or empty, aborting download!");
            throw new IllegalArgumentException("URL is null or empty, aborting download!");
        }

        WebSocketUtils.changeObject(content, PACKET_KEY_STATE, "Committed", "sortIndex", sortIndex, "hadServerError", false, "hadWarning", false);

        autoloaderData = content.getObjectContainer("autoloaderData");
        JSONObject options = content.getObjectContainer("options").getRaw();
        options.put("useDirectMemory", useDirectMemory+"");
        if (untrustedCertificates)
            options.put("disableCertificateCheck", "true");
        skipValidation = Boolean.parseBoolean((String)options.get("skipValidation"));

        File downloadTempFolder = new File(outputTempFolder, UUID.randomUUID().toString());
        if (!downloadTempFolder.mkdirs() || !downloadTempFolder.exists()) {
            writeLogLine(Level.SEVERE, "Failed to create temp folder, aborting download!");
            throw new IllegalStateException("Failed to create temp folder, aborting download!");
        }

        downloadStatusUpdateEvent = createDownloadStatusUpdateEvent();
        downloader = DownloaderSelector.selectDownloader(url, downloadTempFolder,
            downloadStatusUpdateEvent, options);

        if (!resolveTarget()) {
            writeLogLine(Level.SEVERE, "Failed to resolve target, aborting download!");
            throw new IllegalStateException("Failed to resolve target, aborting download!");
        }
    }

    public void resolveTitle() {
        String title = downloader.resolveTitle();
        if (title == null) {
            writeLogLine(Level.SEVERE, "Failed to resolve title!");
        } else {
            changeObject(content, "title", title);
            updateLogFile(title);
        }
    }

    public void start(int threads) {
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

    public void download(int threads) {
        downloader.setNumThreads(threads);
        downloader.setProxy(ProxyHandler.getNextProxy());
        File outputFolder = resolveOutputFolder();

        if (outputFolder == null) {
            downloadStatusUpdateEvent.onError("Failed to resolve output folder");
            return;
        }

        if (isDeleted) {
            writeLogLine(Level.INFO, "Download is deleted -> ABORT");
            return;
        }

        isRunning = true;

        writeLogLine(Level.INFO, "Download is started");
        File outputFile = null;
        try {
            outputFile = downloader.start();
        } catch (Exception ex) {
            isFailed = true;
            writeLogLine(Level.INFO, "Download failed");
        }

        if (downloader.isCanceled()) {
            writeLogLine(Level.INFO, "Download is canceled");
            isRunning = false;
            return;
        }

        if (isFailed) {
            writeLogLine(Level.WARNING, "Download has failed");
            errorCount ++;
            long delay = calculateRetryDelay();
            long maxDelayMs = getMaxRetryDelayMs();
            
            if (delay < maxDelayMs) {
                retryTimestamp = System.currentTimeMillis() + delay;
                changeObject(content, PACKET_KEY_STATE, "Retry scheduled for:\n" + new SimpleDateFormat("HH:mm:ss").format(new Date(retryTimestamp)));
            } else {
                retryTimestamp = -1;
                errorCount = 0;
                changeObject(content, PACKET_KEY_STATE, "Error: Download failed, automatic retry limit reached!");
            }
            isRunning = false;
            writeLogLine(Level.WARNING, "Download failed for " + this);
            log.warn("Download failed for {}", this);
            return;
        }

        if (!validate(outputFile))
            return;

        onDownloadCompleted(outputFolder, outputFile);
    }

    public void disableRetry() {
        retryTimestamp = -1;
        errorCount = Integer.MAX_VALUE;
        changeObject(content, PACKET_KEY_STATE, "Error: retry disabled");
    }

    private void onDownloadCompleted(File outputFolder, File outputFile) {
        setState("Completed");
        writeLogLine(Level.INFO, "Download is done");

        File targetFile = new File(outputFolder, outputFile.getName());
        writeLogLine(Level.INFO, "Moving file from " + outputFile.getAbsolutePath() + " to " + targetFile);
        try {
            Files.move(outputFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            writeLogLine(Level.SEVERE, "Failed to move video file");
        }
        writeLogLine(Level.INFO, "File moved to target destination");
        handleAutoloader();

        if (hadServerError) {
            log.error("\t");
			log.error("One or multiple errors occurred for the download with the url {}", url);
			log.error("Check the download log file for more information {}", logFile.getAbsolutePath());
            log.error("\t");
        }
    }

    private boolean validate(File outputFile) {
        try  {
            ValidatorResponse response = validateVideoFile(outputFile);
            if (response != ValidatorResponse.VALID) {
                validationError = true;
                writeLogLine(Level.WARNING, "Validation Error: " + response.getDescription());
                setState("Validation Error: " + response.getDescription());
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
                writeLogLine(Level.INFO, "onInfo() \t " + s);
            }

            @Override
            public void onWarn(String s) {
                writeLogLine(Level.WARNING, "onWarn() \t " + s);
            }

            @Override
            public void onError(String s) {
                writeLogLine(Level.SEVERE, "onError() \t " + s);
                if (isRunning && !isDeleted) {
                    String line = s.split("\n")[0];
                    if (line.length() > 50)
                        s = s.substring(0, 50) + "\n" + s.substring(50);
                    changeObject(content, PACKET_KEY_STATE, "Error: " + s);
                    update();
                }
            }

            @Override
            public void onLogFile(String fileName, byte[] bytes) {
                if (enableDebugFileLogging) {
                    try (FileOutputStream fos = new FileOutputStream(new File(DEBUG_LOG_FOLDER, fileName))) {
                        fos.write(bytes);
                    } catch (IOException e) {
                        log.error("Failed to write log file", e);
                    }
                }
            }

            @Override
            public void onException(Throwable error) {
                update();
                writeLogLine(Level.SEVERE, "onException() \t " + Utils.stackTraceToString(error));
                if (isRunning && !isDeleted) {
                    isFailed = true;
                    changeObject(content, PACKET_KEY_STATE, "Error: " + error.getMessage());
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
        changeObject(content, PACKET_KEY_STATE, state);
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
        logFile = new File(DOWNLOADS_LOG_FOLDER, LOG_FILE_DATE_FORMAT.format(new Date())+"-"+Utils.escape(url)+".log");
        try {
            logFileFOS = new BufferedOutputStream(new FileOutputStream(logFile));

        } catch (IOException ex) {
            log.error("Failed to create download log file", ex);
        }
    }

    /**
     * Updates the download log file with the specified title.
     * The log file is created in the './logs/downloads' directory.
     * The log file name is in the format 'yyyy-MM-dd-HH-mm-ss-<title>-<url>.log'.
     * @param title The title to use in the log file name
     */
    private void updateLogFile(String title) {
        if (logFile == null || logFileFOS == null)
            throw new IllegalStateException("Log file is not initialized");

        synchronized (this) {
            try {
                logFileFOS.flush();
                logFileFOS.close();

                String formatedTime = LOG_FILE_DATE_FORMAT.format(new Date());
                String formatedUrl = Utils.escape(url.split("e/")[1]).replace("_","");
                String filename = String.format("%s-%s-%s.log", formatedTime, formatedUrl, title);

                File newFile = new File(DOWNLOADS_LOG_FOLDER, filename);

				log.debug("Updating log file -> {}", newFile.getAbsolutePath());

                Files.move(logFile.toPath(), newFile.toPath());
                logFile = newFile;
                logFileFOS = new BufferedOutputStream(new FileOutputStream(logFile));
            } catch (IOException ex) {
                log.error("Failed to update download log file", ex);
            }
        }
    }

    private void writeLogLine(Level level, String message)  {
        if (logFile == null || logFileFOS == null)
            throw new IllegalStateException("Log file is not initialized");

        boolean statusChanged = false;
        
        if (!hadServerError && level == Level.SEVERE) {
            hadServerError = true;
            statusChanged = true;
        }

        if (!hadWarning && level == Level.WARNING) {
            hadWarning = true;
            statusChanged = true;
        }

        // Update WebSocket content if status changed
        if (statusChanged) {
            WebSocketUtils.changeObject(content, "hadServerError", hadServerError, "hadWarning", hadWarning);
        }

        synchronized (this) {
            try {
                String logMessage = LOG_FILE_DATE_FORMAT.format(new Date());
                logMessage += " " + level.toString();
                logMessage += " \t" + message;
                logMessage += "\n";

                logFileFOS.write(logMessage.getBytes());
                logFileFOS.flush();
            } catch (IOException ex) {
                log.error("Failed to write log file", ex);
            }
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

    /**
     * Calculates the retry delay using the configurable formula from system settings.
     * The formula can use the variable 'errorCount' which represents the current error count.
     * @return The calculated delay in milliseconds
     */
    private long calculateRetryDelay() {
        try {
            String formula = defaultHandler.getRetryDelayFormula();
            return evaluateFormula(formula, errorCount);
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
    
    /**
     * Simple expression evaluator for mathematical formulas containing the errorCount variable.
     * Supports basic arithmetic operations: +, -, *, /, parentheses
     * @param formula The mathematical formula as a string
     * @param errorCount The current error count to substitute in the formula
     * @return The evaluated result
     */
    private long evaluateFormula(String formula, int errorCount) {
        if (formula == null || formula.trim().isEmpty()) {
            throw new IllegalArgumentException("Formula cannot be null or empty");
        }
        
        // Replace the errorCount variable with its actual value
        String expression = formula.replace("errorCount", String.valueOf(errorCount));
        
        // Basic validation - ensure only allowed characters
        if (!expression.matches("[0-9+\\-*/()\\s.]+")) {
            throw new IllegalArgumentException("Formula contains invalid characters");
        }
        
        try {
            // Use JavaScript engine for expression evaluation
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("nashorn");
            
            if (engine == null) {
                throw new RuntimeException("Nashorn JavaScript engine not available");
            }
            
            Object result = engine.eval(expression);
            
            if (result instanceof Number number) {
                // Convert seconds to milliseconds for internal use
                return number.longValue() * 1000L;
            } else {
                throw new IllegalArgumentException("Formula did not evaluate to a number: " + result);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to evaluate formula: " + expression, e);
        }
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
