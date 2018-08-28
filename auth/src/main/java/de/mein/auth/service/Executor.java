package de.mein.auth.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by xor on 12/16/16.
 */
public class Executor {
    private static ExecutorService cached = Executors.newCachedThreadPool();

    public static Future<?> startCached(Runnable runnable) {
        return cached.submit(runnable);
    }

}
