package de.mein.drive.watchdog;

import com.sun.nio.file.ExtendedWatchEventModifier;
import de.mein.drive.sql.FsDirectory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchService;

/**
 * Created by xor on 12.08.2016.
 */
public class IndexWatchDogWindows extends IndexWatchdogListener {

    private boolean watchesRoot = false;

    public IndexWatchDogWindows(WatchService watchService) {
        super("IndexWatchDogWindows", watchService);
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
}
