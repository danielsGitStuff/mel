package de.mein.drive.index.watchdog;


import com.sun.nio.file.ExtendedWatchEventModifier;

import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.drive.service.MeinDriveService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by xor on 2/6/17.
 */

public abstract class IndexWatchdogListenerPC extends IndexWatchdogListener {
    protected WatchService watchService;
    protected WatchEvent.Kind<?>[] KINDS = new WatchEvent.Kind<?>[]{StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE};


    public IndexWatchdogListenerPC(MeinDriveService meinDriveService, String name, WatchService watchService) {
        super(meinDriveService);
        this.name = name;
        this.watchService = watchService;
        this.setStageIndexer(meinDriveService.getStageIndexer());
        this.transferDirectoryPath = meinDriveService.getDriveSettings().getTransferDirectory().getAbsolutePath();
    }

    protected void analyze(WatchEvent<?> event, AFile file) {
        try {
            if (meinDriveService.getMeinAuthService().getPowerManager().heavyWorkAllowed()) {
                watchDogTimer.start();
            }else
                surpressEvent();
            if (event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
                // figure out whether or not writing to the file is still in progress
                try {
                    double r = Math.random();
                    System.out.println("IndexWatchdogListener.analyze.attempt to open " + file.getAbsolutePath() + " " + r);
                    InputStream is = file.inputStream();
                    is.close();
                    System.out.println("IndexWatchdogListener.analyze.success " + r);
                    watchDogTimer.resume();
                } catch (FileNotFoundException e) {
                    System.out.println("IndexWatchdogListener.analyze.file not found: " + file.getAbsolutePath());
                } catch (Exception e) {
                    System.out.println("IndexWatchdogListener.analyze.writing in progress");
                    watchDogTimer.waite();
                }
            } else if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE) && file.exists() && file.isDirectory()) {
                this.watchDirectory(file);
            }
            System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].analyze[" + event.kind() + "]: " + file.getAbsolutePath());
            pathCollection.addPath(file);
            if (event.kind().equals(ExtendedWatchEventModifier.FILE_TREE)) {
                System.out.println("ALARM!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onShutDown() {
        N.r(() -> watchService.close());
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinDriveService.getRunnableName();
    }



}
