package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.datastorage.sql.Result;
import de.theholyexception.holyapi.datastorage.sql.Row;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.Utils;
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
    @Getter
    private Season season;

    public Episode(Element element) {
        this.episodeNumber = Integer.parseInt(element.text());
        this.id = Integer.parseInt(element.attr("data-episode-id"));
        this.videoUrl = null;
        this.aniworldUrl = AniworldHelper.ANIWORLD_URL + element.attr("href");
        this.title = element.attr("title");
        this.isDirty = true;
    }

    public Episode(Row row, Season season) {
        this.episodeNumber = row.get("nEpisodeNumber", Integer.class);
        this.id = row.get("nKey", Integer.class);
        this.aniworldUrl = row.get("szAniworldURL", String.class);
        this.videoUrl = row.get("szVideoURL", String.class);
        this.title = row.get("szTitle", String.class);
        this.downloaded = row.get("bLoaded", Integer.class) == 1;
        String szLanguageIDs = row.get("szLanguageIds", String.class);
        if (szLanguageIDs != null && !szLanguageIDs.isEmpty())
            this.languageIds = Utils.stringToIntgerList(szLanguageIDs);

        if (aniworldUrl != null && aniworldUrl.isEmpty()) aniworldUrl = null;
        if (videoUrl != null && videoUrl.isEmpty()) videoUrl = null;

        this.season = season;
        this.isDirty = false;
    }

    public static void loadFromDB(DataBaseInterface db, Season season) {
        Result rs = db.getResult(String.format("""
                        select *
                        from episode
                        where nSeasonLink = %s
                        """, season.getId()));
        for (Row row : rs.getTable(0).getRows()) {
            Episode e = new Episode(row, season);
            season.addEpisode(e);
        }
    }

    public List<Integer> getLanguageIdsRaw() {
        return languageIds;
    }

    public List<Integer> getLanguageIds() {
        if (languageIds == null) {
            System.out.println("getLanguageIDs");
            AniworldHelper.resolveEpisodeLanguages(this);
            AniworldHelper.urlResolver.awaitGroup(883855723);
            this.isDirty = true;
        }
        return languageIds;
    }

    public void activeScanLanguageIDs() {
        if (languageIds != null && season != null) {
            Anime a = season.getAnime();
            if (a == null)
                throw new IllegalStateException("Season has no anime");
            log.debug("Episode " + a.getTitle() + " - " + title + " scans for language");

            AniworldHelper.resolveEpisodeLanguages(this);
            AniworldHelper.urlResolver.awaitGroup(883855723);
            this.isDirty = true; // TODO check if changes has occured, then set this to true
        }

    }

    public void writeToDB(DataBaseInterface db, int seasonLink) {
        if (!isDirty)
            return;

        log.debug("Writing episode to db: " + this);
        db.executeSafe("call addEpisode(?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                seasonLink,
                episodeNumber,
                title,
                aniworldUrl == null ? "" : aniworldUrl,
                videoUrl == null ? "" : videoUrl,
                downloaded ? 1 : 0,
                Utils.intergerListToString(languageIds));
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
