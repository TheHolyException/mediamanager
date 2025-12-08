package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.datastorage.sql.Result;
import de.theholyexception.holyapi.datastorage.sql.Row;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.mediamanager.handler.DefaultHandler;
import de.theholyexception.mediamanager.util.Utils;
import de.theholyexception.mediamanager.util.WebResponseException;
import de.theholyexception.mediamanager.util.WebSocketResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ToString
@Slf4j
public class Anime {

    @Getter
    private final int id;
    @Getter
    private int languageId;
    @Getter
    private final String title;
    @Getter
    private final String url;
    @Getter
    private File directory;
    @Getter
    private List<Integer> excludedSeasons;
    @Getter
    private boolean paused;
    @Getter @Setter
    private boolean scanning = false;
    @Getter @Setter
    private boolean downloading = false;
    @Getter
    private final List<Season> seasonList = new ArrayList<>();
    private Long lastUpdate = 0L;

    @Getter
    private boolean isDirty;

    @Getter @Setter
    private static int currentID;
    @Setter
    private static File baseDirectory;
    private String customDirectory;


    private static int getAndAddCurrentID() {
        return ++currentID;
    }

    public static List<Anime> loadFromDB(DataBaseInterface db) {
        List<Anime> result = new ArrayList<>();

        Result rs = db.getResult("select * from anime");

        for (Row row : rs.getTable(0).getRows()) {
            Anime anime = new Anime(row);
            Season.loadFromDB(db, anime);
            result.add(anime);
        }

        return result;
    }

    public static Anime createFromContent(Context httpContext) {
        JSONObjectContainer content = (JSONObjectContainer) JSONReader.readString(httpContext.body());
        String url = content.get("url", String.class);
        int languageId = content.get("languageId", Integer.class);
        String directory = content.get("directory", String.class);
        if (directory != null && directory.isEmpty()) directory = null;
        if (directory == null)
            directory = AniworldHelper.getSubdirectoryFromURL(url);

        String title = AniworldHelper.getAnimeTitle(url);
        if (title == null) {
            httpContext.status(HttpStatus.BAD_REQUEST);
            httpContext.json(Map.of("error", "Cannot resolve anime title from " + url));
            return null;
        }

        List<Integer> excludedSeasonList = new ArrayList<>();
        String excludedSeasonsString = content.get("excludedSeasons", String.class);
        if (excludedSeasonsString != null && !excludedSeasonsString.isEmpty()) {
            String[] excludedSeasons = excludedSeasonsString.split(",");
            for (String excludedSeason : excludedSeasons) {
                try {
                    excludedSeasonList.add(Integer.parseInt(excludedSeason.trim()));
                } catch (NumberFormatException e) {
                    httpContext.status(HttpStatus.BAD_REQUEST);
                    httpContext.json(Map.of("error", "Invalid excluded season number: " + excludedSeason));
                    return null;
                }
            }
        }

        Anime anime = new Anime(languageId, title, url, excludedSeasonList);
        anime.setDirectoryPath(directory, true);
        return anime;
    }


    public Anime(int languageId, String title, String url, List<Integer> excludedSeasons) {
        this.languageId = languageId;
        this.title = title;
        this.url = url;
        isDirty = true;
        this.id = Anime.getAndAddCurrentID();
        this.excludedSeasons = excludedSeasons;
        this.paused = false;
        setDirectoryPath(null, false);
    }

    public Anime(Row row) {
        this.id = row.get("nKey", Integer.class);
        this.languageId = row.get("nLanguageId", Integer.class);
        this.title = row.get("szTitle", String.class);
        this.url = row.get("szURL", String.class);
        String overridePath = row.get("szCustomDirectory", String.class);
        if (overridePath == null || overridePath.isEmpty()) overridePath = null;
        this.excludedSeasons = new ArrayList<>();
        String szExcludedSeasonsString = row.get("szExcludedSeasons", String.class);
        if (szExcludedSeasonsString != null && !szExcludedSeasonsString.isEmpty()) {
            for (String s : szExcludedSeasonsString.split(","))
                this.excludedSeasons.add(Integer.parseInt(s));
        }
        Boolean pausedValue = row.get("bPaused", Boolean.class);
        this.paused = pausedValue != null && pausedValue;
        this.isDirty = false;
        setDirectoryPath(overridePath, false);
    }

