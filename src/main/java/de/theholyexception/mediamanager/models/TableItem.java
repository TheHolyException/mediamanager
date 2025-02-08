package de.theholyexception.mediamanager.models;

import lombok.Getter;
import org.json.simple.JSONObject;

import java.util.UUID;

public class TableItem implements Comparable<TableItem> {

    @Getter
    private long created;

    @Getter
    private String state;

    @Getter
    private String url;

    @Getter
    private UUID uuid;

    @Getter
    private JSONObject jsonObject;


    public TableItem(JSONObject content) {
        created = Long.parseLong(content.get("created").toString());
        state = content.get("state").toString();
        url = content.get("url").toString();
        uuid = UUID.fromString(content.get("uuid").toString());
        jsonObject = content;
    }

    /*
        a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified objec
     */
    @Override
    public int compareTo(TableItem o) {
        Long l1 = Long.valueOf(created);
        Long l2 = Long.valueOf(o.created);
        return l1.compareTo(l2);
    }
}
