package de.theholyexception.mediamanager;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static de.theholyexception.mediamanager.Utils.saveBytes;

public class AniWorldParser {

    public static
    String url = "https://aniworld.to/anime/stream/that-time-i-got-reincarnated-as-a-slime/staffel-2";

    static List<String> getLinks(String url, int language) {
        ArrayList<String> links = readPageUrls(url);
        return readVideoTargetLinksFromPages(links, language);
    }

    private static ArrayList<String> readVideoTargetLinksFromPages(ArrayList<String> in, int language){
        ArrayList<String> out = new ArrayList<String>(in.size());
        String serverName = in.get(0).substring(0, in.get(0).indexOf('/', 8) + 1);
        for(String s : in){
            //String s = in.get(0);
            String page = new String(pingServer_simple(s));
            //saveBytes("page.html", page.getBytes());
            page = page.split("<div class\\=\"inSiteWebStream\">")[1];
            page = page.split("data-lang-key\\=\""+language+"\"")[1];
            s = page.split("data-link-target\\=\"")[1].split("\"")[0];
            if(!s.startsWith("http")) s = serverName + s;
            out.add(pingServer_and_extract_redirect(s));
        }
        return out;
    }

    private static ArrayList<String> readPageUrls(String base_link){
        ArrayList<String> links = new ArrayList<String>();
        String serverName = base_link.substring(0, base_link.indexOf('/', 8) + 1);
        String page = new String(pingServer_simple(base_link));
        saveBytes("overview.html", page.getBytes());
        String[] a = page.split("<meta itemprop\\=\"episodeNumber\"");
        for(int i=1; i<a.length; i++){
            String b = a[i].split("href\\=\"")[1].split("\">")[0];
            if(!b.startsWith("http")) b = serverName + b;
            links.add(b);
        }
        return links;
    }

    private static byte[] pingServer_simple(String a) {
        try {
            URL url = new URL(a);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Language", "de,en-US;q=0.7,en;q=0.3");
            BufferedInputStream dis = new BufferedInputStream(conn.getInputStream());
            ByteArrayOutputStream baos = new  ByteArrayOutputStream();
            int chr = -1;
            byte[] buffer = new byte[1024];
            try {
                while ((chr = dis.read(buffer)) > 0) baos.write(buffer, 0, chr);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
            dis.close();
            return baos.toByteArray();
        }
        catch(Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private static String pingServer_and_extract_redirect(String a) {
        try {
            URL url = new URL(a);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false); // Disable automatic redirection
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Language", "de,en-US;q=0.7,en;q=0.3");
            BufferedInputStream dis = new BufferedInputStream(conn.getInputStream());
            ByteArrayOutputStream baos = new  ByteArrayOutputStream();
            int chr = -1;
            byte[] buffer = new byte[1024];
            try {
                while ((chr = dis.read(buffer)) > 0) baos.write(buffer, 0, chr);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
            dis.close();
            String loc = "";
            for (java.util.Map.Entry<String, java.util.List<String>> entry : conn.getHeaderFields().entrySet()) {
                String key = entry.getKey();
                java.util.List<String> values = entry.getValue();
                if(key == null) continue;
                if(key.equalsIgnoreCase("location")){
                    loc = values.get(0);
                }
            }
            return loc;
        }
        catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}







