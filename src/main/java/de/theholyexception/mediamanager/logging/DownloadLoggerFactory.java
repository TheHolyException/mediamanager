package de.theholyexception.mediamanager.logging;

import de.theholyexception.mediamanager.MediaManager;
import org.tomlj.TomlParseResult;

public class DownloadLoggerFactory {

	public static DownloadLogger getLogger(String name) {
		TomlParseResult config = MediaManager.getInstance().getDependencyInjector().resolve(TomlParseResult.class);
		String loggerType = config.getString("general.downloadLoggerType", () -> "DISK");

		return switch (loggerType.toUpperCase()) {
			case "RAM" -> new RamLogger(name);
			case "FILE" -> new FileLogger(name);
			default -> new FileLogger(name);
		};
	}

}
