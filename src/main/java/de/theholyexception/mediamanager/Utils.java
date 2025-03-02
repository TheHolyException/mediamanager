package de.theholyexception.mediamanager;

import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.StaticUtils;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

@Slf4j
public class Utils {
    public static void saveBytes(String filename, byte[] data) {
        try {
            File file = new File(filename);
            if (!file.exists()) file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.flush();
            fos.close();
        } catch (IOException ex) {
            log.error("Failed to save bytes", ex);
        }
    }

    public static byte[] loadBytes(String filename) throws IOException {
        URL url = new URL(filename);
        try (DataInputStream stream = new DataInputStream(url.openStream())) {
            return stream.readAllBytes();
        }
    }

    public static void sendNotify(String content) {
        try {
            Runtime.getRuntime().exec("curl -d \"MediaManager - "+content+"\" 10.0.1.1:60001/main");
        } catch (Exception ex) {
            log.error("Failed to send notification", ex);
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            log.error("Interrupted Fail", ex);
        }
    }

}
