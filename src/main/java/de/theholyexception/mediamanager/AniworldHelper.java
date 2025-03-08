package de.theholyexception.mediamanager;

import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.holyapi.util.expiringmap.ExpiringMap;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.aniworld.Season;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Slf4j
public class AniworldHelper {

    private AniworldHelper() {}

    public static final ExecutorHandler urlResolver = new ExecutorHandler(Executors.newFixedThreadPool(20));

    public static final String ANIWORLD_URL = "https://aniworld.to";

    public static boolean isSeasonLink(String url) {
        return url.contains("staffel");
    }


    /**
     * Resolves all Seasons (including the specials section) of an aniworld url
     * @param url URL to the root page of the season (ex: https://aniworld.to/.../staffel-1)
     * @return List of all Seasons
     */
    public static List<Season> getSeasons(String url) {
        List<Season> result = new ArrayList<>();
        try {
            Document document = Jsoup.connect(url).get();
            Element streamDiv = document.selectFirst("#stream");
            Elements listElements = streamDiv.select("ul > li");
            for (Element listElement : listElements) {
                if (!listElement.html().contains("href")) continue;
                if (listElement.html().contains("Episode")) break;
                Element domSeason = listElement.selectFirst("a");
                result.add(Season.parseFromElement(domSeason));
            }
        } catch (Exception ex) {
            log.error("Failed to obtain seasons", ex);
        }
        return result;
    }

    public static Season getSeason(String url, int number) {
        try {
            Document document = Jsoup.connect(url).get();
            Element streamDiv = document.selectFirst("#stream");
            Elements listElements = streamDiv.select("ul > li");
            for (Element listElement : listElements) {
                if (!listElement.html().contains("href")) continue;
                if (listElement.html().contains("Episode")) break;
                Element domSeason = listElement.selectFirst("a");
                if (Integer.parseInt(Season.isNumeric(domSeason.text()) ? domSeason.text() : "0") != number)
                    continue;
                return Season.parseFromElement(domSeason);
            }
        } catch (Exception ex) {
            log.error("Failed to obtain season", ex);
        }
        return null;
    }

    public static List<Episode> getEpisodes(Season season) {
        return getEpisodes(season.getUrl());
    }

    /**
     * Resolves all Seasons (including the specials section)
     * @param url Season of which the episodes should be resolved
     * @return List of all Episodes from a season
     */
    public static List<Episode> getEpisodes(String url) {
        List<Episode> result = new ArrayList<>();
        try {
            Document document = Jsoup.connect(url).get();
            Element streamDiv = document.selectFirst("#stream");
            Elements listElements = streamDiv.select("ul > li");
            for (Element listElement : listElements) {
                if (!listElement.html().contains("data-season-id")) continue;
                Element domEpisode = listElement.selectFirst("a");
                result.add(Episode.parseFromElement(domEpisode));
            }
        } catch (Exception ex) {
            log.error("Failed to obtain episodes", ex);
        }
        return result;
    }

    public static String getAnimeTitle(String url) {
        try {
            Document document = Jsoup.connect(url).get();
            Element seriesTitle = document.selectFirst(".series-title > h1 > span");

            return seriesTitle.text();
        } catch (Exception ex) {
            log.error("Failed to obtain anime title", ex);
        }
        return null;
    }

    public static String getRedirectedURL(String url) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false); // Disable automatic redirect following

            int responseCode = con.getResponseCode();

            // Look for redirect response codes
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                return con.getHeaderField("Location");
            }
        } catch (Exception ex) {
            log.error("Failed to follow redirect url", ex);
        }
        return null;
    }


    private static final ExpiringMap<String, List<Integer>> episodeLanguageCache = new ExpiringMap<>(1000L*60L*30L, false);
    public static ExecutorTask resolveEpisodeLanguages(Episode episode) {
        if (episodeLanguageCache.containsKey(episode.getUrl())) {
            episode.setLanguageIds(new ArrayList<>(episodeLanguageCache.get(episode.getUrl())));
            return null;
        }

        List<Integer> languageIds = episode.getLanguageIds();
        ExecutorTask task = new ExecutorTask(() -> {
            try {
                Document document = Jsoup.connect(episode.getUrl()).get();
                Elements list = document.select(".row > li");
                for (Element element : list) {
                    int langId = Integer.parseInt(element.attr("data-lang-key"));
                    if (languageIds.contains(langId)) continue;
                    languageIds.add(langId);
                }
            } catch (Exception ex) {
                log.error("Failed to parse episode language Id", ex);
            }
        });

        episodeLanguageCache.put(episode.getUrl(), languageIds);
        urlResolver.putTask(task);
        return task;
    }

    private static final Map<String, String> videoUrlCache = Collections.synchronizedMap(new ExpiringMap<>(1000L*60L*30L, false)); // 30 min
    public static ExecutorTask resolveVideoURL(Episode episode, int languageId) {
        ExecutorTask task = new ExecutorTask(() -> {
            String cacheIdentifier = episode.getUrl() + languageId;
            if (videoUrlCache.containsKey(cacheIdentifier)) {
                episode.setVideoUrl(videoUrlCache.get(cacheIdentifier));
                return;
            }

            try {
                Document document = Jsoup.connect(episode.getUrl()).get();
                Elements list = document.select(".row > li");
                for (Element element : list) {
                    if (Integer.parseInt(element.attr("data-lang-key")) != languageId) continue;
                    episode.setVideoUrl(AniworldHelper.getRedirectedURL(AniworldHelper.ANIWORLD_URL+element.attr("data-link-target")));
                    break;
                }

                if (episode.getVideoUrl() != null)
                    videoUrlCache.put(cacheIdentifier, episode.getVideoUrl());
            } catch (Exception ex) {
                log.error("Failed to load video url", ex);
            }
        });
        urlResolver.putTask(task, 1);
        return task;
    }

}
