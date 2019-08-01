package de.mein.drive;

import de.mein.drive.bash.BashTools;
import de.mein.drive.nio.FileDistributor;
import de.mein.drive.nio.FileDistributorImpl;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.index.watchdog.IndexWatchdogListener;

/**
 * Created by xor on 2/4/17.
 */

public class DriveInjector {
    public static void setDriveSqlInputStreamInjector(DriveDatabaseManager.DriveSqlInputStreamInjector driveSqlInputStreamInjector) {
        DriveDatabaseManager.setDriveSqlInputStreamInjector(driveSqlInputStreamInjector);
    }

    public static void setSqlConnectionCreator(DriveDatabaseManager.SQLConnectionCreator connectionCreator) {
        DriveDatabaseManager.setSqlqueriesCreator(connectionCreator);
    }

    public static void setWatchDogRunner(IndexWatchdogListener.WatchDogRunner watchDogRunner) {
        IndexWatchdogListener.setWatchDogRunner(watchDogRunner);
    }

    public static void setFileDistributorImpl(Class<FileDistributorImpl> fileDistributorClass) {
        FileDistributor.Companion.setFileDistributorImpl(fileDistributorClass);
    }

    public static void setBinPath(String path) {
        BashTools.setBinPath(path);
    }
}
