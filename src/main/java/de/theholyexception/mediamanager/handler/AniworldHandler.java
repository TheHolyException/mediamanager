package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.TargetSystem;
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

@Slf4j
public class AniworldHandler extends Handler {


    public AniworldHandler(TargetSystem targetSystem) {
        super(targetSystem);
    }

    @Override
    public void initialize() {
        // Not needed
    }

    @Override
    public WebSocketResponse handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        return switch (command) {
            case "resolve" -> cmdResolve(socket, content);
            default -> {
                log.error("Invalid command " + command);
                yield WebSocketResponse.ERROR.setMessage("Invalid command " + command);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private WebSocketResponse cmdResolve(WebSocketBasic socket, JSONObjectContainer content) {
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
            return WebSocketResponse.ERROR.setMessage(ex.getMessage());
        }
        return null;
    }
}
