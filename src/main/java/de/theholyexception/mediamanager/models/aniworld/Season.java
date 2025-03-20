package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.datastorage.sql.Result;
import de.theholyexception.holyapi.datastorage.sql.Row;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.mediamanager.AniworldHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class Season {

    @Getter
    private final int id;
    @Getter
    private final int seasonNumber;
    @Getter
    private final String url;
    @Getter
    private List<Episode> episodeList = Collections.synchronizedList(new ArrayList<>());

    @Getter
    protected boolean isDirty;
    @Getter
    private Anime anime;

    @Getter @Setter
    private static int currentID;

    private static int getAndAddCurrentID() {
        return ++currentID;
    }


    public Season(Element element) {
        this.seasonNumber = Integer.parseInt(isNumeric(element.text()) ? element.text() : "0");
        this.url = "https://aniworld.to" + element.attr("href");
        this.isDirty = true;
        this.id = Season.getAndAddCurrentID();
    }

    public Season(Row row, Anime anime) {
        this.id = row.get("nKey", Integer.class);
        this.url = row.get("szURL", String.class);
        this.seasonNumber = row.get("nSeasonNumber", Integer.class);
        this.isDirty = false;

        this.anime = anime;
    }

    public static void loadFromDB(DataBaseInterface db, Anime anime) {
        Result rs = db.getResult(String.format("""
                        select *
                        from season
                        where nAnimeLink = %s
                        """, anime.getId()));
        for (Row row : rs.getTable(0).getRows()) {
            Season season = new Season(row, anime);
            Episode.loadFromDB(db, season);
            anime.addSeason(season);
        }
    }

    public void addEpisode(Episode episode) {
        episodeList.add(episode);
    }

    public void writeToDB(DataBaseInterface db, int animeLink) {
        if (isDirty) {

            log.debug("Writing season to db: " + this);
            db.executeSafe("call addSeason(?, ?, ?, ?)",
                    id,
                    seasonNumber,
                    animeLink,
                    url);
            isDirty = false;
        }

        episodeList.forEach(episode -> episode.writeToDB(db, id));
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
