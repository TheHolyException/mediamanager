package de.theholyexception.mediamanager.logging;

import de.theholyexception.mediamanager.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.logging.Level;

@Slf4j
public class RamLogger extends DownloadLogger {

	private StringBuilder logs;

	public RamLogger(String name) {
		super(name);
		this.logs = new StringBuilder();
	}

	@Override
	public synchronized void write(Level level, String message) {
		super.write(level, message);
		if (logs == null) {
			log.debug("Cannot log message, logs object is null");
			return;
		}
		logs.append(String.format("%s %s \t%s%n", LOG_FILE_DATE_FORMAT.format(LocalDateTime.now()), level, message));
	}

	@Override
	public synchronized String getContent() {
		if (logs == null) {
			log.debug("Cannot get content, logs object is null (logger may be closed)");
			return "";
		}
		return logs.toString();
	}

	@Override
	public void close() {
		super.close();
		compressToFile();
		logs = null;
	}

	private void compressToFile() {
		if (logs == null) {
			log.debug("Cannot write to file, logs object is null");
			return;
		}

		if (logs.isEmpty()) {
			log.debug("Skipping file creation, no log content to write");
			return;
		}
		
		String safeOutputFilename = outputFilename != null ? Utils.escape(outputFilename) : "unknown";
		String filename = String.format("%s-%s.log", LOG_FILE_DATE_FORMAT.format(LocalDateTime.now()), safeOutputFilename);
		try (BufferedWriter writer = Files.newBufferedWriter(DOWNLOADS_LOG_FOLDER.resolve(filename),
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING)) {
			writer.write(logs.toString());
		} catch (IOException ex) {
			log.error("Failed to write log file {} \n{}", filename, Utils.stackTraceToString(ex));
		}
	}
}
