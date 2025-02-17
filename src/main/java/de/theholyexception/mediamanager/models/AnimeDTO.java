package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import lombok.Getter;

import java.util.UUID;

public class AnimeDTO {

    @Getter
    private String url;

    @Getter
    private UUID uuid;

    @Getter
    private JSONObjectContainer jsonObject;

    public AnimeDTO(JSONObjectContainer content) {
        url = content.get("url", String.class);
        uuid = UUID.fromString(content.get("uuid", String.class));
        jsonObject = content;
    }

}
