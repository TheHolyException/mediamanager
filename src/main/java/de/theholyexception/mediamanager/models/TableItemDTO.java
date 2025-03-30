package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.util.ExecutorTask;
import lombok.Getter;
import lombok.Setter;
import me.kaigermany.downloaders.Downloader;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class TableItemDTO implements Comparable<TableItemDTO> {

    private static final AtomicInteger counter = new AtomicInteger(0);

    private final long created;
    private final String state;
    private final String url;
    private final UUID uuid;
    private final JSONObjectContainer jsonObject;
    @Setter
    private ExecutorTask task;
    @Setter
    private Downloader downloader;
    @Setter
    private boolean isDeleted = false;
    @Setter
    private boolean isRunning = false;
    @Setter
    private boolean isFailed = false;
    private final int sortIndex = counter.getAndIncrement();


    public TableItemDTO(JSONObjectContainer content) {
        created = content.get("created", Long.class);
        state = content.get("state", String.class);
        url = content.get("url", String.class);
        uuid = UUID.fromString(content.get("uuid", String.class));
        jsonObject = content;
    }

    /*
        a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object
     */
    @Override
    public int compareTo(TableItemDTO o) {
        Long l1 = (long) sortIndex;
        Long l2 = (long) o.sortIndex;
        return l1.compareTo(l2);
    }
}
