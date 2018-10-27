package de.mein;

/**
 * German word for 'locomotive' which pronounces like 'log' in English.
 * Does the obvious thing.
 */
public class Lok {

    private static LokImpl impl = new LokImpl().setup(0, true);

    public static void setLokImpl(LokImpl impl) {
        Lok.impl = impl;
    }

    public static void debug(Object msg) {
        impl.debug(msg);
    }

    public static void error(Object msg) {
        impl.error(msg);
    }

    public static void warn(Object msg) {
        impl.warn(msg);
    }

    public static void info(Object msg) {
        impl.info(msg);
    }



    public static void setLokListener(LokImpl.LokListener listener) {
        impl.setLokListener(listener);
    }

    public static void setupSaveLok(int lines, boolean timeStamp) {
        impl.setup(lines, timeStamp);
    }

    public static String[] getLines() {
        return impl.getLines();
    }

    public static boolean isLineStorageActive() {
        return impl.isLineStorageActive();
    }

    public static void devOnLineMatches(String line, Runnable r) {
        impl.devOnLineMatches(line,r);
    }
}