    public void setDirectoryPath(String overridePath, boolean markDirty) {
        if (overridePath != null) {
            overridePath = Utils.escape(overridePath);
            customDirectory = overridePath;
            if (markDirty) isDirty = true;
            directory = new File(baseDirectory, overridePath);
        } else {
            directory = new File(baseDirectory, AniworldHelper.getSubdirectoryFromURL(url));
        }
    }

    /**
     * Sets the language ID for this anime subscription.
     * This will affect which episodes are considered for download.
     * 
     * @param languageId The new language ID
     * @param markDirty Whether to mark the object as dirty for database persistence
     */
    public void setLanguageId(int languageId, boolean markDirty) {
        this.languageId = languageId;
        if (markDirty) {
            this.isDirty = true;
        }
    }

    /**
     * Sets the excluded seasons for this anime subscription.
     * Episodes from excluded seasons will not be downloaded.
     * 
     * @param excludedSeasons List of season numbers to exclude
     * @param markDirty Whether to mark the object as dirty for database persistence
     */
    public void setExcludedSeasons(List<Integer> excludedSeasons, boolean markDirty) {
        this.excludedSeasons = new ArrayList<>(excludedSeasons);
        if (markDirty) {
            this.isDirty = true;
        }
    }

    /**
     * Sets the paused state for this anime subscription.
     * When paused, the anime will not be included in automatic downloads.
     * 
     * @param paused Whether the subscription should be paused
     * @param markDirty Whether to mark the object as dirty for database persistence
     */
    public void setPaused(boolean paused, boolean markDirty) {
        this.paused = paused;
        if (markDirty) {
            this.isDirty = true;
        }
    }

    /**
     * Gets the current status string for display in the frontend.
     * Returns appropriate status text based on current state.
     * 
     * @return Status string: "Scanning" (yellow), "Paused" (gray), or "Active" (green)
     */
    public String getStatusString() {
        if (scanning) {
            return "Scanning";
        } else if (downloading) {
            return "Downloading";
        } else if (paused) {
            return "Paused";
        } else {
            return "Active";
        }
    }

