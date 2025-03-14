package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.util.ExecutorTask;
import lombok.Getter;
import lombok.Setter;
import me.kaigermany.downloaders.Downloader;

import java.util.UUID;

@Getter
public class TableItemDTO implements Comparable<TableItemDTO> {

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


    public TableItemDTO(JSONObjectContainer content) {
        created = content.get("created", Long.class);
        state = content.get("state", String.class);
        url = content.get("url", String.class);
        uuid = UUID.fromString(content.get("uuid", String.class));
        jsonObject = content;
    }

    /*
        a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified objec
     */
    @Override
    public int compareTo(TableItemDTO o) {
        Long l1 = Long.valueOf(created);
        Long l2 = Long.valueOf(o.created);
        return l1.compareTo(l2);
    }
}
