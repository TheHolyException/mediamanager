package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import lombok.Getter;

import java.util.UUID;

public class AnimeDTO {

    @Getter
    private final String url;

    @Getter
    private final UUID uuid;

    @Getter
    private final JSONObjectContainer jsonObject;

    public AnimeDTO(JSONObjectContainer content) {
        url = content.get("url", String.class);
        uuid = UUID.fromString(content.get("uuid", String.class));
        jsonObject = content;
    }

}
