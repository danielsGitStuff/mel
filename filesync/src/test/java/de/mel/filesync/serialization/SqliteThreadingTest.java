package de.mel.filesync.serialization;

import de.mel.Lok;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.tools.N;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.FsFile;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.StageSet;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.execute.SqliteExecutor;
import de.mel.sql.*;
import de.mel.sql.conn.SQLConnector;
import de.mel.sql.transform.SqlResultTransformer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SuppressWarnings("Duplicates")
public class SqliteThreadingTest {


//    @Test
    public void thread() throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        IFile testDir = AbstractFile.instance("test");
        IFile rootFile = AbstractFile.instance(testDir, "root");
        CertificateManager.deleteDirectory(testDir);
        rootFile.mkdirs();
        RootDirectory root = new RootDirectory();
        root.setOriginalFile(rootFile);
        root.setId(1L);
        root.setPath(rootFile.getAbsolutePath());
        FileSyncSettings fileSyncSettings = new FileSyncSettings().setLastSyncedVersion(0L).setRole(FileSyncStrings.ROLE_CLIENT)
                .setRootDirectory(root)
                .setTransferDirectory(AbstractFile.instance(testDir.getPath() + File.separator + "transfer"));

        FileSyncDatabaseManager.SQLConnectionCreator sqlqueriesCreator = (driveDatabaseManager, uuid) -> new SQLQueries(SQLConnector.createSqliteConnection(new File(testDir.getPath(), "test.db")), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
        ISQLQueries sqlQueries = sqlqueriesCreator.createConnection(null, null);
        SQLStatement st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA synchronous=OFF");
        st.execute();
        st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA foreign_keys=ON");
        st.execute();
        SqliteExecutor sqliteExecutor = new SqliteExecutor(sqlQueries.getSQLConnection());
        if (!sqliteExecutor.checkTablesExist("fsentry", "stage", "stageset", "transfer", "waste")) {
            //find sql file in workingdir
            FileSyncDatabaseManager.FileSyncSqlInputStreamInjector fileSyncSqlInputStreamInjector = () -> FileSyncDatabaseManager.class.getResourceAsStream("/de/mel/filesync/filesync.sql");
            sqliteExecutor.executeStream(fileSyncSqlInputStreamInjector.createSqlFileInputStream());
        }

        FsDao fsDao = new FsDao(null, sqlQueries);
        StageDao stageDao = new StageDao(fileSyncSettings, sqlQueries, fsDao);

        StageSet stageSet1 = fillNewStageSet(stageDao, 2000, 3000);
        StageSet stageSet2 = fillNewStageSet(stageDao, 2000, 3000);
        N n = new N(e -> {
            System.err.println("jjv54900");
            e.printStackTrace();
        });

        Callable<Void> reader1 = () -> {
            n.r(() -> {
                ISQLResource<Stage> resource = stageDao.getStagesByStageSet(stageSet1.getId().v());
                Stage stage = resource.getNext();
                while (stage != null) {
                    Lok.debug("read1: " + stage.getName());
                    stage = resource.getNext();
                }
            });
            return null;
        };
        Callable<Void> reader2 = () -> {
            n.r(() -> {
                ISQLResource<Stage> resource = stageDao.getStagesByStageSet(stageSet1.getId().v());
                Stage stage = resource.getNext();
                while (stage != null) {
                    Lok.debug("read2: " + stage.getName());
                    stage = resource.getNext();
                }
            });
            return null;
        };
        Callable<Void> writer1 = () -> {
            n.r(() -> {
                for (Integer i = 1; i < 500; i++) {
                    Stage stage = new Stage()
                            .setStageSet(stageSet1.getId().v())
                            .setName(i.toString())
                            .setIsDirectory(false)
                            .setOrder((long) i)
                            .setDeleted(false);
                    Lok.debug("write: " + i);
                    stageDao.insert(stage);
                }
            });
            return null;
        };
        Callable<Void> writer2 = () -> {
            n.r(() -> {
//                FsDirectory fsDirectory = (FsDirectory) new FsDirectory()
//                        .setName("dir")
//                        .setOldVersion(1L);
//                fsDao.insert(fsDirectory);
                for (Integer i = 500; i < 1000; i++) {
//                    Stage stage = new Stage()
//                            .setStageSet(stageSet2.getId().v())
//                            .setName(i.toString())
//                            .setIsDirectory(false)
//                            .setOrder((long) i)
//                            .setDeleted(false);
//                    Lok.debug("write: " + i);
//                    stageDao.insert(stage);
                    FsFile fsFile = new FsFile()
                            .setName(i.toString());
                    fsFile.getVersion().v(1L);
                    fsFile.getSynced().v(true);
                    fsDao.insert(fsFile);
                }
            });
            return null;
        };

        Callable<Void> updater = () -> {
            n.r(() -> {
                ISQLResource<Stage> resource = stageDao.getStagesByStageSet(stageSet1.getId().v());
                Stage stage = resource.getNext();
                while (stage != null) {
                    Lok.debug("read2: " + stage.getName());
                    stage.setName(stage.getName() + ".a");
                    stageDao.update(stage);
                    stage = resource.getNext();
                }
            });
            return null;
        };

        List<Callable<Void>> callables = new ArrayList<>();
        callables.add(reader1);
        callables.add(reader2);
        callables.add(writer1);
        callables.add(writer2);
        callables.add(updater);
        ExecutorService executorService = Executors.newCachedThreadPool();
//        executorService.submit(reader1);
//        executorService.submit(reader2);
//        executorService.submit(writer1);
//        executorService.submit(writer2);
//        executorService.submit(updater);
        List<Future<Void>> res = executorService.invokeAll(callables);
        for (Future<Void> f : res) {
            f.get();
        }
        Lok.debug("SqliteThreadingTest.thread.end");
    }

    private StageSet fillNewStageSet(StageDao stageDao, int from, int to) throws SqlQueriesException {
        StageSet stageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_STARTUP_INDEX, null, null, null, 666L);
        for (Integer i = from; i < to; i++) {
            Stage stage = new Stage()
                    .setStageSet(stageSet.getId().v())
                    .setName(i.toString())
                    .setIsDirectory(false)
                    .setOrder((long) i)
                    .setDeleted(false);
            stageDao.insert(stage);
        }
        return stageSet;
    }


}
