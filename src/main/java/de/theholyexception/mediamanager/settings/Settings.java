package de.theholyexception.mediamanager.settings;

import de.theholyexception.holyapi.datastorage.file.ConfigJSON;
import de.theholyexception.holyapi.datastorage.file.FileConfiguration;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.mediamanager.util.InitializationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
        if (configPath != null) {
            settingElement = configJSON.getJson()
                    .getObjectContainer(configPath, new JSONObjectContainer())
                    .getObjectContainer(setting, new JSONObjectContainer());
            if (settingElement == null) {
                throw new InitializationException("Setting " + setting + " not found in configuration file", "Setting " + setting + " not found in configuration file");
            }
        } else {
            settingElement = null;
        }

        // Creating metadata for the setting
        // forClient for example indicates if the setting should be provided to the web clients
        SettingMetadata metadata = new SettingMetadata(setting);
        SettingProperty<T> property = new SettingProperty<>(metadata) {
            @Override
            public void setValue(T value) {
                SETTING_DATA.put(setting, value.toString());
                // Writing the updated setting value to the configuration file
                if (settingElement != null) {
                    settingElement.set("value", value);
                    configJSON.saveConfig(FileConfiguration.SaveOption.PRETTY_PRINT);
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
        log.debug("Setting Loaded: " + property);
        return property;
    }

    private static ConfigJSON configJSON;

    public static void init(ConfigJSON configJSON) {
        Settings.configJSON = configJSON;
    }

}
