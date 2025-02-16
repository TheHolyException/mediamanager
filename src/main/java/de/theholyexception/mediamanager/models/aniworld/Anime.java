package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.datastorage.sql.interfaces.SQLiteInterface;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.MediaManager;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.xml.crypto.dsig.spec.XPathType;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ToString
public class Anime {

    @Getter
    private int id = -1;
    @Getter
    private int languageId;
    @Getter
    private String title;
    @Getter
    private String url;
    @Getter
    private File directory;
    @Getter
    private List<Season> seasonList = new ArrayList<>();

    @Getter
    private boolean isDirty = false;

    @Getter @Setter
    private static int currentID;
    @Setter
    private static File baseDirectory;

    private static int getAndAddCurrentID() {
        return ++currentID;
    }

    public static List<Anime> loadFromDB(DataBaseInterface db) throws SQLException {
        List<Anime> result = new ArrayList<>();

        ResultSet rs = db.executeQuery("""
                select nKey,
                       nLanguageId,
                       szTitle,
                       szURL
                from anime
                """);
        while (rs.next()) {
            Anime anime = new Anime(rs);
            Season.loadFromDB(db, anime);
            result.add(anime);
        }
        return result;
    }


    public Anime(int id, int languageId, String title, String url) {
        this.id = id;
        this.languageId = languageId;
        this.title = title;
        this.url = url;
        isDirty = true;
        setDirectoryPath();
    }

    public Anime(ResultSet rs) throws SQLException {
        this.id = rs.getInt("nKey");
        this.languageId = rs.getInt("nLanguageId");
        this.title = rs.getString("szTitle");
        this.url = rs.getString("szURL");
        this.isDirty = false;
        setDirectoryPath();
    }

    @Deprecated(forRemoval = true)
    public void addEpisode(Season season, Episode episode) {
        Optional<Season> optSeason = seasonList.stream().filter(s -> s.getSeasonNumber() == season.getSeasonNumber()).findFirst();
        if (optSeason.isEmpty())
            throw new IllegalStateException("Season locally not found " + season);
        if (optSeason.get().getEpisodeList().stream().anyMatch(e -> e.getId() == episode.getId()))
            throw new IllegalStateException("Episode already exists locally " + episode);

        Season s = optSeason.get();
        s.getEpisodeList().add(episode);
        s.isDirty = true;
    }

    private void setDirectoryPath() {
        String[] segments = url.split("/");
        String path = segments[segments.length-1];
        path = path.replaceAll("[^a-zA-Z0-9-_.]", "_");
        directory = new File(baseDirectory, path);
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
            for (Episode episode : season.getEpisodeList()) {
                String en = String.format("E%02d", episode.getEpisodeNumber());
                if (existingFiles.contains(sn + en))
                    episode.setDownloaded(true);
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
                    addEpisode(onlineSeason, onlineEpisode);
            }
        }
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
        int animeId = id == -1 ? Anime.getAndAddCurrentID() : id;
        if (isDirty) {
            System.out.println(this);
            if (db instanceof SQLiteInterface) {
                db.executeSafe("""
                insert or replace into anime (
                    nKey,
                    nLanguageId,
                    szTitle,
                    szURL
                ) values (
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                        animeId,
                        languageId,
                        title,
                        url);
            } else {
                db.executeSafe("call addAnime(?, ?, ?, ?)",
                        animeId,
                        languageId,
                        title,
                        url);
            }
            isDirty = false;
        }

        seasonList.forEach(season -> season.writeToDB(db, animeId));
    }

}
