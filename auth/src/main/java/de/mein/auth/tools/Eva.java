package de.mein.auth.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xor on 27.08.2017.
 */
public class Eva {
    public interface EvaRun {
        void run(Eva eva, final int count) throws Exception;
    }

    private Eva() {
    }

    public Eva out(String msg) {
        System.out.println("Eva.out: " + msg);
        return this;
    }

    public Eva error() {
        System.err.println("Eva.error");
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
                run.run(new Eva(), count);
            } catch (Exception ee) {
                System.err.println(Eva.class.getSimpleName() + ".eva(): Exception!!!");
                ee.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Eva.eva((eva, count) -> System.out.println("Eva.main." + count));
        Eva.eva((eva, count) -> System.out.println("Eva.main." + count));
    }
}
