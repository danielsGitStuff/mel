package de.mein.drive;

import de.mein.drive.index.BashTools;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.watchdog.IndexWatchdogListener;

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

    public static void setBinPath(String path) {
        BashTools.setBinPath(path);
    }
}
