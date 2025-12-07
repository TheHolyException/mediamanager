package de.theholyexception.mediamanager.logging;

import de.theholyexception.mediamanager.util.MediaManagerConfig;

public class DownloadLoggerFactory {

	public static DownloadLogger getLogger(String name) {;
		String loggerType = MediaManagerConfig.Logging.downloadLoggerType;

		return switch (loggerType.toUpperCase()) {
			case "RAM" -> new RamLogger(name);
			case "FILE" -> new FileLogger(name);
			default -> new FileLogger(name);
		};
	}

}
