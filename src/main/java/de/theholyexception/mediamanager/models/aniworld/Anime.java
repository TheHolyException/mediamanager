package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.datastorage.sql.Result;
import de.theholyexception.holyapi.datastorage.sql.Row;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.webserver.WebSocketUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ToString
@Slf4j
public class Anime {

    @Getter
    private final int id;
    @Getter
    private final int languageId;
    @Getter
    private final String title;
    @Getter
    private final String url;
    @Getter
    private File directory;
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

    public static List<Anime> loadFromDB(DataBaseInterface db) throws SQLException {
        List<Anime> result = new ArrayList<>();

        Result rs = db.getResult("select * from anime");

        for (Row row : rs.getTable(0).getRows()) {
            Anime anime = new Anime(row);
            Season.loadFromDB(db, anime);
            result.add(anime);
        }

        return result;
    }


    public Anime(int languageId, String title, String url) {
        this.languageId = languageId;
        this.title = title;
        this.url = url;
        isDirty = true;
        this.id = Anime.getAndAddCurrentID();
        setDirectoryPath(null);
    }

    public Anime(Row row) {
        this.id = row.get("nKey", Integer.class);
        this.languageId = row.get("nLanguageId", Integer.class);
        this.title = row.get("szTitle", String.class);
        this.url = row.get("szURL", String.class);
        String overridePath = row.get("szCustomDirectory", String.class);
        if (overridePath == null || overridePath.isEmpty()) overridePath = null;
        this.isDirty = false;
        setDirectoryPath(overridePath);
    }

    public void setDirectoryPath(String overridePath) {
        if (overridePath != null) {
            customDirectory = overridePath;
            isDirty = true;
            directory = new File(baseDirectory, overridePath);
        } else {
            String[] segments = url.split("/");
            String path = segments[segments.length-1];
            path = path.replaceAll("[^a-zA-Z0-9-_.]", "_");
            directory = new File(baseDirectory, path);
        }
    }

    public int getEpisodeCount() {
        int cnt = 0;
        for (Season season : seasonList)
            cnt += season.getEpisodeList().size();
        return cnt;
    }

    public int getUnloadedEpisodeCount() {
        int cnt = 0;
        for (Season season : seasonList)
            cnt += (int) season.getEpisodeList().stream().filter(e -> !e.isDownloaded()).count();
        return cnt;
    }

    public List<Episode> getUnloadedEpisodes() {
        List<Episode> result = new ArrayList<>();
        for (Season season : seasonList) {
            for (Episode episode : season.getEpisodeList()) {
                if (episode.isDownloaded() || episode.isDownloading()) continue; // We do not need to download already downloaded episodes xD
                result.add(episode);
            }
        }
        return result;
    }

    public static final Pattern pattern = Pattern.compile("S\\d{2}E\\d{2}");
    public void scanDirectoryForExistingEpisodes() {
        if (directory == null) return;

        File[] files = directory.listFiles();
        if (files == null) return;


        List<String> existingFiles = new ArrayList<>();
        for (File file : files) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.find())
                existingFiles.add(matcher.group());
        }

        for (Season season : seasonList) {
            String sn = String.format("S%02d", season.getSeasonNumber());
            for (Episode episode : season.getEpisodeList().stream().filter(e -> !e.isDownloaded()).toList()) {
                String en = String.format("E%02d", episode.getEpisodeNumber());
                episode.setDownloaded(existingFiles.contains(sn + en));
            }
        }
    }

    public void addSeason(Season season) {
        Optional<Season> optSeason = seasonList.stream().filter(s -> s.getSeasonNumber() == season.getSeasonNumber()).findFirst();
        if (optSeason.isPresent())
            throw new IllegalStateException("Season already exists locally " + season);

        seasonList.add(season);
    }

    public void loadMissingEpisodes() {
        List<Season> onlineSeasons = AniworldHelper.getSeasons(url);
        onlineSeasons.forEach(Season::loadEpisodes);

        for (Season onlineSeason : onlineSeasons) {
            Optional<Season> localSeason = seasonList.stream().filter(season -> season.getSeasonNumber() == onlineSeason.getSeasonNumber()).findFirst();

            if (localSeason.isEmpty()) {
                addSeason(onlineSeason);
                continue;
            }

            for (Episode onlineEpisode : onlineSeason.getEpisodeList()) {
                Optional<Episode> localEpisode = localSeason.get().getEpisodeList().stream().filter(episode -> episode.getEpisodeNumber() == onlineEpisode.getEpisodeNumber()).findFirst();
                if (localEpisode.isEmpty())
                    localSeason.get().addEpisode(onlineEpisode);
            }
        }

        lastUpdate = System.currentTimeMillis();
        WebSocketUtils.sendAutoLoaderItem(null, this);
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
            log.debug("Writing season to db: " + this);
            db.executeSafe("call addAnime(?, ?, ?, ?, ?)",
                    id,
                    languageId,
                    title,
                    url,
                    customDirectory);
            isDirty = false;
        }

        seasonList.forEach(season -> season.writeToDB(db, id));
    }

    public JSONObject toJSONObject() {
        Map<String, Object> object = new HashMap<>();
        object.put("id", id);
        object.put("languageId", languageId);
        object.put("title", title);
        object.put("url", url);
        object.put("unloaded", getUnloadedEpisodeCount());
        object.put("lastScan", lastUpdate);
        object.put("directory", getDirectory().toString().replace(baseDirectory.toString(), ""));
        return new JSONObject(object);
    }

}
