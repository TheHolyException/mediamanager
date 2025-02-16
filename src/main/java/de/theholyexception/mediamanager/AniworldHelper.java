package de.theholyexception.mediamanager;

import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.aniworld.Season;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AniworldHelper {

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
            ex.printStackTrace();
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
            ex.printStackTrace();
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
            ex.printStackTrace();
        }
        return result;
    }

    public static String getAnimeTitle(String url) {
        try {
            Document document = Jsoup.connect(url).get();
            Element seriesTitle = document.selectFirst(".series-title > h1 > span");

            return seriesTitle.text();
        } catch (Exception ex) {
            ex.printStackTrace();
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
            ex.printStackTrace();
        }
        return null;
    }

}
