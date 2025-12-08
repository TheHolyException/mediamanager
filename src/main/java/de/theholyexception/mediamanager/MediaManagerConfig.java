package de.theholyexception.mediamanager;

import ch.qos.logback.classic.Level;
import de.theholyexception.mediamanager.models.Target;
import de.theholyexception.mediamanager.util.InitializationException;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MediaManagerConfig {

	private static File file;
	private static TomlParseResult config;

	public static class General {
		public static Level logLevel;
		public static String ffmpeg;
		public static boolean enablePacketBuffer;
		public static int packetBufferSleep;
		
		static void load(TomlParseResult config) {
			logLevel = Level.valueOf(config.getString("general.logLevel", () -> "INFO"));
			ffmpeg = config.getString("general.ffmpeg", () -> "ffmpeg");
			enablePacketBuffer = config.getBoolean("general.enablePacketBuffer", () -> true);
			packetBufferSleep = Math.toIntExact(config.getLong("general.packetBufferSleep", () -> 100));
		}
	}
	
	public static class Logging {
		public static Level logLevel;
		public static String downloadLoggerType;
		public static Path debugLogFolder;
		public static Path downloadLogFolder;
		
		static void load(TomlParseResult config) {
			logLevel = Level.valueOf(config.getString("logging.logLevel", () -> "INFO"));
			downloadLoggerType = config.getString("logging.downloadLoggerType", () -> "RAM");
			debugLogFolder = Paths.get(config.getString("logging.debugLogFolder", () -> "debug-logs"));
			downloadLogFolder = Paths.get(config.getString("logging.downloadLogFolder", () -> "debug-logs/downloads"));

			try {
				Files.createDirectories(debugLogFolder);
				Files.createDirectories(downloadLogFolder);
			} catch (Exception ex) {
				throw new IllegalStateException("Failed to create debug log directories", ex);
			}
		}
	}

	public static class Downloader {
		public static String tmpDownloadFolder;
		public static boolean useDirectMemory;
		public static boolean untrustedCertificates;

		static void load(TomlParseResult config) {
			tmpDownloadFolder = config.getString("downloader.tmpDownloadFolder", () -> "./tmp");
			useDirectMemory = config.getBoolean("downloader.useDirectMemory", () -> false);
			untrustedCertificates = config.getBoolean("downloader.untrustedCertificates", () -> false);
		}
	}

	public static class Validator {
		public static boolean enabled;
		public static double videoLengthThreshold;
		public static double videoSizeThreshold;
		public static String targets;

		static void load(TomlParseResult config) {
			enabled = config.getBoolean("validator.enabled", () -> true);
			videoLengthThreshold = Integer.parseInt(config.getString("validator.videoLengthThreshold", () -> "20%").replace("%", ""))/100d;
			videoSizeThreshold = Integer.parseInt(config.getString("validator.videoSizeThreshold", () -> "20%").replace("%", ""))/100d;
			targets = config.getString("validator.targets", () -> "stream-animes");
		}
	}

	public static class Proxy {
		public static boolean enabled;

		static void load(TomlParseResult config) {
			enabled = config.getBoolean("proxy.enabled", () -> false);

		}
	}

	public static class Proxies {
		public static List<ProxyEntry> list = new ArrayList<>();

		static void load(TomlParseResult config) {
			list.clear();
			var proxyArray = config.getArray("proxies");
			if (proxyArray == null) return;

			for (int i = 0; i < proxyArray.size(); i++) {
				var proxyTable = proxyArray.getTable(i);
				if (proxyTable != null) {
					String host = proxyTable.getString("host");
					Long portLong = proxyTable.getLong("port");
					java.net.Proxy.Type type = java.net.Proxy.Type.valueOf(proxyTable.getString("type"));

					if (host == null) throw new IllegalStateException("Missing required 'host' in proxies[" + i + "]");
					if (portLong == null) throw new IllegalStateException("Missing required 'port' in proxies[" + i + "]");
					if (type == null) throw new IllegalStateException("Missing required 'type' in proxies[" + i + "]");

					list.add(new ProxyEntry(host, Math.toIntExact(portLong), type));
				}
			}
		}
	}

	public static class WebServer {
		public static int port;
		public static String host;
		public static String webroot;
		public static int webSocketThreads;
		
		static void load(TomlParseResult config) {
			port = Math.toIntExact(config.getLong("webserver.port", () -> 8080L));
			host = config.getString("webserver.host", () -> "0.0.0.0");
			webroot = config.getString("webserver.webroot", () -> "./www");
			webSocketThreads = Math.toIntExact(config.getLong("webserver.webSocketThreads", () -> 10));
		}
	}

	public static class MySQL {
		public static String host;
		public static int port;
		public static String username;
		public static String password;
		public static String database;

		static void load(TomlParseResult config) {
			host = config.getString("mysql.host", () -> "localhost");
			port = Math.toIntExact(config.getLong("mysql.port", () -> 3306L));
			username = config.getString("mysql.username", () -> "root");
			password = config.getString("mysql.password", () -> "");
			database = config.getString("mysql.database", () -> "mediamanager");
		}
	}

	public static class Autoloader {
		public static boolean enabled;
		public static boolean executeDBScripts;
		public static int checkIntervalMin;
		public static int checkDelayMs;
		public static int urlResolverThreads;


		static void load(TomlParseResult config) {
			enabled = config.getBoolean("autoloader.enabled", () -> false);
			executeDBScripts = config.getBoolean("autoloader.executeDBScripts", () -> false);
			checkIntervalMin = Math.toIntExact(config.getLong("autoloader.checkIntervalMin", () -> 5));
			checkDelayMs = Math.toIntExact(config.getLong("autoloader.checkDelayMs", () -> 500));
			urlResolverThreads = Math.toIntExact(config.getLong("autoloader.urlResolverThreads", () -> 10));
		}
	}

	public static class Targets {
		public static List<Target> list = new ArrayList<>();

		static void load(TomlParseResult config) {
			list.clear();
			var targetArray = config.getArray("target");
			if (targetArray == null) return;

			for (int i = 0; i < targetArray.size(); i++) {
				var targetTable = targetArray.getTable(i);
				if (targetTable != null) {
					String identifier = targetTable.getString("identifier");
					String path = targetTable.getString("path");
					Boolean subFolders = targetTable.getBoolean("subFolders");
					String displayName = targetTable.getString("displayName");

					if (identifier == null) throw new IllegalStateException("Missing required 'identifier' in target[" + i + "]");
					if (path == null) throw new IllegalStateException("Missing required 'path' in target[" + i + "]");
					if (subFolders == null) throw new IllegalStateException("Missing required 'subFolders' in target[" + i + "]");
					if (displayName == null) throw new IllegalStateException("Missing required 'displayName' in target[" + i + "]");

					list.add(new Target(identifier, displayName, path, subFolders));
				}
			}
		}
	}

	public static void initialize(Path path) {
		if (file != null)
			throw new IllegalStateException("Already initialized");

		try {
			config = Toml.parse(path);
			StringBuilder errors = new StringBuilder();
			config.errors().forEach(error -> errors.append(error.getMessage()));
			if (!errors.isEmpty())
				throw new InitializationException("Failed to parse config.toml", errors.toString());
		} catch (IOException ex) {
			throw new InitializationException("Failed to load " + path.getFileName(), ex.getMessage());
		}

		log.debug("Loading configuration sections");
		
		General.load(config);
		Logging.load(config);
		Downloader.load(config);
		Validator.load(config);
		Proxy.load(config);
		WebServer.load(config);
		MySQL.load(config);
		Autoloader.load(config);
		Proxies.load(config);
		Targets.load(config);
		
		log.info("Configuration loaded successfully");
	}

	public static class ProxyEntry {
		public final String host;
		public final int port;
		public final java.net.Proxy.Type type;

		public ProxyEntry(String host, int port, java.net.Proxy.Type type) {
			this.host = host;
			this.port = port;
			this.type = type;
		}
	}


}
