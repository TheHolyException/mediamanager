package de.theholyexception.mediamanager;

import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.smarthttp.HTTPRequestOptions;
import me.kaigermany.ultimateutils.networking.smarthttp.HTTPResult;
import me.kaigermany.ultimateutils.networking.smarthttp.SmartHTTP;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ProxyHandler {


	private static int cursor = 0;
	private static final List<Proxy> proxies = new ArrayList<>();

	private ProxyHandler() {
	}

	public static void initialize(TomlParseResult config) {
		TomlArray array = config.getArray("proxies");
		if (array == null) {
			log.error("No proxies found in config");
			return;
		}

		for (int i = 0; i < array.size(); i ++) {
			TomlTable x = array.getTable(i);
			String host = x.getString("host", () -> "127.0.0.1");
			int port = Math.toIntExact(x.getLong("port", () -> 8080));
			Proxy.Type type = Proxy.Type.valueOf(x.getString("type", () -> "SOCKS").toUpperCase());
			Proxy proxy = new Proxy(type, new InetSocketAddress(host, port));
			if (!checkTorNetwork(proxy)) {
				log.error("Proxy {}:{} ({}) is not a tor proxy", host, port, type);
				continue;
			}
			proxies.add(proxy);
			log.info("Added proxy: {}:{} ({})", host, port, type);
		}
	}

	public static Proxy getNextProxy() {
		synchronized (proxies) {
			if (proxies.isEmpty())
				return null;
			Proxy proxy = proxies.get(cursor);
			cursor = (cursor + 1) % proxies.size();
			return proxy;
		}
	}

	public static boolean hasProxies() {
		synchronized (proxies) {
			return !proxies.isEmpty();
		}
	}

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

	public static List<Proxy> getProxies() {
		return proxies;
	}
}
