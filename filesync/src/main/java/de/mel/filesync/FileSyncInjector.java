package de.mel.filesync;

import de.mel.filesync.bash.BashTools;
import de.mel.filesync.nio.FileDistributor;
import de.mel.filesync.nio.FileDistributorFactory;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.index.watchdog.IndexWatchdogListener;

/**
 * Created by xor on 2/4/17.
 */

public class FileSyncInjector {
    public static void setDriveSqlInputStreamInjector(FileSyncDatabaseManager.FileSyncSqlInputStreamInjector fileSyncSqlInputStreamInjector) {
        FileSyncDatabaseManager.setFileSyncSqlInputStreamInjector(fileSyncSqlInputStreamInjector);
    }

    public static void setSqlConnectionCreator(FileSyncDatabaseManager.SQLConnectionCreator connectionCreator) {
        FileSyncDatabaseManager.setSqlqueriesCreator(connectionCreator);
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
