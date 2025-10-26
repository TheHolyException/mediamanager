package de.theholyexception.mediamanager.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * A thread-safe file logging utility that provides simple file-based logging capabilities.
 * Each logger instance writes to its own log file in the './logs' directory.
 * Log entries include timestamp and thread information.
 */
@Slf4j
public class FileLogger {

	private static final Map<String, FileLogger> fileLoggerCache = new HashMap<>();

	/**
	 * Retrieves or creates a FileLogger instance with the specified name.
	 * If a logger with the given name already exists, returns the existing instance.
	 *
	 * @param name The name of the logger, which will be used to create the log file
	 * @return A FileLogger instance for the specified name
	 * @throws RuntimeException if the log file cannot be created
	 */
	public static synchronized FileLogger getLogger(String name) {
		return fileLoggerCache.computeIfAbsent(name, k -> new FileLogger(name));
	}

	private static final File loggerFolder = new File("./logs");
	private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	@Getter
	private final File file;
	private BufferedOutputStream bos;

	/**
	 * Creates a new FileLogger instance for the specified log file.
	 * The log file will be created in the './logs' directory.
	 *
	 * @param name The name of the log file (without extension)
	 * @throws RuntimeException if the log file cannot be created or accessed
	 */
	public FileLogger(String name) {
		file = new File(loggerFolder, name);
		try {
			if (!file.exists() && !file.createNewFile()) {
				throw new IOException("Failed to create logger file");
			}

			bos = new BufferedOutputStream(new FileOutputStream(file));
		} catch (IOException ex) {
			log.error("Failed to create logger", ex);
		}
	}

	/**
	 * Writes a log message to the log file with timestamp and thread information.
	 * The message will be automatically followed by a newline character.
	 *
	 * @param message The message to log
	 */
	public void log(String message) {
		String time = sdf.format(System.currentTimeMillis());
		String thread = Thread.currentThread().getName();

		StringBuilder builder = new StringBuilder();
		builder.append(time).append("\t[").append(thread).append("]\t").append(message).append("\n");
		try {
			bos.write(builder.toString().getBytes());
			bos.flush();
		} catch (IOException ex) {
			log.error("Failed to log", ex);
		}
	}

}
