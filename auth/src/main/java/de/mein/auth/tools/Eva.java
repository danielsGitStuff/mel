package de.mein.auth.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import de.mein.Lok;

/**
 * Created by xor on 27.08.2017.
 */
public class Eva {
    private final String key;

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
    private static Semaphore semaphore = new Semaphore(1, true);
    public static final boolean ENABLED = true;

    public static void eva(EvaRun run) {
        if (ENABLED) {
            try {
                semaphore.acquire();
                String key;
                try {
                    throw new Exception();
                } catch (Exception e) {
                    StackTraceElement trace = e.getStackTrace()[1];
                    key = trace.getClassName() + "/" + trace.getMethodName();
                }
                if (!countMap.containsKey(key))
                    countMap.put(key, new AtomicInteger(0));
                final int count = countMap.get(key).getAndIncrement();
                semaphore.release();
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
