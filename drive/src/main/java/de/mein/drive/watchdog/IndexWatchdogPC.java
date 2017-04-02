package de.mein.drive.watchdog;

import de.mein.drive.service.MeinDriveService;

import java.io.File;
import java.nio.file.*;
import java.util.List;

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
                    File ff = ((Path) event.context()).toFile();
                    String oo = ff.getAbsolutePath();
                    Path watchKeyPath = (Path) watchKey.watchable();
                    String objectPath = watchKeyPath.toString() + File.separator + eventPath.toString();
                    ignoredSemaphore.acquire();
                    if (!ignoredMap.containsKey(objectPath)) {
                        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].got event[" + event.kind() + "] for: " + objectPath);
                        File object = new File(objectPath);
                        analyze(event, object);
                    } else {
                        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].IGN event[" + event.kind() + "] for: " + objectPath);
                        int amount = ignoredMap.get(objectPath);
                        amount--;
                        if (amount == 0) {
                            System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].STOP IGN for: " + objectPath);
                            ignoredMap.remove(objectPath);
                        }
                        else
                            ignoredMap.put(objectPath, amount);
                    }
                    ignoredSemaphore.release();
                    watchKey.reset();
                }
                // reset the key
                boolean valid = watchKey.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