    public int getUnloadedEpisodeCount(boolean filterLanguage) {
        int cnt = 0;
        for (Season season : seasonList) {
            if (excludedSeasons.contains(season.getSeasonNumber()))
                continue;

            for (Episode episode : season.getEpisodeList()) {
                if (episode.isDownloaded()) continue;
                if (filterLanguage) {
                    if (episode.getLanguageIds().contains(languageId))
                        cnt++;
                } else {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    public List<Episode> getUnloadedEpisodes() {
        List<Episode> result = new ArrayList<>();
        for (Season season : seasonList) {
            if (excludedSeasons.contains(season.getSeasonNumber()))
                continue;

            if (season.getAnime() == null)
                season.setAnime(this);

            for (Episode episode : season.getEpisodeList()) {
                if (episode.isDownloaded() || episode.isDownloading()) continue; // We do not need to download already downloaded episodes xD
                if (!episode.getLanguageIds().contains(languageId)) continue; // We also do not want any episodes that are not in the selected language
                if (episode.getSeason() == null)
                    episode.setSeason(season);
                result.add(episode);
            }
        }
        return result;
    }

    public static final Pattern pattern = Pattern.compile("S\\d{2}E\\d{2}");
    public void scanDirectoryForExistingEpisodes() {
        if (directory == null) {
            log.warn("No directory set for " + title);
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) return;


        List<String> existingFiles = new ArrayList<>();
        for (File file : files) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.find())
                existingFiles.add(matcher.group());
        }

        for (Season season : seasonList) {
            if (excludedSeasons.contains(season.getSeasonNumber()))
                continue;

            String sn = String.format("S%02d", season.getSeasonNumber());
            for (Episode episode : season.getEpisodeList().stream().toList()) {
                String en = String.format("E%02d", episode.getEpisodeNumber());
                boolean exists = existingFiles.contains(sn + en);
                episode.setDownloaded(exists);
            }
        }
    }

    public void addSeason(Season season) {
        if (excludedSeasons.contains(season.getSeasonNumber()))
            return;

        Optional<Season> optSeason = seasonList.stream().filter(s -> s.getSeasonNumber() == season.getSeasonNumber()).findFirst();
        if (optSeason.isPresent())
            log.error("Season already exists locally " + season);

        seasonList.add(season);
    }

    public void loadMissingEpisodes() {
        List<Season> onlineSeasons = AniworldHelper.getSeasons(url);
        onlineSeasons.forEach(Season::loadEpisodes);

        for (Season onlineSeason : onlineSeasons) {
            if (excludedSeasons.contains(onlineSeason.getSeasonNumber()))
                continue;

            Optional<Season> localSeason = seasonList.stream().filter(season -> season.getSeasonNumber() == onlineSeason.getSeasonNumber()).findFirst();

            // If the season does not exist locally, add it
            if (localSeason.isEmpty()) {
                addSeason(onlineSeason);
                continue;
            }

            // Add missing episodes
            for (Episode onlineEpisode : onlineSeason.getEpisodeList()) {
                Optional<Episode> localEpisode = localSeason.get().getEpisodeList().stream().filter(episode -> episode.getEpisodeNumber() == onlineEpisode.getEpisodeNumber()).findFirst();
                if (localEpisode.isEmpty())
                    localSeason.get().addEpisode(onlineEpisode);
            }
        }

        lastUpdate = System.currentTimeMillis();
    }

    /**
     * Initiates downloads for all unloaded episodes of a specific anime.
     * Creates download tasks for each missing episode and adds them to the download queue.
     */
    public void runDownload(DataBaseInterface db, DefaultHandler defaultHandler) {
        for (Episode unloadedEpisode : getUnloadedEpisodes()) {
            unloadedEpisode.setDownloading(true);
            try {
                unloadedEpisode.loadVideoURL(getLanguageId(), () -> {
                    JSONObjectContainer data = new JSONObjectContainer();
                    data.set("uuid", UUID.randomUUID().toString());
                    data.set("state", "new");
                    data.set("url", unloadedEpisode.getVideoUrl());
                    data.set("target", "stream-animes/"+getDirectory().getName());
                    data.set("created", System.currentTimeMillis());

                    JSONObjectContainer autoloaderData = new JSONObjectContainer();
                    autoloaderData.set("animeId", getId());
                    autoloaderData.set("seasonId", unloadedEpisode.getSeason().getId());
                    autoloaderData.set("episodeId", unloadedEpisode.getId());
                    data.set("autoloaderData", autoloaderData.getRaw());

                    JSONObjectContainer options = new JSONObjectContainer();
                    options.set("useDirectMemory", "false");
                    options.set("enableSessionRecovery", "false");
                    options.set("enableSeasonAndEpisodeRenaming", "true");
                    data.set("options", options.getRaw());

                    log.debug("Scheduling download internally: {}", data);
                    defaultHandler.scheduleDownload(data);
                });
            } catch (Exception ex) {
                log.error("", ex);
                throw new WebResponseException(WebSocketResponse.ERROR.setMessage("Download failed: " + ex.getMessage()));
            } finally {
                unloadedEpisode.setDownloading(false);
            }
        }
        writeToDB(db);
    }

    public boolean isDeepDirty() {
        if (isDirty) return true;
        for (Season season : seasonList) {
            if (season.isDirty) return true;
            for (Episode episode : season.getEpisodeList()) {
                if (episode.isDirty()) return true;
            }
        }
        return false;
    }

    public void writeToDB(DataBaseInterface db) {
        if (isDirty) {
            log.debug("Writing anime to db: " + this);
            db.executeSafe("call addAnime(?, ?, ?, ?, ?, ?, ?)",
                    id,
                    languageId,
                    title,
                    url,
                    customDirectory == null ?"" : customDirectory,
                    Utils.intergerListToString(excludedSeasons),
                    paused ? 1 : 0);
            isDirty = false;
        }

        seasonList.forEach(season -> season.writeToDB(db, id));
    }

    public void updateLastUpdate() {
        lastUpdate = System.currentTimeMillis();
    }


    public JSONObject toJSONObject() {
        Map<String, Object> object = new HashMap<>();
        object.put("id", id);
        object.put("languageId", languageId);
        object.put("title", title);
        object.put("url", url);
        object.put("unloaded", getUnloadedEpisodeCount(true) + " [\uD83C\uDDE9\uD83C\uDDEA]  ("+getUnloadedEpisodeCount(false)+"[\uD83C\uDDEF\uD83C\uDDF5])");
        object.put("lastScan", lastUpdate);
        String relativePath = getDirectory().toString().replace(baseDirectory.toString(), "");
        // Remove leading path separators to avoid them being escaped as underscores
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            relativePath = relativePath.substring(1);
        }
        object.put("directory", relativePath);
        object.put("excludedSeasons", Utils.intergerListToString(excludedSeasons));
        object.put("paused", paused);
        object.put("status", getStatusString());
        return new JSONObject(object);
    }

}
