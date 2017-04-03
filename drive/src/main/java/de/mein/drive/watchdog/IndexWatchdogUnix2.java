package de.mein.drive.watchdog;

import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;

import java.io.File;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 7/11/16.
 */
@SuppressWarnings("Duplicates")
class IndexWatchdogUnix2 extends IndexWatchdogPC {
    // todo debug
    private Map<String, WatchKey> keyMap = new HashMap<>();

    IndexWatchdogUnix2(MeinDriveService meinDriveService, WatchService watchService) {
        super(meinDriveService, "IndexWatchdogUnix", watchService);
    }

    @Override
    public void foundDirectory(FsDirectory fsDirectory) {
        try {
            Path path = Paths.get(fsDirectory.getOriginal().getAbsolutePath());
            WatchKey key =path.register(watchService, KINDS);
            keyMap.put(path.toString(), key);
            System.out.println("IndexWatchdogListener.foundDirectory: " + path.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName(name);
            /**
             * cause the WatchService sometimes confuses the WatchKeys we have to go around that.
             * We will only process all "delete" and "modify" (cause they can be ongoing for some time) events directly.
             * when an Event popped up, we will start the Timer and once it is finished we ask the Bash to show us every new/modified
             * File or Directory.
             */
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

    @Override
    public void watchDirectory(File dir) {
        try {
            Path path = Paths.get(dir.getAbsolutePath());
            WatchKey key = path.register(watchService, KINDS);
            keyMap.put(dir.getAbsolutePath(), key);
            System.out.println("IndexWatchdogListener.watchDirectory: " + path.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
