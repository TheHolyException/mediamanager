package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.util.WebSocketResponseException;
import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.util.TargetSystem;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.aniworld.Season;
import de.theholyexception.mediamanager.webserver.WebSocketResponse;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static de.theholyexception.mediamanager.webserver.WebSocketUtils.sendPacket;

/**
 * Handler for resolving anime episode links from Aniworld.
 * This handler processes requests to resolve video URLs for anime episodes,
 * supporting both individual seasons and entire series.
 */
@Slf4j
public class AniworldHandler extends Handler {


    /**
     * Creates a new AniworldHandler for the specified target system.
     *
     * @param targetSystem The target system this handler is responsible for
     */
    public AniworldHandler(TargetSystem targetSystem) {
        super(targetSystem);
    }

    /**
     * Initializes the handler. No initialization is required for this handler.
     */
    @Override
    public void initialize() {
        // No initialization needed
    }

    /**
     * Processes incoming WebSocket commands for the Aniworld handler.
     * Routes commands to the appropriate handler method based on the command type.
     *
     * @param socket The WebSocket connection that received the command
     * @param command The command to execute (currently only "resolve" is supported)
     * @param content JSON data associated with the command
     * @throws WebSocketResponseException if the command is invalid or processing fails
     */
    @Override
    public void handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        switch (command) {
            case "resolve" -> cmdResolve(socket, content);
            default ->
                throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage("Invalid command " + command));
        }
    }

    /**
     * Handles the 'resolve' command to retrieve video URLs for anime episodes.
     * Supports resolving both individual seasons and entire series.
     *
     * @param socket The WebSocket connection to send the results to
     * @param content JSON data containing the resolution parameters:
     *                - language: The language ID to filter episodes
     *                - url: The Aniworld URL to resolve (can be a series or season URL)
     * @throws WebSocketResponseException if resolution fails or invalid parameters are provided
     */
    @SuppressWarnings("unchecked")
    private void cmdResolve(WebSocketBasic socket, JSONObjectContainer content) {
        try {
            int language = Integer.parseInt(content.get("language", String.class));
            String url = content.get("url", String.class);

            List<String> links = new ArrayList<>();
            List<Season> seasonList;
            if (!AniworldHelper.isSeasonLink(url)) {
                seasonList = AniworldHelper.getSeasons(url);
            } else {
                String[] urlSeg = url.split("/");
                String selector = urlSeg[urlSeg.length-1];

                int number = 0;
                if (selector.contains("staffel")) {
                    number = Integer.parseInt(selector.split("-")[1]);
                }

                seasonList = new ArrayList<>();
                seasonList.add(AniworldHelper.getSeason(url, number));
            }

            seasonList.forEach(Season::loadEpisodes);
            seasonList.forEach(s -> s.loadVideoURLs(language));
            AniworldHelper.urlResolver.awaitGroup(1);
            for (Season season : seasonList) {
                for (Episode episode : season.getEpisodeList()) {
                    if (episode.getLanguageIds().contains(language))
                        links.add(episode.getVideoUrl());
                }
            }

            JSONObject res = new JSONObject();
            JSONArray ds = new JSONArray();
            ds.addAll(links);
            res.put("links", ds);
            sendPacket("links", TargetSystem.ANIWORLD, res, socket);
        } catch (Exception ex) {
            log.error("Failed to resolve anime", ex);
            throw new WebSocketResponseException(WebSocketResponse.ERROR.setMessage(ex.getMessage()));
        }
    }

}
