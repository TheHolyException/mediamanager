package de.theholyexception.mediamanager.models;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AnimeDTO {

    private final String url;

    private final UUID uuid;

    private final JSONObjectContainer jsonObject;

    public AnimeDTO(JSONObjectContainer content) {
        url = content.get("url", String.class);
        uuid = UUID.fromString(content.get("uuid", String.class));
        jsonObject = content;
    }

}
