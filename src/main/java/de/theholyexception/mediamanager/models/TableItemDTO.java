package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import lombok.Getter;

import java.util.UUID;

public class TableItemDTO implements Comparable<TableItemDTO> {

    @Getter
    private long created;

    @Getter
    private String state;

    @Getter
    private String url;

    @Getter
    private UUID uuid;

    @Getter
    private JSONObjectContainer jsonObject;


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
