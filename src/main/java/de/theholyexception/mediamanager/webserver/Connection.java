package de.theholyexception.mediamanager.webserver;

import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketServer;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Connection implements Runnable {
    private final Socket socket;
    private DataInputStream is;
    private DataOutputStream os;
    private WebSocketServer clientEndpoint;
    private final ByteArrayOutputStream readLineBuffer = new ByteArrayOutputStream(32);
    private final String webroot;

    private final WebServer.Configuration webServerConfiguration;

    private static final Map<String, String> mediaMap = new HashMap<>();

    static {
        // Image
        mediaMap.put("gif", "image/gif");
        mediaMap.put("jpg", "image/jpeg");
        mediaMap.put("png", "image/png");
        mediaMap.put("svg", "image/svg+xml");

        // Application
        mediaMap.put("json", "application/json");
        mediaMap.put("xml", "application/xml");
        mediaMap.put("ogg", "application/ogg");
        mediaMap.put("pdf", "application/pdf");
        mediaMap.put("zip", "application/zip");

        // Audio
        mediaMap.put("wav", "audio/x-wav");

        // Text
        mediaMap.put("css", "text/css");
        mediaMap.put("csv", "text/csv");
        mediaMap.put("html", "text/html");
        mediaMap.put("js", "text/javascript");
        mediaMap.put("txt", "text/plain");

        // Video
        mediaMap.put("mp4", "video/mp4");
        mediaMap.put("mkv", "video/x-matroska");
    }


    public Connection(Socket socket, WebServer.Configuration configuration) {
        this.socket = socket;
        this.webroot = configuration.webroot();
        this.webServerConfiguration = configuration;
        this.run();
    }

    @Override
    public void run() {
        try {
            this.is = new DataInputStream(socket.getInputStream());
            this.os = new DataOutputStream(socket.getOutputStream());

            String[] arguments = readLine().split(" ");
            Map<String,String> headers = readHeaders();

            // Check if we have a websocket
            if (headers.containsKey("Upgrade") && headers.get("Upgrade").equals("websocket")) {

                // If we don't have a valid handler, then the user is just too early in the initialization phase
                if (webServerConfiguration.receptionist() == null) {
                    write503();
                    return;
                }
                // Upgrading the socket to websocket and handles it in the corresponding handler
                clientEndpoint = new WebSocketServer(socket, webServerConfiguration.receptionist(), headers);
            }
            // Else handle it as normal http request
            else {
                handleHttpRequest(arguments);
            }

        } catch (IOException ex) {
            log.error("Failed to process web request", ex);
        }
    }

    public void sendWebsocket(String data) {
        if (clientEndpoint == null) return;
        clientEndpoint.send(data);
    }

    /**
     * Handles the incoming request as HTTP Request
     * @param arguments http arguments
     * @throws IOException when you do something stupid
     */
    private void handleHttpRequest(String[] arguments) throws IOException {
        String methode = arguments[0];
        String path = arguments[1];

        try {
            if (methode.equals("GET")) {
                // If we don't find and '.' in the path, we guess that no file is explicit specified
                // And we default to 'index.html'
                if (!path.contains(".")) {
                    // Hardcode the root index html (because angular does the rooting)
                    path = "index.html";
                }

                // Getting the media type for the response header
                String type = mediaMap.get(path.split("\\.")[1]);
                if (type == null) type = "text/plain";

                // Checking if the file exists, else send an 404 Not Found
                File file = new File(webroot, path);
                if (!file.exists()) {
                    write404();
                    return;
                }

                // Sending the file to the client
                writeFile(file, type);
            }
        } catch (Exception ex) {
            write400();
        } finally {
            os.flush();
            os.close();
            is.close();
            socket.close();
        }
    }

    /**
     * Writes the HTTP Header to the outputstream
     * @param mediaType MediaType to send - <a href="https://www.iana.org/assignments/media-types/media-types.xhtml">Documentation</a>
     * @param contentLength Content Length
     * @throws IOException if you do something stupid
     */
    private void writeHeader(String mediaType, long contentLength) throws IOException {
        // Note: Do not remove the spacing line below Content-Length, this is mandatory!!!!!
        os.write(String.format("""
                HTTP/1.1 200 OK
                Content-Type: %s
                Content-Length: %s

                """,mediaType, contentLength).getBytes());
    }

    /**
     * Writes the given file to the output stream including the header of the media type
     * @param file File to send to the client
     * @param type Media Type of the File
     * @throws IOException if you do something stupid
     */
    private void writeFile(File file, String type) throws IOException {
        try (BufferedInputStream lBis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] data = lBis.readAllBytes();
            writeHeader(type, data.length);
            os.write(data);
        }
    }

    private void write400() throws IOException {
        os.write((
                """
                HTTP/1.1 400 Bad Request
                Content-Type: text/html

                The Server was unable to parse or handle your http request.
                """).getBytes());
    }

    private void write404() throws IOException {
        os.write((
                """
                HTTP/1.1 404 Not Found
                Content-Type: text/html

                The File you requested does not exist.
                """).getBytes());
    }

    private void write503() throws IOException {
        os.write((
                """
                HTTP/1.1 503 Service Unavailable
                Content-Type: text/html

                WebSocket is not initialized yet
                """).getBytes());
    }

    /**
     * Reads a single line from the input stream
     * @return single line from the input stream
     * @throws IOException if you do something stupid
     */
    private String readLine() throws IOException {
        int chr;
        ByteArrayOutputStream baos = this.readLineBuffer;
        while(((chr = is.read()) != '\n') && chr != -1){
            baos.write(chr);
        }

        if(baos.size() == 0)
            return "";

        String out = baos.toString();
        baos.reset();

        if(out.charAt(out.length() - 1) == '\r')
            return out.substring(0, out.length() - 1);

        return out;
    }

    /**
     * Reads the headers from a client request
     * @return Key-Value Map of the header parameters
     * @throws IOException if you do something stupid
     */
    private Map<String, String> readHeaders() throws IOException {
        Map<String, String> result = new HashMap<>();
        String line;
        do {
            line = readLine();
            String[] a = line.split(": ");
            if (a.length >= 2) {
                result.put(a[0].trim(), a[1].trim());
            }
        } while (!line.isEmpty());
        return result;
    }

}
