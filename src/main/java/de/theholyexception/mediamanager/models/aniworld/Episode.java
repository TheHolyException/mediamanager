package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.datastorage.sql.Result;
import de.theholyexception.holyapi.datastorage.sql.Row;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.AniworldHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

@ToString
@Slf4j
@Getter
public class Episode {

    private final int id;
    private final int episodeNumber;
    private String url;
    @Setter
    private String videoUrl;
    private final String title;
    private boolean downloaded = false;
    @Setter
    private boolean downloading = false;
    @Getter @Setter
    private List<Integer> languageIds = new ArrayList<>();
    private boolean isDirty;



    private Episode(int episodeNumber, int id, String url, String title, boolean isDirty) {
        this.episodeNumber = episodeNumber;
        this.id = id;
        if (url.startsWith("https://voe")) this.videoUrl = url;
        else this.url = url;
        this.title = title;
        this.isDirty = isDirty;
        AniworldHelper.resolveEpisodeLanguages(this);
    }

    public static void loadFromDB(DataBaseInterface db, Season season) {
        Result rs = db.getResult(String.format("""
                        select *
                        from episode
                        where nSeasonLink = %s
                        """, season.getId()));
        for (Row row : rs.getTable(0).getRows()) {
            Episode e = new Episode(row);
            season.addEpisode(e);
        }
    }

    public static Episode parseFromElement(Element element) {
        return new Episode(
                Integer.parseInt(element.text()),
                Integer.parseInt(element.attr("data-episode-id")),
                AniworldHelper.ANIWORLD_URL + element.attr("href"),
                element.attr("title"),
                true
        );
    }

    public Episode(Row row) {
        this.episodeNumber = row.get("nEpisodeNumber", Integer.class);
        this.id = row.get("nKey", Integer.class);
        String szURL = row.get("szURL", String.class);
        if (szURL.startsWith(AniworldHelper.ANIWORLD_URL))
            this.url = szURL;
        else
            this.videoUrl = szURL;
        this.title = row.get("szTitle", String.class);
        this.downloaded = row.get("bLoaded", Integer.class) == 1;
        this.isDirty = false;
    }

    public void writeToDB(DataBaseInterface db, int seasonLink) {
        if (!isDirty)
            return;

        log.debug("Writing episode to db: " + this);
        db.executeSafe("call addEpisode(?, ?, ?, ?, ?, ?)",
                id,
                seasonLink,
                episodeNumber,
                title,
                videoUrl == null ? url : videoUrl,
                downloaded ? 1 : 0);
        isDirty = false;
    }

    public void loadVideoURL(int languageId) {
        loadVideoURL(languageId, null);
    }

    public void loadVideoURL(int languageId, Runnable then) {
        if (videoUrl != null) return; // No need to parse, already parsed
        if (url == null) {
            log.error("Cant load videoURL because url is null");
            return;
        }
        ExecutorTask task = AniworldHelper.resolveVideoURL(this, languageId);
        if (then != null)
            task.onComplete(then);
        isDirty = true;
    }

    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
        isDirty = true;
    }

}
