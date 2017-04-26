package de.mein.drive.index;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by xor on 10.07.2016.
 */
public class BackgroundExecutor {
    protected ExecutorService executorService;

    protected void adjustExecutor() {
        if (executorService != null && (!executorService.isShutdown() || !executorService.isTerminated())) {
            executorService.shutdownNow();
            executorService = null;
        }
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
    }

}
