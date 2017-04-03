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
                ignoredSemaphore.acquire();
                Path keyPath = (Path) watchKey.watchable();
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    Path eventPath = (Path) event.context();
                    String absolutePath = keyPath.toString() + File.separator + eventPath.toString();
                    if (!ignoredMap.containsKey(absolutePath)) {
                        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].got event[" + event.kind() + "] for: " + absolutePath);
                        File object = new File(absolutePath);
                        analyze(event, object);
                    } else {
                        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].IGN event[" + event.kind() + "] for: " + absolutePath);
                        int amount = ignoredMap.get(absolutePath);
                        amount--;
                        if (amount == 0) {
                            System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].STOP IGN for: " + absolutePath);
                            ignoredMap.remove(absolutePath);
                        }
                        else
                            ignoredMap.put(absolutePath, amount);
                    }
                    watchKey.reset();
                }
                ignoredSemaphore.release();
                // reset the key
                boolean valid = watchKey.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
