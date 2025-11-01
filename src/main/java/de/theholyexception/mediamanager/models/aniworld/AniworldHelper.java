package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.holyapi.util.expiringmap.ExpiringMap;
import de.theholyexception.mediamanager.util.AniworldProvider;
import de.theholyexception.mediamanager.util.ProxyHandler;
import de.theholyexception.mediamanager.util.Utils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.validation.constraints.Null;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class AniworldHelper {

    @Getter
    private static final Map<String, AtomicInteger> statistics = Collections.synchronizedMap(new HashMap<>());

    public static final ExecutorHandler urlResolver = new ExecutorHandler(Executors.newFixedThreadPool(1));

    public static final String ANIWORLD_URL = "https://aniworld.to";

    public static boolean isSeasonLink(String url) {
        urlResolver.setThreadNameFactory(cnt -> "AW-URLResolver-" + cnt);
        return url.contains("staffel");
    }

    /**
     * Resolves all Seasons (including the specials section) of an aniworld url
     * @param url URL to the root page of the season (ex: https://aniworld.to/.../staffel-1)
     * @return List of all Seasons
     */
    public static List<Season> getSeasons(String url) {
        statistics.computeIfAbsent("Multi-Season Requests", k -> new AtomicInteger(0)).incrementAndGet();
        List<Season> result = new ArrayList<>();
        try {
            Connection con = Jsoup.connect(url);
            if (ProxyHandler.hasProxies()) con.proxy(ProxyHandler.getNextProxy());
            Document document = con.get();
            Element streamDiv = document.selectFirst("#stream");
            Elements listElements = streamDiv.select("ul > li");
            for (Element listElement : listElements) {
                if (!listElement.html().contains("href")) continue;
                if (listElement.html().contains("Episode")) break;
                Element domSeason = listElement.selectFirst("a");
                result.add(new Season(domSeason));
            }
        } catch (Exception ex) {
            log.error("Failed to obtain seasons", ex);
        }
        return result;
    }

    public static Season getSeason(String url, int number) {
        statistics.computeIfAbsent("Season Requests", k -> new AtomicInteger(0)).incrementAndGet();
        try {
            Connection con = Jsoup.connect(url);
            if (ProxyHandler.hasProxies()) con.proxy(ProxyHandler.getNextProxy());
            Document document = con.get();
            Element streamDiv = document.selectFirst("#stream");
            Elements listElements = streamDiv.select("ul > li");
            for (Element listElement : listElements) {
                if (!listElement.html().contains("href")) continue;
                if (listElement.html().contains("Episode")) break;
                Element domSeason = listElement.selectFirst("a");
                if (Integer.parseInt(Season.isNumeric(domSeason.text()) ? domSeason.text() : "0") != number)
                    continue;
                return new Season(domSeason);
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
        statistics.computeIfAbsent("Episode Requests", k -> new AtomicInteger(0)).incrementAndGet();
        List<Episode> result = new ArrayList<>();
        try {
            Connection con = Jsoup.connect(url);
            if (ProxyHandler.hasProxies()) con.proxy(ProxyHandler.getNextProxy());
            Document document = con.get();
            Element streamDiv = document.selectFirst("#stream");
            Elements listElements = streamDiv.select("ul > li");
            for (Element listElement : listElements) {
                if (!listElement.html().contains("data-season-id")) continue;
                Element domEpisode = listElement.selectFirst("a");
                result.add(new Episode(domEpisode));
            }
        } catch (Exception ex) {
            log.error("Failed to obtain episodes", ex);
        }
        return result;
    }

    public static @Null String getAnimeTitle(String url) {
        statistics.computeIfAbsent("Title Requests", k -> new AtomicInteger(0)).incrementAndGet();
        try {
            Connection con = Jsoup.connect(url);
            if (ProxyHandler.hasProxies()) con.proxy(ProxyHandler.getNextProxy());
            Document document = con.get();
            Element seriesTitle = document.selectFirst(".series-title > h1 > span");

            return seriesTitle.text();
        } catch (Exception ex) {
            log.error("Failed to obtain anime title", ex);
        }
        return null;
    }

    public static String getRedirectedURL(String url) {
        statistics.computeIfAbsent("Redirect Requests", k -> new AtomicInteger(0)).incrementAndGet();
        try {
            HttpURLConnection con;
            if (ProxyHandler.hasProxies()) {
                con = (HttpURLConnection) new URL(url).openConnection(ProxyHandler.getNextProxy());
            } else {
                con = (HttpURLConnection) new URL(url).openConnection();
            }
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
        if (episode.getAniworldUrl() == null) {
            log.error("Episode has no aniworld-url " + episode.getTitle());
            return null;
        }

        if (episodeLanguageCache.containsKey(episode.getAniworldUrl())) {
            episode.setLanguageIds(new ArrayList<>(episodeLanguageCache.get(episode.getAniworldUrl())));
            return null;
        }
        statistics.computeIfAbsent("Episode Language Requests", k -> new AtomicInteger(0)).incrementAndGet();

        List<Integer> ids = episode.getLanguageIdsRaw();
        if (ids == null) ids = new ArrayList<>();
        final List<Integer> languageIds = ids;

        ExecutorTask task = new ExecutorTask(() -> {
            try {
                Connection con = Jsoup.connect(episode.getAniworldUrl());
                if (ProxyHandler.hasProxies()) con.proxy(ProxyHandler.getNextProxy());
                Document document = con.get();
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

        episode.setLanguageIds(languageIds);

        episodeLanguageCache.put(episode.getAniworldUrl(), languageIds);
        urlResolver.putTask(task, 883855723);
        return task;
    }

    private static final Map<String, String> videoUrlCache = Collections.synchronizedMap(new ExpiringMap<>(1000L*60L*30L, false)); // 30 min
    public static ExecutorTask resolveVideoURL(Episode episode, int languageId) {
        ExecutorTask task = new ExecutorTask(() -> {
            String cacheIdentifier = episode.getAniworldUrl() + languageId;
            if (videoUrlCache.containsKey(cacheIdentifier)) {
                episode.setVideoUrl(videoUrlCache.get(cacheIdentifier));
                return;
            }
            statistics.computeIfAbsent("Resolve Video URL Requests", k -> new AtomicInteger(0)).incrementAndGet();

            try {
                Connection con = Jsoup.connect(episode.getAniworldUrl());
                if (ProxyHandler.hasProxies()) con.proxy(ProxyHandler.getNextProxy());
                Document document = con.get();
                Elements list = document.select(".row > li");
                for (Element element : list) {
                    if (Integer.parseInt(element.attr("data-lang-key")) != languageId) continue;
                    AniworldProvider provider = AniworldProvider.getProvider(element);
                    if (provider == null) continue;
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


    private static final Map<Integer, Map<AniworldProvider, String>> alternateVideoUrlCache = Collections.synchronizedMap(new ExpiringMap<>(1000L*60L*30L, false)); // 30 min

    /**
     * Searches for alternate video urls
     * @param episode Episode to search in
     * @param languageId Language to search for
     * @param exclude Provider to exclude
     */
    public static Map<AniworldProvider,String> resolveAlternateVideoURLs(Episode episode, int languageId, AniworldProvider exclude) {
        Map<AniworldProvider, String> result = new HashMap<>();
        int cacheIdentifier = generateCacheIdentifier(episode.getAniworldUrl(), languageId, exclude == null ? "null" : exclude);
        if (alternateVideoUrlCache.containsKey(cacheIdentifier)) {
            return alternateVideoUrlCache.get(cacheIdentifier);
        }
        statistics.computeIfAbsent("Resolve Alternate Video URL Requests", k -> new AtomicInteger(0)).incrementAndGet();

        try {
            Connection con = Jsoup.connect(episode.getAniworldUrl());
            if (ProxyHandler.hasProxies()) con.proxy(ProxyHandler.getNextProxy());
            Document document = con.get();
            Elements list = document.select(".row > li");

            for (Element element : list) {
                // Check if the language matches
                if (Integer.parseInt(element.attr("data-lang-key")) != languageId) continue;

                AniworldProvider provider = AniworldProvider.getProvider(element);
                // Check if we support the provider and if we should exclude it
                if (provider == null || provider.equals(exclude)) {
                    log.debug("No provider found for " + element.selectFirst(".watchEpisode > i").attr("title") + " excluding " + exclude);
                    log.debug(provider == null ? "No provider found" : provider.toString());
                    continue;
                }

                String url = AniworldHelper.getRedirectedURL(AniworldHelper.ANIWORLD_URL+element.attr("data-link-target"));
                result.put(provider, url);

            }
            alternateVideoUrlCache.put(cacheIdentifier, result);
        } catch (Exception ex) {
            log.error("Failed to load video url", ex);
        }
        return result;
    }

    private static final Pattern pattern = Pattern.compile("https:\\/\\/aniworld\\.to\\/anime\\/stream\\/([^\\/]+)");
    private static final Map<String, String> urlToSubdirectory = new HashMap<>();
    public static synchronized String getSubdirectoryFromURL(String url) {
        if (urlToSubdirectory.containsKey(url))
            return urlToSubdirectory.get(url);

        Matcher matcher = pattern.matcher(url);
        if (!matcher.find()) {
            String uuid = UUID.randomUUID().toString();
            log.error("Failed to obtain anime directory name from url: " + url);
            log.error("Using alternative directory: ./" + uuid);
            urlToSubdirectory.put(url, uuid);
            return uuid;
        }
        String match = matcher.group(1);
        match = Utils.escape(match);
        urlToSubdirectory.put(url, match);
        return match;
    }

    public static int generateCacheIdentifier(Object... data) {
        return Arrays.stream(data)
                .map(Object::toString)
                .collect(Collectors.joining())
                .hashCode();
    }
}
