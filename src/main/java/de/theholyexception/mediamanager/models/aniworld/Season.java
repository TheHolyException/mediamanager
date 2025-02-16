package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.datastorage.sql.interfaces.SQLiteInterface;
import de.theholyexception.mediamanager.AniworldHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jsoup.nodes.Element;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ToString
public class Season {

    @Getter
    private int id = -1;
    @Getter
    private int seasonNumber;
    @Getter
    private String url;
    @Getter
    private List<Episode> episodeList = Collections.synchronizedList(new ArrayList<>());

    @Getter
    protected boolean isDirty = false;

    @Getter @Setter
    private static int currentID;

    private static int getAndAddCurrentID() {
        return ++currentID;
    }

    public static void loadFromDB(DataBaseInterface db, Anime anime) throws SQLException {
        ResultSet rs = db.executeQuerySafe("""
                        select nKey,
                               nSeasonNumber,
                               szURL
                        from season
                        where nAnimeLink = ?
                        """, anime.getId());
        while (rs.next()) {
            Season season = new Season(rs);
            Episode.loadFromDB(db, season);
            anime.addSeason(season);
        }
    }

    public Season(int seasonNumber, String url, boolean isDirty) {
        this.seasonNumber = seasonNumber;
        this.url = url;
        this.isDirty = isDirty;
    }

    public static Season parseFromElement(Element element) {
        return new Season(
                Integer.parseInt(isNumeric(element.text()) ? element.text() : "0"),
                "https://aniworld.to" + element.attr("href"),
                true);
    }

    public Season(ResultSet rs) throws SQLException {
        this.id = rs.getInt("nKey");
        this.url = rs.getString("szURL");
        this.seasonNumber = rs.getInt("nSeasonNumber");
        this.isDirty = false;
    }

    public void addEpisode(Episode episode) {
        episodeList.add(episode);
    }

    public void writeToDB(DataBaseInterface db, int animeLink) {
        int seasonId = id == -1 ? Season.getAndAddCurrentID() : id;
        if (isDirty) {
            System.out.println(this);
            if (db instanceof SQLiteInterface) {
                db.executeSafeAsync("""
                insert or replace into season (
                    nKey,
                    nAnimeLink,
                    nSeasonNumber,
                    szURL
                ) values (
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                        seasonId,
                        animeLink,
                        seasonNumber,
                        url);
            } else {
                db.executeSafe("call addSeason(?, ?, ?, ?)",
                        seasonId,
                        seasonNumber,
                        animeLink,
                        url);
            }
            isDirty = false;
        }

        episodeList.forEach(episode -> episode.writeToDB(db, seasonId));
    }

    public static boolean isNumeric(String str) {
        ParsePosition pos = new ParsePosition(0);
        NumberFormat.getInstance().parse(str, pos);
        return str.length() == pos.getIndex();
    }

    public void loadEpisodes() {
        this.episodeList = AniworldHelper.getEpisodes(this);
    }

    public void loadVideoURLs(int languageId) {
        this.episodeList.forEach(episode -> episode.loadVideoURL(languageId));
    }

}
