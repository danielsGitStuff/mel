package de.mein.drive.serialization;

import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.file.AFile;
import de.mein.auth.file.FFile;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.execute.SqliteExecutor;
import de.mein.sql.*;
import de.mein.sql.conn.SQLConnector;
import de.mein.sql.transform.SqlResultTransformer;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("Duplicates")
public class SqliteThreadingTest {

    @Test
    public void thread() throws Exception {
        AFile.setClass(FFile.class);
        AFile testDir = AFile.instance("test");
        AFile rootFile = AFile.instance(testDir, "root");
        CertificateManager.deleteDirectory(testDir);
        rootFile.mkdirs();
        RootDirectory root = new RootDirectory();
        root.setOriginalFile(rootFile);
        root.setId(1L);
        root.setPath(rootFile.getAbsolutePath());
        DriveSettings driveSettings = new DriveSettings().setLastSyncedVersion(0L).setRole(DriveStrings.ROLE_CLIENT)
                .setRootDirectory(root)
                .setTransferDirectoryPath(testDir.getPath() + File.separator + "transfer");

        DriveDatabaseManager.SQLConnectionCreator sqlqueriesCreator = (driveDatabaseManager, uuid) -> new SQLQueries(SQLConnector.createSqliteConnection(new File(testDir.getPath(), "test.db")), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
        ISQLQueries sqlQueries = sqlqueriesCreator.createConnection(null, null);
        SQLStatement st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA synchronous=OFF");
        st.execute();
        st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA foreign_keys=ON");
        st.execute();
        SqliteExecutor sqliteExecutor = new SqliteExecutor(sqlQueries.getSQLConnection());
        if (!sqliteExecutor.checkTablesExist("fsentry", "stage", "stageset", "transfer", "waste")) {
            //find sql file in workingdir
            DriveDatabaseManager.DriveSqlInputStreamInjector driveSqlInputStreamInjector = () -> String.class.getResourceAsStream("/drive.sql");
            sqliteExecutor.executeStream(driveSqlInputStreamInjector.createSqlFileInputStream());
        }

        FsDao fsDao = new FsDao(null, sqlQueries);
        StageDao stageDao = new StageDao(driveSettings, sqlQueries, fsDao);

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
        executorService.submit(reader1);
        executorService.submit(reader2);
        executorService.submit(writer1);
        executorService.submit(writer2);
//        executorService.submit(updater);
//        List<Future<Void>> res = executorService.invokeAll(callables);
//        for (Future<Void> f : res) {
//            f.get();
//        }
        Lok.debug("SqliteThreadingTest.thread.end");
        new WaitLock().lock().lock();
    }

    private StageSet fillNewStageSet(StageDao stageDao, int from, int to) throws SqlQueriesException {
        StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_STARTUP_INDEX, null, null,null);
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
