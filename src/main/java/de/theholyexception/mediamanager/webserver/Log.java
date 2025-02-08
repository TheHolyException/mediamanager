package de.theholyexception.mediamanager.webserver;

public class Log {

    public static void e(String a) {
        String callerClass = null;
    }

    private static String getCallerClass() {
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            if (!ste.getClassName().equals(Log.class.getName()) && ste.getClassName().indexOf("java.lang.Thread") != 0) {
                return ste.getClassName();
            }
        }
        return "NOCALLERCLASS";
    }

}
