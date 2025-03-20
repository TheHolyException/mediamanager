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

import java.util.List;

@ToString
@Slf4j
@Getter
public class Episode {

    private final int id;
    private final int episodeNumber;
    private String aniworldUrl;
    @Setter
    private String videoUrl;
    private final String title;
    private boolean downloaded = false;
    @Setter
    private boolean downloading = false;
    @Setter
    private List<Integer> languageIds = null;
    private boolean isDirty;



    private Episode(int episodeNumber, int id, String aniworldUrl, String videoUrl, String title, boolean isDirty) {
        this.episodeNumber = episodeNumber;
        this.id = id;
        this.videoUrl = videoUrl;
        this.aniworldUrl = aniworldUrl;
        this.title = title;
        this.isDirty = isDirty;
    }

    public List<Integer> getLanguageIds() {
        if (languageIds == null) {
            AniworldHelper.resolveEpisodeLanguages(this);
        }
        AniworldHelper.urlResolver.awaitGroup(883855723);
        return languageIds;
    }

    public List<Integer> getLanguageIdsRaw() {
        return languageIds;
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
                null,
                element.attr("title"),
                true
        );
    }

    public Episode(Row row) {
        this.episodeNumber = row.get("nEpisodeNumber", Integer.class);
        this.id = row.get("nKey", Integer.class);
        this.aniworldUrl = row.get("szAniworldURL", String.class);
        this.videoUrl = row.get("szVideoURL", String.class);
        this.title = row.get("szTitle", String.class);
        this.downloaded = row.get("bLoaded", Integer.class) == 1;
        this.isDirty = false;

        if (aniworldUrl != null && aniworldUrl.isEmpty()) aniworldUrl = null;
        if (videoUrl != null && videoUrl.isEmpty()) videoUrl = null;
    }

    public void writeToDB(DataBaseInterface db, int seasonLink) {
        if (!isDirty)
            return;

        log.debug("Writing episode to db: " + this);
        db.executeSafe("call addEpisode(?, ?, ?, ?, ?, ?, ?)",
                id,
                seasonLink,
                episodeNumber,
                title,
                aniworldUrl == null ? "" : aniworldUrl,
                videoUrl == null ? "" : videoUrl,
                downloaded ? 1 : 0);
        isDirty = false;
    }

    public void loadVideoURL(int languageId) {
        loadVideoURL(languageId, null);
    }

    public void loadVideoURL(int languageId, Runnable then) {
        if (videoUrl != null) return; // No need to parse, already parsed
        if (aniworldUrl == null) {
            log.error("Cant load videoURL because url is null");
            return;
        }
        ExecutorTask task = AniworldHelper.resolveVideoURL(this, languageId);
        if (then != null)
            task.onComplete(then);
        isDirty = true;
    }

    public void setDownloaded(boolean downloaded) {
        if (this.downloaded == downloaded)
            return;

        this.downloaded = downloaded;
        isDirty = true;
    }

}
