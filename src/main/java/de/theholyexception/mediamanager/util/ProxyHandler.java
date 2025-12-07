package de.theholyexception.mediamanager.util;

import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.smarthttp.HTTPRequestOptions;
import me.kaigermany.ultimateutils.networking.smarthttp.HTTPResult;
import me.kaigermany.ultimateutils.networking.smarthttp.SmartHTTP;
import me.kaigermany.ultimateutils.sync.thread.Parallel;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles proxy configuration and management for the MediaManager application.
 * Supports multiple proxy configurations with automatic Tor network verification.
 * Proxies are managed in a round-robin fashion for load balancing.
 */
@Slf4j
public class ProxyHandler {


	private static int cursor = 0;
	private static final List<Proxy> proxies = new ArrayList<>();

	/**
	 * Private constructor to prevent instantiation
	 */
	private ProxyHandler() {
	}

	/**
	 * Initializes the proxy handler
	 * Validates each proxy by checking if it's a Tor exit node.
	 *
	 * @throws IllegalArgumentException if the configuration is invalid
	 */
	public static void initialize() {
		if (!MediaManagerConfig.Proxy.enabled)
			return;
		List<MediaManagerConfig.ProxyEntry> array = MediaManagerConfig.Proxies.list;
		if (array == null) {
			log.error("No proxies found in config");
			return;
		}

		Parallel.exec(array.size(), i -> {
			MediaManagerConfig.ProxyEntry x = array.get(i);
			Proxy proxy = new Proxy(x.type, new InetSocketAddress(x.host, x.port));
			if (!checkTorNetwork(proxy)) {
				log.error("Proxy {}:{} ({}) is not a tor proxy", x.host, x.port, x.type);
				return;
			}
			proxies.add(proxy);
			log.info("Added proxy: {}:{} ({})", x.host, x.port, x.type);
		}, array.size());
	}

	/**
	 * Retrieves the next available proxy in a round-robin fashion.
	 *
	 * @return The next Proxy in the rotation, or null if no proxies are configured
	 */
	public static Proxy getNextProxy() {
		synchronized (proxies) {
			if (proxies.isEmpty())
				return null;
			Proxy proxy = proxies.get(cursor);
			cursor = (cursor + 1) % proxies.size();
			return proxy;
		}
	}

	/**
	 * Checks if any proxies are configured and available.
	 *
	 * @return true if proxies are available, false otherwise
	 */
	public static boolean hasProxies() {
		synchronized (proxies) {
			return !proxies.isEmpty();
		}
	}

	/**
	 * Verifies if a proxy is a valid Tor exit node by checking against the Tor Project's API.
	 *
	 * @param proxy The proxy to verify
	 * @return true if the proxy is a valid Tor exit node, false otherwise
	 */
	private static boolean checkTorNetwork(Proxy proxy) {
		try {
			HTTPResult result = SmartHTTP.request(new HTTPRequestOptions("https://check.torproject.org/api/ip").setProxy(proxy));
			JSONObject torNetworkStatus = (JSONObject) new JSONParser().parse(new String(result.getData()));
			if (torNetworkStatus == null) return false;
			if (!torNetworkStatus.containsKey("IsTor")) return false;
			return Boolean.parseBoolean(torNetworkStatus.get("IsTor").toString());
		} catch (IOException | ParseException ex) {
			log.error(ex.getMessage());
			return false;
		}
	}

	/**
	 * Checks the status of the Tor network by making a request to the Tor project's check.torproject.org API.
	 */
	public static JSONObject getProxyStatus() {
		JSONObject result = new JSONObject();

		for (Proxy proxy : ProxyHandler.getProxies()) {
			try {
				HTTPResult response = SmartHTTP.request(new HTTPRequestOptions("https://check.torproject.org/api/ip").setProxy(proxy));
				JSONObject responseJ = (JSONObject) new JSONParser().parse(new String(response.getData()));
				result.put(proxy.address().toString(), responseJ.get("IP").toString());
			} catch (IOException | ParseException ex) {
				log.error(ex.getMessage());
				result.put(proxy.address().toString(), "NOT CONNECTED");
			}
		}

		return result;
	}

	/**
	 * Retrieves an unmodifiable list of all configured proxies.
	 *
	 * @return List of all configured Proxy instances
	 */
	public static List<Proxy> getProxies() {
		synchronized (proxies) {
			return new ArrayList<>(proxies);
		}
	}
}
