package de.mein.drive.watchdog;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

import de.mein.drive.service.MeinDriveService;
import de.mein.drive.watchdog.IndexWatchdogListener;

/**
 * Created by xor on 2/6/17.
 */

public abstract class IndexWatchdogPC extends IndexWatchdogListener {
    protected WatchService watchService;
    protected WatchEvent.Kind<?>[] KINDS = new WatchEvent.Kind<?>[]{StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE};

    public IndexWatchdogPC(MeinDriveService meinDriveService, String name, WatchService watchService) {
        this.name = name;
        this.watchService = watchService;
        this.meinDriveService = meinDriveService;
        this.setStageIndexer(meinDriveService.getStageIndexer());
    }
    @Override
    public void run() {
        try {
            Thread.currentThread().setName(name);
            while (true) {
                WatchKey watchKey = watchService.take();
                List<WatchEvent<?>> events = watchKey.pollEvents();
                for (WatchEvent<?> event : events) {
                    Path eventPath = (Path) event.context();
                    Path watchKeyPath = (Path) watchKey.watchable();
                    String objectPath = watchKeyPath.toString() + File.separator + eventPath.toString();
                    ignoredSemaphore.acquire();
                    if (!ignoredMap.containsKey(objectPath)) {
                        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].got event for: " + objectPath);
                        File object = new File(objectPath);
                        analyze(event, object);
                    } else {
                        ignoredMap.remove(objectPath);
                    }
                    ignoredSemaphore.release();
                }
                // reset the key
                boolean valid = watchKey.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
