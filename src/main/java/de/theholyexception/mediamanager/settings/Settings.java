package de.theholyexception.mediamanager.settings;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.configuration.ConfigJSON;
import de.theholyexception.mediamanager.models.SettingMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class Settings {

    private Settings() {}

    protected static final Map<String, String> SETTING_DATA = Collections.synchronizedMap(new HashMap<>());
    public static final Map<String, SettingProperty<?>> SETTING_PROPERTIES = Collections.synchronizedMap(new HashMap<>());

    @SuppressWarnings("unchecked")
    public static <T> SettingProperty<T> getSettingProperty(String setting, T defaultValue, String configPath) {
        // Check for duplicates, and returns already registered settings
        if (SETTING_PROPERTIES.containsKey(setting)) {
            SettingProperty<?> property = SETTING_PROPERTIES.get(setting);
            if (!property.getArgumentType().getClass().equals(defaultValue.getClass()))
                throw new IllegalStateException("There is already a Setting registered with another type");
            return (SettingProperty<T>) property;
        }
        
        // Reading data from the configuration file
        JSONObjectContainer settingElement;
        Boolean forClient = false;
        if (configPath != null) {
            settingElement = configJSON.getJson()
                    .getObjectContainer(configPath)
                    .getObjectContainer(setting);
            forClient = settingElement.get("forClient", Boolean.class);
        } else {
            settingElement = null;
        }

        // Creating metadata for the setting
        // forClient for example indicates if the setting should be provided to the web clients
        SettingMetadata metadata = new SettingMetadata(setting, forClient != null && forClient);
        SettingProperty<T> property = new SettingProperty<>(metadata) {
            @Override
            public void setValue(T value) {
                SETTING_DATA.put(setting, value.toString());
                // Writing the updated setting value to the configuration file
                if (settingElement != null) {
                    settingElement.set("value", value);
                    configJSON.saveConfig();
                }
                // Setting the setting object value
                super.setValue(value);
            }
        };

        if (settingElement != null) {
            T systemSettingValue = settingElement.get("value", defaultValue, (Class<T>) defaultValue.getClass());
            property.setValue(systemSettingValue);
        }
        else
            property.setValue(defaultValue);
        SETTING_PROPERTIES.put(setting, property);
        log.info("Setting Loaded: " + property);
        return property;
    }

    private static ConfigJSON configJSON;

    public static void init(ConfigJSON configJSON) {
        Settings.configJSON = configJSON;
    }

}
