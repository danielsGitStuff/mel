package de.mel.util;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.RootDirectory;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.StageSet;
import de.mel.filesync.sql.dao.ConflictDao;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.sql.SQLQueries;
import de.mel.sql.conn.SQLConnector;
import de.mel.sql.transform.SqlResultTransformer;
import fun.with.Lists;
import fun.with.Maps;

import java.io.File;
import java.nio.file.Files;

/**
 * Helps to debug a database with conflicts
 */
public class AnalyseConflictDb {
    public static void main(String[] args) throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        String serviceUUid = "test uuid";
        File workingDirectory = new File("delete_me_workdir");
        workingDirectory.mkdirs();
        File srcFile = new File("/home/xor/Documents/melfilesync.db");
        File dbFile = new File("/home/xor/Documents/melfilesync.run.db");
        if (dbFile.exists()) dbFile.delete();
        Files.copy(srcFile.toPath(), dbFile.toPath());

        FileSyncSettings fileSyncSettings = new FileSyncSettings();
        fileSyncSettings.setRootDirectory(RootDirectory.buildRootDirectory(new File("delete_me_rootdir")));
        SQLQueries sqlQueries = new SQLQueries(SQLConnector.createSqliteConnection(dbFile), SqlResultTransformer.sqliteResultSetTransformer());
        FileSyncDatabaseManager fileSyncDatabaseManager = new FileSyncDatabaseManager(serviceUUid, workingDirectory, fileSyncSettings);
        FsDao fsDao = new FsDao(fileSyncDatabaseManager, sqlQueries);
        StageDao stageDao = new StageDao(fileSyncSettings, sqlQueries, fsDao);
        ConflictDao conflictDao = new ConflictDao(stageDao, fsDao);
        StageSet localStageSet = stageDao.getStageSetById(7L);
        StageSet remoteStageSet = stageDao.getStageSetById(8L);
        ConflictSolver conflictSolver = new ConflictSolver(conflictDao, localStageSet, remoteStageSet);
        conflictSolver.findConflicts();
        Lists<Conflict> children = Lists.wrap(Lists.wrap(conflictSolver.getConflictMap().values())
                .filter(Conflict::hasChildren)
                .first()
                .getChildren().get());
        Maps<Integer, Lists<Conflict>> grouped = children.groupBy(conflict -> conflict.hashCode());
        System.out.println("AnalyseConflictDb.main");
    }
}
