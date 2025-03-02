package de.theholyexception.mediamanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class for working with resources in both development (IntelliJ) and
 * production (JAR) environments.
 */
public class ResourceHelper {

    /**
     * Lists all files in a resource directory (non-recursive).
     *
     * @param resourcePath Path to the resource directory (e.g., "templates", "images")
     * @return List of file names in the directory
     * @throws IOException If there's an error accessing the resources
     */
    public static List<String> listResourceFiles(String resourcePath) throws IOException {
        List<String> fileNames = new ArrayList<>();

        // Normalize the path to ensure consistent behavior
        String normalizedPath = normalizePath(resourcePath);

        // Get the classloader resource URL
        URL resource = ResourceHelper.class.getResource(normalizedPath);
        if (resource == null) {
            throw new IOException("Resource not found: " + normalizedPath);
        }

        // Handle different environments (file system vs JAR)
        if (resource.getProtocol().equals("file")) {
            // Running from file system (likely IntelliJ)
            try {
                Path dirPath = Paths.get(resource.toURI());
                fileNames = Files.list(dirPath)
                        .filter(path -> !Files.isDirectory(path)) // Only files, not directories
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toList());
            } catch (URISyntaxException e) {
                throw new IOException("Failed to convert URL to URI", e);
            }
        } else if (resource.getProtocol().equals("jar")) {
            // Running from JAR
            String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
            try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + jarPath), Collections.emptyMap())) {
                Path path = fs.getPath(normalizedPath);
                fileNames = Files.list(path)
                        .filter(p -> !Files.isDirectory(p)) // Only files, not directories
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
            }
        } else {
            // Alternative approach for other environments using classloader
            try {
                // Remove leading slash for getResources
                String searchPath = normalizedPath;
                if (searchPath.startsWith("/")) {
                    searchPath = searchPath.substring(1);
                }

                Enumeration<URL> resources = ResourceHelper.class.getClassLoader().getResources(searchPath);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    if (url.getProtocol().equals("file")) {
                        File file = new File(url.toURI());
                        for (File f : file.listFiles()) {
                            if (!f.isDirectory()) {
                                fileNames.add(f.getName());
                            }
                        }
                    }
                }
            } catch (URISyntaxException e) {
                throw new IOException("Failed to convert URL to URI", e);
            }
        }

        return fileNames;
    }

    /**
     * Lists all files in a resource directory recursively.
     * Returns file paths relative to the provided resourcePath.
     *
     * @param resourcePath Path to the resource directory (e.g., "templates", "images")
     * @return List of relative file paths including files in subdirectories
     * @throws IOException If there's an error accessing the resources
     */
    public static List<String> listResourceFilesRecursive(String resourcePath) throws IOException {
        List<String> filePaths = new ArrayList<>();

        // Normalize the path to ensure consistent behavior
        String normalizedPath = normalizePath(resourcePath);

        // Get the classloader resource URL
        URL resource = ResourceHelper.class.getResource(normalizedPath);
        if (resource == null) {
            throw new IOException("Resource not found: " + normalizedPath);
        }

        // Handle different environments (file system vs JAR)
        if (resource.getProtocol().equals("file")) {
            // Running from file system (likely IntelliJ)
            try {
                Path rootPath = Paths.get(resource.toURI());
                collectFilesRecursive(rootPath, rootPath, filePaths);
            } catch (URISyntaxException e) {
                throw new IOException("Failed to convert URL to URI", e);
            }
        } else if (resource.getProtocol().equals("jar")) {
            // Running from JAR
            String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
            String pathInsideJar = resource.getPath().substring(resource.getPath().indexOf("!") + 1);

            try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + jarPath), Collections.emptyMap())) {
                Path rootPath = fs.getPath(pathInsideJar);
                collectFilesRecursive(rootPath, rootPath, filePaths);
            }
        } else {
            // If we can't do it directly, we'll try to scan the classloader
            scanClasspathResourcesRecursive(resourcePath, filePaths);
        }

        return filePaths;
    }

    /**
     * Helper method to collect files recursively from a directory.
     *
     * @param rootPath The root path to calculate relative paths from
     * @param currentPath The current path being explored
     * @param filePaths List to collect the file paths
     * @throws IOException If there's an error accessing files
     */
    private static void collectFilesRecursive(Path rootPath, Path currentPath, List<String> filePaths) throws IOException {
        Files.list(currentPath).forEach(path -> {
            try {
                if (Files.isDirectory(path)) {
                    collectFilesRecursive(rootPath, path, filePaths);
                } else {
                    // Get path relative to the root
                    String relativePath = rootPath.relativize(path).toString();
                    filePaths.add(relativePath);
                }
            } catch (IOException e) {
                // Log or handle the exception as needed
                System.err.println("Error accessing path: " + path + " - " + e.getMessage());
            }
        });
    }

    /**
     * Alternative method to scan classpath resources recursively.
     * This is a fallback for when direct filesystem access isn't available.
     *
     * @param resourcePath The resource path to scan
     * @param filePaths List to collect the file paths
     */
    private static void scanClasspathResourcesRecursive(String resourcePath, List<String> filePaths) {
        try {
            // Remove leading slash for getResources
            String searchPath = resourcePath;
            if (searchPath.startsWith("/")) {
                searchPath = searchPath.substring(1);
            }

            // Unfortunately, there's no easy built-in way to recursively scan resources in a JAR
            // This is a very simplified implementation that may not work in all environments
            ClassLoader classLoader = ResourceHelper.class.getClassLoader();

            // For demonstration purposes - in a real implementation you might need
            // to use reflection or a third-party library to scan the classpath recursively
            // This implementation assumes you know the structure of your resources

            // Example implementation for a known structure:
            // 1. List the direct resources
            Enumeration<URL> resources = classLoader.getResources(searchPath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if (url.getProtocol().equals("file")) {
                    File file = new File(url.toURI());
                    collectFilesFromDirectory(file, resourcePath, "", filePaths);
                }
            }

            // Note: For JAR resources, a more sophisticated approach would be needed
            // Libraries like Reflections (https://github.com/ronmamo/reflections) can help

        } catch (IOException | URISyntaxException e) {
            System.err.println("Error scanning classpath resources: " + e.getMessage());
        }
    }

    /**
     * Helper method to collect files from a directory recursively.
     *
     * @param directory The directory to scan
     * @param basePath The base resource path
     * @param currentRelativePath Current relative path
     * @param filePaths List to collect the file paths
     */
    private static void collectFilesFromDirectory(File directory, String basePath, String currentRelativePath, List<String> filePaths) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String relativePath = currentRelativePath.isEmpty() ? file.getName() : currentRelativePath + "/" + file.getName();

                if (file.isDirectory()) {
                    collectFilesFromDirectory(file, basePath, relativePath, filePaths);
                } else {
                    filePaths.add(relativePath);
                }
            }
        }
    }

    /**
     * Gets an InputStream for a resource file.
     *
     * @param resourcePath Path to the resource file (e.g., "templates/template.html")
     * @return InputStream for the resource
     * @throws IOException If the resource cannot be found or accessed
     */
    public static InputStream getResourceAsStream(String resourcePath) throws IOException {
        // Normalize the path (add leading slash if missing)
        String normalizedPath = resourcePath;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        // Get the stream
        InputStream stream = ResourceHelper.class.getResourceAsStream(normalizedPath);

        if (stream == null) {
            // Try without the leading slash as a fallback
            stream = ResourceHelper.class.getClassLoader().getResourceAsStream(
                    normalizedPath.startsWith("/") ? normalizedPath.substring(1) : normalizedPath);
        }

        if (stream == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }

        return stream;
    }

    /**
     * Utility method to read a resource file to String.
     *
     * @param resourcePath Path to the resource file
     * @return The content of the resource as a String
     * @throws IOException If the resource cannot be found or read
     */
    public static String readResourceAsString(String resourcePath) throws IOException {
        try (InputStream is = getResourceAsStream(resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    /**
     * Helper method to normalize a resource path.
     *
     * @param path The path to normalize
     * @return Normalized path
     */
    private static String normalizePath(String path) {
        String normalizedPath = path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }
        return normalizedPath;
    }
}