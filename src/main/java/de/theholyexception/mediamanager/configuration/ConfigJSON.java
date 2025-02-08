package de.theholyexception.mediamanager.configuration;

import com.wsojka.CuteJson;
import de.theholyexception.holyapi.datastorage.file.FileConfiguration;
import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.json.JSONReader;
import de.theholyexception.holyapi.util.NotImplementedException;
import me.kaigermany.ultimateutils.StaticUtils;

import java.io.*;

public class ConfigJSON implements FileConfiguration {

	private final File file;
	private JSONObjectContainer json;

	public ConfigJSON(File file) {
		this.file = file;
	}

	@Override
	public void saveConfig() {
		if (json == null) throw new IllegalStateException("JSON content not loaded!");
		String formattedJson = CuteJson.format(json.toString());
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(formattedJson.getBytes());
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public <T> T getValue(Object path, Class<T> clazz) {throw new NotImplementedException();}
	@Override
	public <T> T getValue(Object path, T defaultValue, Class<T> clazz) {throw new NotImplementedException();}
	@Override
	public void setValue(Object path, Object value) {throw new NotImplementedException();}
	@Override
	public boolean createNewIfNotExists() {
		if (file.exists()) return false;
		createNew();
        return true;
    }
	@Override
	public boolean createNewIfNotExists(InputStream stream) {
		if (file.exists()) return false;
		try {
			boolean result = file.createNewFile();
			if (!result) throw new IllegalStateException("File cant be created!");

			String content = new String(StaticUtils.readAllBytes(stream));
			json = (JSONObjectContainer) JSONReader.readString(content);

			saveConfig();
			return true;
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	@Override
	public void createNew() {
		try {
			boolean result = file.createNewFile();
			if (!result) throw new IllegalStateException("File cant be created!");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void createNew(InputStream stream) {throw new NotImplementedException();}

	@Override
	public File getFile() {
		return file;
	}

	@Override
	public void loadConfig() {
		if (file == null) return;
		json = (JSONObjectContainer) JSONReader.readFile(file);
	}

	public JSONObjectContainer getJson() {
		return json;
	}
}
