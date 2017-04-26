package de.mein.drive.watchdog;

import com.sun.nio.file.ExtendedWatchEventModifier;

import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchService;

/**
 * Created by xor on 12.08.2016.
 */
public class IndexWatchDogWindows extends IndexWatchdogPC {

    private boolean watchesRoot = false;

    public IndexWatchDogWindows(MeinDriveService meinDriveService, WatchService watchService) {
        super(meinDriveService,"IndexWatchDogWindows", watchService);
    }


    @Override
    public void foundDirectory(FsDirectory fsDirectory) {
        if (!watchesRoot)
            try {
                watchesRoot = true;
                Path path = Paths.get(fsDirectory.getOriginal().getAbsolutePath());
                path.register(watchService, KINDS, ExtendedWatchEventModifier.FILE_TREE);
                System.out.println("IndexWatchDogWindows.registerRoot: " + path.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
    }


    @Override
    public void watchDirectory(File dir) {

    }

    @Override
    public void runImpl() {

    }
}
