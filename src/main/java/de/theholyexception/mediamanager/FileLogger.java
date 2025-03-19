package de.theholyexception.mediamanager;

import lombok.Getter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class FileLogger {

	private static Map<String, FileLogger> fileLoggerCache = new HashMap<>();

	public static synchronized FileLogger getLogger(String name) {
		return fileLoggerCache.computeIfAbsent(name, k -> new FileLogger(name));
	}

	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final File loggerFolder = new File("./logs");
	@Getter
	private final File file;
	private BufferedOutputStream bos;

	public FileLogger(String name) {
		file = new File(loggerFolder, name);
		try {
			if (!file.exists()) {
				file.createNewFile();
			}

			bos = new BufferedOutputStream(new FileOutputStream(file));
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public void log(String message) {
		String time = sdf.format(System.currentTimeMillis());
		String thread = Thread.currentThread().getName();
		String caller = Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName();

		StringBuilder builder = new StringBuilder();
		builder.append(time).append("\t[").append(thread).append("]\t[").append(caller).append("]\t").append(message).append("\n");
		try {
			bos.write(builder.toString().getBytes());
			bos.flush();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

}
