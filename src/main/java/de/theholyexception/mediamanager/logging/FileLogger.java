package de.theholyexception.mediamanager.logging;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class FileLogger extends DownloadLogger {

	@Getter
	private Path file;
	private FileChannel fileChannel;

	public FileLogger(String name) {
		super(name);
		try {
			this.file = DOWNLOADS_LOG_FOLDER.resolve(name);
			this.fileChannel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		} catch (IOException ex) {
			log.error("Failed to initialize file logger", ex);
		}
	}

	@Override
	public void write(Level level, String message) {
		super.write(level, message);
		if (fileChannel == null) {
			log.debug("Cannot write log line because the fileChannel is null");
			return;
		}
		
		try {
			String logLine = String.format("%s %s \t%s\n", LOG_FILE_DATE_FORMAT.format(java.time.LocalDateTime.now()), level, message);
			fileChannel.write(java.nio.ByteBuffer.wrap(logLine.getBytes()));
		} catch (IOException ex) {
			log.error("Failed to write log line", ex);
		}
	}

	@Override
	public synchronized String getContent() {
		if (file == null || !Files.exists(file)) {
			log.debug("Cannot read content, file is null or doesn't exist");
			return "";
		}
		
		try {
			return Files.readString(file);
		} catch (IOException ex) {
			log.error("Failed to read log file content", ex);
			return "";
		}
	}

	@Override
	public void close() {
		super.close();
		compress();
	}

	@Override
	public void changeOutputFilename(String newName) {
		super.changeOutputFilename(newName);
		if (fileChannel == null) {
			log.debug("Cannot change file name, fileChannel is null");
			return;
		}
		
		try {
			fileChannel.close();
			Path newFile = DOWNLOADS_LOG_FOLDER.resolve(newName);
			log.debug("Updating log file -> {}", newName);

			Files.move(file, newFile);
			file = newFile;
			fileChannel = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		} catch (IOException ex) {
			log.error("Failed to update log file", ex);
			fileChannel = null;
		}
	}

	public void compress() {
		if (fileChannel == null) {
			log.debug("Cannot compress log file, fileChannel is null");
			return;
		}
		
		try {
			fileChannel.close();
			fileChannel = null;

			if (!Files.exists(file) || Files.size(file) == 0) {
				log.debug("Skipping compression, file is empty or doesn't exist");
				Files.deleteIfExists(file);
				return;
			}
			
			Path compressedFile = DOWNLOADS_LOG_FOLDER.resolve(file.getFileName() + ".gz");
			try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(compressedFile))) {
				gzipOutputStream.write(Files.readAllBytes(file));
			}
			Files.delete(file);
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to compress log file", ex);
		}
	}

}
