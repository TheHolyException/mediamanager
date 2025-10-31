package de.theholyexception.mediamanager.api;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.di.DIInitializer;
import de.theholyexception.holyapi.di.DIInject;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.handler.DefaultHandler;
import de.theholyexception.mediamanager.handler.Handler;
import de.theholyexception.mediamanager.util.TargetSystem;
import de.theholyexception.mediamanager.util.WebSocketResponseException;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tomlj.TomlParseResult;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WebServer implements DIInitializer {

	@Getter
	private Javalin app = null;
	private ExecutorHandler executorHandler;
	private ScheduledExecutorService keepAliveExecutor;
	@Getter
	private Set<WsContext> activeConnections;

	@DIInject
	private MediaManager mediaManager;
	@DIInject
	private TomlParseResult config;

	@DIInject
	private DefaultHandler defaultHandler;

	@Override
	public void initialize() {
		if (app != null)
			throw new IllegalStateException("WebServer already initialized");

		int webSocketThreads = Math.toIntExact(config.getLong("webserver.webSocketThreads", () -> 10));
		executorHandler = new ExecutorHandler(Executors.newFixedThreadPool(webSocketThreads));
		executorHandler.setThreadNameFactory(cnt -> "WS-Executor-" + cnt);

		// Initialize keepalive system
		keepAliveExecutor = Executors.newScheduledThreadPool(1);
		activeConnections = ConcurrentHashMap.newKeySet();
		startKeepAliveTimer();

		String webserverHost = config.getString("webserver.host", () -> "localhost");
		int webserverPort = Math.toIntExact(config.getLong("webserver.port", () -> 8080));
		String webRoot = config.getString("webserver.webroot", () -> "./www");

		app = Javalin.create(config -> {
				config.registerPlugin(new OpenApiPlugin(pluginConfig -> {
					pluginConfig.withDefinitionConfiguration((version, definition) -> {
						definition.withInfo(info -> {
							info.setTitle("MediaManager API");
							info.setVersion("1.0");
							info.setDescription("API for MediaManager");
						});
					});
				}));
				config.staticFiles.add(staticFiles -> {
					staticFiles.hostedPath = "/";
					staticFiles.directory = webRoot;
					staticFiles.location = Location.EXTERNAL;
				});
				config.showJavalinBanner = false;

				config.registerPlugin(new SwaggerPlugin());

			})
			.start(webserverHost, webserverPort);

		mediaManager.getHandlers().values().forEach(handler -> handler.registerAPI(app));

		app.ws("/", ws -> {
			ws.onConnect(activeConnections::add);
			ws.onMessage(this::handleWebsocketMessage);
			ws.onBinaryMessage(ctx -> log.debug("Binary message received"));
			ws.onClose(activeConnections::remove);
			ws.onError(ctx -> {
				activeConnections.remove(ctx);
				String errorMsg = ctx.error() != null ? ctx.error().getMessage() : "Unknown error";
				log.error("WebSocket error for {}: {}", ctx.sessionId(), errorMsg);
			});
		});


	}

	private void handleWebsocketMessage(WsMessageContext ctx) {
		JSONObjectContainer dataset = (JSONObjectContainer) JSONReader.readString(ctx.message());

		String targetSystem = dataset.get("targetSystem", "default", String.class);
		String cmd = dataset.get("cmd", String.class);
		JSONObjectContainer content = dataset.getObjectContainer("content", new JSONObjectContainer());
		
		// Handle keepalive messages
		if ("keepalive".equals(cmd)) {
			return;
		}

		Handler handler = mediaManager.getHandlers().get(TargetSystem.valueOf(targetSystem.toUpperCase()));
		if (handler == null)
			throw new IllegalStateException("Invalid target-system: " + targetSystem);

		executorHandler.putTask(new ExecutorTask(() -> {
			WebSocketResponse response = null;
			try {
				handler.handleCommand(ctx, cmd, content);
			} catch (WebSocketResponseException ex) {
				response = ex.getResponse();
			} catch (Exception ex) {
				log.error("Failed to process command", ex);
				response = WebSocketResponse.ERROR.setMessage(ex.getMessage());
			}
			if (response != null) {
				response.getResponse().set("sourceCommand", cmd);
				WebSocketUtils.sendPacket("response", handler.getTargetSystem(),response.getResponse().getRaw(), ctx);
			}
		}));
	}
	
	private void startKeepAliveTimer() {
		keepAliveExecutor.scheduleAtFixedRate(() -> {
			for (WsContext ctx : activeConnections) {
				try {
					sendKeepAlivePing(ctx);
				} catch (Exception e) {
					log.warn("Failed to send keepalive to {}: {}", ctx.sessionId(), e.getMessage());
					activeConnections.remove(ctx);
				}
			}
		}, 15, 15, TimeUnit.SECONDS); // Send keepalive every 30 seconds
	}
	
	private void sendKeepAlivePing(WsContext ctx) {
		JSONObjectContainer keepAliveMessage = new JSONObjectContainer();
		keepAliveMessage.set("cmd", "keepalive");
		keepAliveMessage.set("type", "ping");
		keepAliveMessage.set("timestamp", System.currentTimeMillis());
		ctx.send(keepAliveMessage.toString());
	}
	
	public void shutdown() {
		if (keepAliveExecutor != null && !keepAliveExecutor.isShutdown()) {
			keepAliveExecutor.shutdown();
			try {
				if (!keepAliveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
					keepAliveExecutor.shutdownNow();
				}
			} catch (InterruptedException e) {
				keepAliveExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

}
