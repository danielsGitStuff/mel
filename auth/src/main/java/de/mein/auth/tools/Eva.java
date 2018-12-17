package de.mein.auth.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import de.mein.Lok;

/**

 * Currently used for testing. Does nothing if not explicitly turned on by calling enable().
 * Created by xor on 27.08.2017.
 */
public class Eva {
    private final String key;

    public static void enable() {
        ENABLED = true;
        Lok.error("Eva.ENABLED... this is for testing only");
        Lok.error("Eva.ENABLED... this is for testing only");
        Lok.error("Eva.ENABLED... this is for testing only");
        Lok.error("Eva.ENABLED... this is for testing only");
        Lok.error("Eva.ENABLED... this is for testing only");
    }

    public static int getFlagCount(String flag) {
        return flagMap.get(flag);
    }


    public void print() {
        print(null);
    }

    public void print(String appendix) {
        Lok.debug(key + "." + countMap.get(key).get() + (appendix != null ? "." + appendix : ""));
    }

    public interface EvaRun {
        void run(Eva eva, final int count) throws Exception;
    }

    private Eva(String key) {
        this.key = key;
    }

    public Eva out(String msg) {
        Lok.debug("Eva.out: " + msg);
        return this;
    }

    public Eva error() {
        Lok.error("Eva.error");
        return this;
    }

    private static Map<String, AtomicInteger> countMap = new HashMap<>();
    private static Map<String, Integer> flagMap = new HashMap<>();
    private static Semaphore semaphore = new Semaphore(1, true);
    private static boolean ENABLED = false;

    public static void eva() {
        eva(null);
    }

    public static boolean hasFlag(String flag) {
        return flagMap.containsKey(flag);
    }

    /**
     * You can call this method with a certain flag in your code.
     * Eva will remember whether and how often this flag was "called".
     * You can get the results by calling hasFlag() and getFlagCount().
     *
     * @param flag a unique String
     */
    public static void flag(String flag) {
        if (ENABLED) {
            try {
                semaphore.acquire();
                if (!flagMap.containsKey(flag))
                    flagMap.put(flag, 0);
                final int count = flagMap.get(flag) + 1;
                flagMap.put(flag, count);
                semaphore.release();
            } catch (Exception ee) {
                Lok.error(Eva.class.getSimpleName() + ".flag(): Exception!!!");
                ee.printStackTrace();
            }
        }
    }

    /**
     * Remembers the line of code (+ Class + MethodName)you called this method from.
     * run lets you run code to evaluate things. It is called with the number of times this method has been called from the same line of your code.
     *
     * @param run
     */
    public static void eva(EvaRun run) {
        if (ENABLED) {
            try {
                semaphore.acquire();
                String key;
                StackTraceElement trace = Thread.currentThread().getStackTrace()[2];
                key = trace.getClassName() + "/" + trace.getMethodName() + "/" + trace.getLineNumber();
                if (!countMap.containsKey(key))
                    countMap.put(key, new AtomicInteger(0));
                final int count = countMap.get(key).getAndIncrement();
                semaphore.release();
                if (run != null)
                    run.run(new Eva(key), count);
            } catch (Exception ee) {
                Lok.error(Eva.class.getSimpleName() + ".eva(): Exception!!!");
                ee.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Eva.eva((eva, count) -> Lok.debug("Eva.main." + count));
        Eva.eva((eva, count) -> Lok.debug("Eva.main." + count));
    }
}
