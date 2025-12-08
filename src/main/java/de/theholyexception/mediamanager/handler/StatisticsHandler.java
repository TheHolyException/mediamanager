package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.di.DIInject;
import de.theholyexception.holyapi.util.GUIUtils;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.models.DownloadTask;
import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.MediaManagerConfig;
import de.theholyexception.mediamanager.util.ProxyHandler;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiResponse;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.StaticUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class StatisticsHandler extends Handler {

	@DIInject
	private DefaultHandler defaultHandler;

	private long dockerMemoryLimit;
	private long dockerMemoryUsage;
	private JSONObject torNetworkStatus;
	private final Queue<JSONObject> memoryHistory = new LinkedList<>();
	private final Queue<JSONObject> downloadHistory = new LinkedList<>();
	private final Queue<JSONObject> threadHistory = new LinkedList<>();
	private static final int MAX_HISTORY_SIZE = 120; // Keep last 720 data points (60 minutes at 5-second intervals)

	public StatisticsHandler() {
	}

	@Override
	public void initialize() {
		startSystemDataFetcher();
		startDockerDataFetcher();
		startProxyDataFetcher();
	}

	private void startSystemDataFetcher() {
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				captureStatistics();
			}
		}, 0, 5000);
	}

	private void startDockerDataFetcher() {
		if (MediaManager.getInstance().isDockerEnvironment()) {
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					try {
						dockerMemoryLimit = Long.parseLong(new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.max").start().getInputStream())).trim());
						dockerMemoryUsage = Long.parseLong(new String(StaticUtils.readAllBytes(new ProcessBuilder("cat", "/sys/fs/cgroup/memory.current").start().getInputStream())).trim());
					} catch (Exception ex) {
						log.error(ex.getMessage());
					}
				}
			}, 0, 5000);
		}
	}

	private void startProxyDataFetcher() {
		if (MediaManagerConfig.Proxy.enabled) {
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					torNetworkStatus = ProxyHandler.getProxyStatus();
				}
			}, 0, 30000);
		}
	}

	@Override
	public void registerAPI(Javalin app) {
		app.get("/api/system", this::getSystemInformation);
	}

	@OpenApi(
		summary = "Gets the system statistics and information",
		operationId = "getSystemInformation",
		path = "/api/system",
		tags = {"Statistics"},
		methods = HttpMethod.GET,
		responses = {
			@OpenApiResponse(status = "200", description = "System information")
		}
	)
	private void getSystemInformation(Context ctx) {
		// Enable gzip compression for this response
		ctx.header("Content-Encoding", "gzip");
		ctx.header("Content-Type", "application/json");
		
		// Get the JSON data
		JSONObject systemInfo = formatSystemInfo();
		String jsonString = systemInfo.toJSONString();
		
		try {
			// Compress the JSON with gzip
			java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
			java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos);
			gzipOut.write(jsonString.getBytes("UTF-8"));
			gzipOut.close();
			
			// Send compressed response
			ctx.result(baos.toByteArray());
		} catch (Exception e) {
			log.error("Failed to compress system info response", e);
			// Fallback to uncompressed
			ctx.json(systemInfo);
		}
	}

	/**
	 * Collects and formats system information into a JSON object.
	 *
	 * @return JSONObject containing system metrics including memory usage, thread pool information, etc.
	 */
	@SuppressWarnings("unchecked")
	private JSONObject formatSystemInfo() {
		JSONObject response = new JSONObject();
		JSONObject torNetwork = torNetworkStatus;
		response.put("torNetwork", torNetwork);

		ThreadPoolExecutor threadPoolExecutor = ((ThreadPoolExecutor) DownloadTask.getDownloadHandler().getExecutorService());
		response.put("memory", getMemoryStatistics());
		response.put("threadPool", getThreadPoolStatistics(threadPoolExecutor));
		response.put("system", getDownloaderStatistics(threadPoolExecutor));
		response.put("docker", getDockerStatistics());
		response.put("version", getVersionData());

		JSONObject aniworld = new JSONObject();
		AniworldHelper.getStatistics().forEach((k, v) -> aniworld.put(k, v.get()));
		response.put("aniworld", aniworld);

		// Add historical data
		response.put("memoryHistory", convertHistoryData(memoryHistory, "timestamp", "usagePercent", "usedBytes"));
		response.put("downloadHistory", convertHistoryData(downloadHistory, "timestamp", "total", "active", "failed", "completed"));
		response.put("threadHistory", convertHistoryData(threadHistory, "timestamp", "active", "queued", "RUNNABLE", "WAITING", "NEW", "BLOCKED", "TIMED_WAITING", "TERMINATED", "max"));

		return response;
	}

	private Map<String, AtomicInteger> getThreadData() {
		Map<String, AtomicInteger> map = new HashMap<>();
		for (Thread downloadThread : DownloadTask.getDownloadHandler().getThreadList())
			map.computeIfAbsent(downloadThread.getState().toString(), k -> new AtomicInteger(0)).incrementAndGet();
		return map;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getMemoryStatistics() {
		Runtime runtime = Runtime.getRuntime();
		long usedMemory = runtime.totalMemory() - runtime.freeMemory();
		long totalMemory = runtime.totalMemory();
		long maxMemory = runtime.maxMemory();

		JSONObject result = new JSONObject();
		result.put("current", GUIUtils.formatStorageSpace(usedMemory));
		result.put("heap", GUIUtils.formatStorageSpace(totalMemory));
		result.put("max", GUIUtils.formatStorageSpace(maxMemory));
		result.put("currentBytes", usedMemory);
		result.put("heapBytes", totalMemory);
		result.put("maxBytes", maxMemory);
		result.put("usagePercent", Math.round((double) usedMemory / maxMemory * 100.0));
		return result;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getThreadPoolStatistics(ThreadPoolExecutor threadPoolExecutor) {
		JSONObject result = new JSONObject();
		result.putAll(getThreadData());
		result.put("max", threadPoolExecutor.getMaximumPoolSize());
		result.put("active", threadPoolExecutor.getActiveCount());
		result.put("queued", threadPoolExecutor.getQueue().size());
		result.put("completed", threadPoolExecutor.getCompletedTaskCount());
		return result;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getDownloaderStatistics(ThreadPoolExecutor threadPoolExecutor) {
		Map<UUID, DownloadTask> urls = defaultHandler.getUrls();

		JSONObject result = new JSONObject();
		result.put("availableProcessors", Runtime.getRuntime().availableProcessors());
		result.put("totalDownloads", urls.size());
		result.put("activeDownloads", (int) urls.values().stream().filter(DownloadTask::isRunning).count());
		result.put("failedDownloads", (int) urls.values().stream().filter(DownloadTask::isFailed).count());
		result.put("completedDownloads", (int) threadPoolExecutor.getCompletedTaskCount());
		return result;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getDockerStatistics() {
		JSONObject result = new JSONObject();
		result.put("memoryLimit", GUIUtils.formatStorageSpace(dockerMemoryLimit));
		result.put("memoryUsage", GUIUtils.formatStorageSpace(dockerMemoryUsage));
		result.put("memoryLimitBytes", dockerMemoryLimit);
		result.put("memoryUsageBytes", dockerMemoryUsage);
		if (dockerMemoryLimit > 0)
			result.put("usagePercent", Math.round((double) dockerMemoryUsage / dockerMemoryLimit * 100.0));
		return result;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getVersionData() {
		JSONObject result = new JSONObject();
		result.put("downloaders", MediaManager.getInstance().getDownloadersVersion());
		result.put("ultimateutils", MediaManager.getInstance().getUltimateutilsVersion());
		result.put("holyapi", MediaManager.getInstance().getHolyapiVersion());
		return result;
	}

	private void captureStatistics() {
		long currentTime = System.currentTimeMillis();
		ThreadPoolExecutor threadPoolExecutor = ((ThreadPoolExecutor) DownloadTask.getDownloadHandler().getExecutorService());
		captureMemoryStatistics(currentTime);
		captureThreadStatistics(currentTime, threadPoolExecutor);
		captureDownloadStatistics(currentTime, threadPoolExecutor);
	}

	@SuppressWarnings("unchecked")
	private void captureMemoryStatistics(long timestamp) {
		Runtime runtime = Runtime.getRuntime();
		long usedMemory = runtime.totalMemory() - runtime.freeMemory();
		long maxMemory = runtime.maxMemory();

		JSONObject memoryPoint = new JSONObject();
		memoryPoint.put("timestamp", timestamp);
		memoryPoint.put("usagePercent", Math.round((double) usedMemory / maxMemory * 100.0));
		memoryPoint.put("usedBytes", usedMemory);
		memoryHistory.offer(memoryPoint);
		if (memoryHistory.size() > MAX_HISTORY_SIZE) {
			memoryHistory.poll();
		}
	}

	@SuppressWarnings("unchecked")
	private void captureThreadStatistics(long timestamp, ThreadPoolExecutor threadPoolExecutor) {
		JSONObject threadPoint = new JSONObject();
		threadPoint.put("timestamp", timestamp);
		threadPoint.putAll(getThreadData()); // Thread states (NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED)
		threadPoint.put("max", threadPoolExecutor.getMaximumPoolSize());
		threadPoint.put("active", threadPoolExecutor.getActiveCount());
		threadPoint.put("queued", threadPoolExecutor.getQueue().size());
		// Explicitly exclude completed counter from history
		threadHistory.offer(threadPoint);
		if (threadHistory.size() > MAX_HISTORY_SIZE) {
			threadHistory.poll();
		}
	}

	private void captureDownloadStatistics(long timestamp, ThreadPoolExecutor threadPoolExecutor) {
		Map<UUID, DownloadTask> urls = defaultHandler.getUrls();
		JSONObject downloadPoint = new JSONObject();
		downloadPoint.put("timestamp", timestamp);
		downloadPoint.put("total", urls.size());
		downloadPoint.put("active", (int) urls.values().stream().filter(DownloadTask::isRunning).count());
		downloadPoint.put("failed", (int) urls.values().stream().filter(DownloadTask::isFailed).count());
		downloadPoint.put("completed", (int) threadPoolExecutor.getCompletedTaskCount());
		downloadHistory.offer(downloadPoint);
		if (downloadHistory.size() > MAX_HISTORY_SIZE) {
			downloadHistory.poll();
		}
	}

	@SuppressWarnings("unchecked")
	private JSONObject convertHistoryData(Queue<JSONObject> history, String... fieldNames) {
		JSONObject convertedData = new JSONObject();

		if (history.isEmpty()) {
			for (String fieldName : fieldNames) {
				convertedData.put(fieldName, new JSONArray());
			}
			return convertedData;
		}

		Map<String, JSONArray> fieldArrays = new HashMap<>();
		for (String fieldName : fieldNames) {
			fieldArrays.put(fieldName, new JSONArray());
		}

		for (JSONObject point : history) {
			for (String fieldName : fieldNames) {
				Object value = point.get(fieldName);
				if (value != null) {
					fieldArrays.get(fieldName).add(value);
				} else {
					fieldArrays.get(fieldName).add(0);
				}
			}
		}

		convertedData.putAll(fieldArrays);
		return convertedData;
	}
}
