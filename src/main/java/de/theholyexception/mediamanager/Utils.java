package de.theholyexception.mediamanager;

import jdk.jshell.execution.Util;
import me.kaigermany.ultimateutils.classextensioninternals.IO;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

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
            ex.printStackTrace();
        }
    }

    public static byte[] loadBytes(String filename) throws IOException {
        URL url = new URL(filename);
        DataInputStream stream = new DataInputStream(url.openStream());
        return stream.readAllBytes();
    }

    public static void sendNotify(String content) {
        try {
            Runtime.getRuntime().exec("curl -d \"MediaManager - "+content+"\" 10.0.1.1:60001/main");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
