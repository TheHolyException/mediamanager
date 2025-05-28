package de.theholyexception.mediamanager.webserver;

import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketServer;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles individual client connections to the WebServer.
 * This class processes both HTTP requests and WebSocket upgrade requests,
 * delegating to the appropriate handler based on the request type.
 */
@Slf4j
public class Connection implements Runnable {

    private final Socket socket;
    private DataInputStream is;
    private DataOutputStream os;
    private WebSocketServer clientEndpoint;
    private final ByteArrayOutputStream readLineBuffer = new ByteArrayOutputStream(32);
    private final String webroot;
    private final WebServer.Configuration webServerConfiguration;

    /**
     * Maps file extensions to their corresponding MIME types.
     * Used for setting the Content-Type header in HTTP responses.
     */
    private static final Map<String, String> mediaMap = new HashMap<>();

    static {
        // Initialize MIME type mappings for various file types
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


    /**
     * Creates a new Connection to handle a client socket.
     * The connection will be processed immediately on the calling thread.
     *
     * @param socket The client socket to handle
     * @param configuration The web server configuration
     */
    public Connection(Socket socket, WebServer.Configuration configuration) {
        this.socket = socket;
        this.webroot = configuration.webroot();
        this.webServerConfiguration = configuration;
        this.run();
    }

    /**
     * Main connection handling loop.
     * Processes the incoming request, determines if it's a WebSocket upgrade
     * or regular HTTP request, and delegates to the appropriate handler.
     */
    @Override
    public void run() {
        try {
            is = new DataInputStream(socket.getInputStream());
            os = new DataOutputStream(socket.getOutputStream());
        } catch (IOException ex) {
            log.error("Failed to open streams", ex);
            return;
        }

        try {
            String firstLine = readLine();
            if (firstLine == null || firstLine.isEmpty()) {
                return;
            }

            String[] arguments = firstLine.split(" ");
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
            } else {
                handleHttpRequest(arguments);
            }

        } catch (IOException ex) {
            log.error("Failed to process web request", ex);
        }
    }

    /**
     * Processes an HTTP request and sends the appropriate response.
     * Supports GET requests for static files with proper MIME type handling.
     *
     * @param arguments The parsed HTTP request line (method, path, version)
     * @throws IOException if an I/O error occurs while processing the request
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
     * Writes an HTTP 200 OK response header to the output stream.
     * The header includes Content-Type and Content-Length fields.
     *
     * @param mediaType The MIME type of the response body
     * @param contentLength The length of the response body in bytes
     * @throws IOException if an I/O error occurs while writing
     * @see <a href="https://www.iana.org/assignments/media-types/media-types.xhtml">IANA Media Types</a>
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
     * Sends a file to the client with the appropriate HTTP headers.
     * The file is read in chunks and streamed to the client.
     *
     * @param file The file to send
     * @param type The MIME type of the file
     * @throws IOException if an I/O error occurs while reading the file or writing to the client
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
     * Reads a single line of text from the input stream.
     * Handles both \n and \r\n line endings.
     *
     * @return The line read from the input stream, or null if the socket times out
     * @throws IOException if an I/O error occurs while reading
     */
    private String readLine() throws IOException {
        try {
            int chr;
            ByteArrayOutputStream baos = readLineBuffer;
            while (((chr = is.read()) != '\n') && chr != -1) {
                baos.write(chr);
            }

            if (baos.size() == 0)
                return "";

            String out = baos.toString();
            baos.reset();

            if (out.charAt(out.length() - 1) == '\r')
                return out.substring(0, out.length() - 1);

            return out;
        }catch (SocketTimeoutException ignored){
            return null;
        }
    }

    /**
     * Reads and parses HTTP headers from the client request.
     * Headers are read until an empty line is encountered.
     *
     * @return Map of header names to their values
     * @throws IOException if an I/O error occurs while reading
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
