package de.theholyexception.mediamanager.logging;

import lombok.Getter;
import lombok.Setter;
import org.tomlj.TomlParseResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public abstract class DownloadLogger {

	protected static Path DEBUG_LOG_FOLDER;
	protected static Path DOWNLOADS_LOG_FOLDER;
	protected static final DateTimeFormatter LOG_FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	protected String outputFilename;

	public static void initialize(TomlParseResult config) {
		DEBUG_LOG_FOLDER = Paths.get(config.getString("logging.debugLogFolder", () -> "debug-logs"));
		DOWNLOADS_LOG_FOLDER = Paths.get(config.getString("logging.downloadLogFolder", () -> "debug-logs/downloads"));
	}

	static {
		try {
			Files.createDirectories(DEBUG_LOG_FOLDER);
			DOWNLOADS_LOG_FOLDER = DEBUG_LOG_FOLDER.resolve("downloads");
			Files.createDirectories(DOWNLOADS_LOG_FOLDER);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to create debug log directories", ex);
		}
	}

	public DownloadLogger(String name) {
		this.outputFilename = name;
	}

	@Getter
	@Setter
	private LoggerCallback loggerCallback;

	@Getter
	private boolean closed;

	public void write(Level level, String message) {
		if (closed)
			throw new IllegalStateException("Logger is closed, cannot write log file");
		if (loggerCallback != null) {
			if (level.equals(Level.SEVERE)) {
				loggerCallback.onError(message);
			} else if (level.equals(Level.INFO)) {
				loggerCallback.onInfo(message);
			} else if (level.equals(Level.WARNING)) {
				loggerCallback.onWarn(message);
			}
		}
	}

	public void changeOutputFilename(String newName) {
		this.outputFilename = newName;
	}

	public abstract String getContent();

	public void close() {
		closed = true;
	}

}
