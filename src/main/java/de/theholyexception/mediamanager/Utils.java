package de.theholyexception.mediamanager;

import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Utils {
    public static void saveBytes(String filename, byte[] data) {
        try {
            File file = new File(filename);
            if (!file.exists() && !file.createNewFile()) {
                log.error("Failed to create file");
                return;
            }
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
        }
    }

    public static String intergerListToString(List<Integer> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Integer i : list) {
            sb.append(i);
            sb.append(",");
        }
        return sb.substring(0, sb.length() - 1);
    }

    public static List<Integer> stringToIntgerList(String string) {
        String[] split = string.split(",");
        List<Integer> list = new ArrayList<>();
        for (String s : split) {
            if (s.isEmpty()) continue;
            list.add(Integer.parseInt(s));
        }
        return list;
    }

    public static String escape(String string) {
        return string.replaceAll("[^a-zA-Z0-9-_.]", "_");
    }

    public static void safeDelete(File dir) {
        if (dir == null) {
            log.error("Directory is null");
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            log.error("Directory has no files " + dir.getAbsolutePath());
            return;
        }
        for(File f : files){
            if(f.isDirectory()){
                safeDelete(f);
            } else {
                if (!f.delete()) log.warn("Failed to delete file " + f.getAbsolutePath());
            }
        }
        if (!dir.delete()) log.warn("Failed to delete dir " + dir.getAbsolutePath());
    }

}
