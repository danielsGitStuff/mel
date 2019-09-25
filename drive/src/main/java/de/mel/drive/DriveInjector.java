package de.mel.drive;

import de.mel.drive.bash.BashTools;
import de.mel.drive.nio.FileDistributor;
import de.mel.drive.nio.FileDistributorFactory;
import de.mel.drive.sql.DriveDatabaseManager;
import de.mel.drive.index.watchdog.IndexWatchdogListener;

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

    public static  <T extends FileDistributorFactory> void setFileDistributorFactory(T fileDistFactory) {
        FileDistributor.Companion.setFactory(fileDistFactory);
    }

    public static void setBinPath(String path) {
        BashTools.setBinPath(path);
    }
}
