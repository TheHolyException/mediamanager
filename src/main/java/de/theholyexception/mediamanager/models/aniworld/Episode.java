package de.theholyexception.mediamanager.models.aniworld;

import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.util.ExecutorHandler;
import de.theholyexception.holyapi.util.ExecutorTask;
import de.theholyexception.mediamanager.AniworldHelper;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executors;

@ToString
@Slf4j
public class Episode {

    @Getter
    private int id = -1;
    @Getter
    private int episodeNumber;
    @Getter
    private String url;
    @Getter
    private String videoUrl;
    @Getter
    private String title;
    @Getter
    private boolean downloaded = false;

    @Getter
    private boolean isDirty = false;

    public static final ExecutorHandler urlResolver = new ExecutorHandler(Executors.newFixedThreadPool(50));

    private Episode(int episodeNumber, int id, String url, String title, boolean isDirty) {
        this.episodeNumber = episodeNumber;
        this.id = id;
        if (url.startsWith("https://voe")) this.videoUrl = url;
        else this.url = url;
        this.title = title;
        this.isDirty = isDirty;
    }

    public static void loadFromDB(DataBaseInterface db, Season season) throws SQLException {
        ResultSet rs = db.executeQuerySafe("""
                        select nKey,
                               nEpisodeNumber,
                               szTitle,
                               szURL,
                               bLoaded
                        from episode
                        where nSeasonLink = ?
                        """, season.getId());
        while (rs.next()) {
            Episode e = new Episode(rs);
            season.addEpisode(e);
        }
        rs.close();
    }

    public static Episode parseFromElement(Element element) {
        return new Episode(
                Integer.parseInt(element.text()),
                Integer.parseInt(element.attr("data-episode-id")),
                "https://aniworld.to" + element.attr("href"),
                element.attr("title"),
                true
        );
    }

    public Episode(ResultSet rs) throws SQLException {
        this.episodeNumber = rs.getInt("nEpisodeNumber");
        this.id = rs.getInt("nKey");
        String szURL = rs.getString("szURL");
        if (szURL.startsWith("https://aniworld.to"))
            this.url = szURL;
        else
            this.videoUrl = szURL;
        this.title = rs.getString("szTitle");
        this.downloaded = rs.getBoolean("bLoaded");
        this.isDirty = false;
    }

    public void writeToDB(DataBaseInterface db, int seasonLink) {
        if (!isDirty)
            return;

        System.out.println(this);
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
        urlResolver.putTask(new ExecutorTask(() -> {
            try {
                Document document = Jsoup.connect(url).get();
                Elements list = document.select(".row > li");
                for (Element element : list) {
                    if (Integer.parseInt(element.attr("data-lang-key")) != languageId) continue;
                    this.videoUrl = AniworldHelper.getRedirectedURL("https://aniworld.to"+element.attr("data-link-target"));
                    break;
                }

                if (then != null)
                    then.run();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }), 1);
        isDirty = true;
    }

    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
        isDirty = true;
    }

}
