package de.theholyexception.mediamanager.util;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
			log.error("Directory has no files {}", dir.getAbsolutePath());
            return;
        }
        for(File f : files){
            if(f.isDirectory()){
                safeDelete(f);
            } else {
                if (!f.delete()) log.warn("Failed to delete file {}", f.getAbsolutePath());
            }
        }
        if (!dir.delete()) log.warn("Failed to delete dir {}", dir.getAbsolutePath());
    }

    public static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.getBuffer().toString();
    }

    public static String getStackTraceAsString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Simple expression evaluator for mathematical formulas containing the errorCount variable.
     * Supports basic arithmetic operations: +, -, *, /, parentheses
     * @param formula The mathematical formula as a string
     * @param errorCount The current error count to substitute in the formula
     * @return The evaluated result
     */
    public static long evaluateFormula(String formula, int errorCount) {
        if (formula == null || formula.trim().isEmpty()) {
            throw new IllegalArgumentException("Formula cannot be null or empty");
        }

        // Replace the errorCount variable with its actual value
        String expression = formula.replace("errorCount", String.valueOf(errorCount));

        // Basic validation - ensure only allowed characters
        if (!expression.matches("[0-9+\\-*/()\\s.]+")) {
            throw new IllegalArgumentException("Formula contains invalid characters");
        }

        try {
            // Use JavaScript engine for expression evaluation
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("nashorn");

            if (engine == null) {
                throw new RuntimeException("Nashorn JavaScript engine not available");
            }

            Object result = engine.eval(expression);

            if (result instanceof Number number) {
                // Convert seconds to milliseconds for internal use
                return number.longValue() * 1000L;
            } else {
                throw new IllegalArgumentException("Formula did not evaluate to a number: " + result);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to evaluate formula: " + expression, e);
        }
    }

    public static Optional<Integer> parseInteger(String str) {
        try {
            return Optional.of(Integer.parseInt(str));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

}
