package de.theholyexception.mediamanager.settings;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.configuration.ConfigJSON;
import de.theholyexception.mediamanager.models.SettingMetadata;

import java.util.*;

public class Settings {

    public static final Map<String, String> SETTING_DATA = Collections.synchronizedMap(new HashMap<>());
    public static final Map<String, SettingProperty<?>> SETTING_PROPERTIES = Collections.synchronizedMap(new HashMap<>());

    public static <T> SettingProperty<T> getSettingProperty(String setting, T defaultValue) {
        if (SETTING_PROPERTIES.containsKey(setting)) {
            SettingProperty<?> property = SETTING_PROPERTIES.get(setting);
            if (!property.getArgumentType().getClass().equals(defaultValue.getClass()))
                throw new IllegalStateException("There is already a Setting registered with another type");
            return (SettingProperty<T>) property;
        }

        JSONObjectContainer settingElement = systemSettings.getObjectContainer(setting);
        Boolean forClient = settingElement.get("forClient", Boolean.class);

        SettingMetadata metadata = new SettingMetadata(setting, forClient != null && forClient);
        SettingProperty<T> property = new SettingProperty<>(metadata) {
            @Override
            public void setValue(T value) {
                SETTING_DATA.put(setting, value.toString());
                systemSettings.set(setting, value.toString());
                super.setValue(value);
            }
        };

        T systemSettingValue = settingElement.get("value", defaultValue, (Class<T>) defaultValue.getClass());
        if (systemSettingValue != null)
            property.setValue(systemSettingValue);
        else
            property.setValue(defaultValue);
        SETTING_PROPERTIES.put(setting, property);
        return property;
    }

    private static JSONObjectContainer systemSettings;

    public static void init(ConfigJSON configJSON) {
        systemSettings = configJSON.getJson().getObjectContainer("systemSettings");
    }

}
