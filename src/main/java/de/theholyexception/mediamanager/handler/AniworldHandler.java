package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.aniworld.Season;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static de.theholyexception.mediamanager.webserver.WebSocketUtils.sendPacket;

@Slf4j
public class AniworldHandler extends Handler {


    public AniworldHandler(String targetSystem) {
        super(targetSystem);
    }

    @Override
    public void initialize() {
    }

    @Override
    public void handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        super.handleCommand(socket, command, content);

        switch (command) {
            case "resolve" -> resolve(socket, content);
            default -> log.error("invalid dataset " + command);
        }

    }

    private void resolve(WebSocketBasic socket, JSONObjectContainer content) {
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
            Episode.urlResolver.awaitGroup(1);
            seasonList.forEach(s -> links.addAll(s.getEpisodeList().stream().map(Episode::getVideoUrl).toList()));

            JSONObject res = new JSONObject();
            JSONArray ds = new JSONArray();
            ds.addAll(links);
            res.put("links", ds);
            sendPacket("links", "aniworld", res, socket);
        } catch (Exception ex) {
            ex.printStackTrace();
            JSONObject res = new JSONObject();
            res.put("error", ex.getMessage());
            sendPacket("links", "aniworld", res, socket);
        }
    }
}
