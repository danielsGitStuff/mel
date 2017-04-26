package de.mein.drive.watchdog;

import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

@Deprecated
/**
 * Created by xor on 7/11/16.
 */
class IndexWatchdogUnix extends IndexWatchdogPC {
    // todo debug
    private Map<String, WatchKey> keyMap = new HashMap<>();

    IndexWatchdogUnix(MeinDriveService meinDriveService, WatchService watchService) {
        super(meinDriveService, "IndexWatchdogUnix", watchService);
    }

    @Override
    public void foundDirectory(FsDirectory fsDirectory) {
        try {
            Path path = Paths.get(fsDirectory.getOriginal().getAbsolutePath());
            WatchKey key = path.register(watchService, KINDS);
            keyMap.put(path.toString(), key);
            System.out.println("IndexWatchdogListener.foundDirectory: " + path.toString());
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



    @Override
    public void runImpl() {

    }
}
