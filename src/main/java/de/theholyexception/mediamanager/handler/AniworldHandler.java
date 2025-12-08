package de.theholyexception.mediamanager.handler;

import de.theholyexception.mediamanager.models.aniworld.AniworldHelper;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.aniworld.Season;
import de.theholyexception.mediamanager.util.TargetSystem;
import de.theholyexception.mediamanager.util.Utils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handler for resolving anime episode links from Aniworld.
 * This handler processes requests to resolve video URLs for anime episodes,
 * supporting both individual seasons and entire series.
 */
@Slf4j
public class AniworldHandler extends Handler {

    private static final String ERROR_KEY = "error";

    /**
     * Creates a new AniworldHandler for the specified target system.
     */
    public AniworldHandler() {
        super(TargetSystem.ANIWORLD);
    }

    /**
     * Initializes the handler. No initialization is required for this handler.
     */
    @Override
    public void initialize() {
        // No initialization needed
    }

    @Override
    public void registerAPI(Javalin app) {
        app.get("/api/aniworld/resolve", this::resolveAnimeRequest);
    }

    /**
     * REST API endpoint to resolve anime episode links.
     * Expects query parameters 'language' and 'url'.
     *
     * @param ctx The HTTP context
     */
    @OpenApi(
            summary = "Resolve anime episode links",
            description = "Resolves video URLs for anime episodes from Aniworld URLs",
            operationId = "resolveAnimeLinks",
            path = "/api/aniworld/resolve",
            tags = {"Aniworld"},
            methods = HttpMethod.GET,
            queryParams = {
                    @OpenApiParam(name = "url", description = "The Aniworld URL to resolve", required = true),
                    @OpenApiParam(name = "language", description = "Language ID to filter episodes", required = true)
               },
            responses = {
                    @OpenApiResponse(status = "200", description = "Successfully resolved episode links"),
                    @OpenApiResponse(status = "400", description = "Invalid request data"),
                    @OpenApiResponse(status = "500", description = "Server error")
            }
    )
    private void resolveAnimeRequest(Context ctx) {
        try {
            String url = ctx.queryParam("url");
            String languageStr = ctx.queryParam("language");
            
            if (url == null || languageStr == null) {
                ctx.status(400).json(Map.of(ERROR_KEY, "Missing required parameters: url and language"));
                return;
            }

            Optional<Integer> language = Utils.parseInteger(languageStr);
            if (language.isEmpty()) {
                ctx.status(400).json(Map.of(ERROR_KEY, "Invalid language ID format"));
                return;
            }

            List<String> links = resolveAnimeLinks(language.get(), url);
            ctx.json(Map.of("links", links));
        } catch (Exception ex) {
            log.error("Failed to resolve anime via REST API", ex);
            ctx.status(500).json(Map.of(ERROR_KEY, ex.getMessage()));
        }
    }

    /**
     * Core logic to resolve anime episode links.
     * Extracted from cmdResolve to be reusable for both WebSocket and REST API.
     *
     * @param language The language ID to filter episodes
     * @param url The Aniworld URL to resolve
     * @return List of resolved video URLs
     */
    private List<String> resolveAnimeLinks(int language, String url) {
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
        
        return links;
    }

}
