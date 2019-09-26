package de.mel;

import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.file.AFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.MelBoot;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.N;
import de.mel.auth.tools.NWrap;
import de.mel.auth.tools.Order;
import de.mel.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mel.core.serialize.serialize.tools.OTimer;
import de.mel.drive.DriveBootloader;
import de.mel.drive.DriveCreateServiceHelper;
import de.mel.drive.bash.BashTools;
import de.mel.drive.serialization.TestDirCreator;
import de.mel.drive.service.MelDriveServerService;
import de.mel.drive.sql.Stage;
import de.mel.drive.sql.StageSet;
import de.mel.drive.sql.dao.StageDao;
import de.mel.sql.RWLock;
import de.mel.sql.SQLQueries;
import de.mel.sql.SqlQueriesException;
import de.mel.sql.conn.SQLConnector;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;
import de.mel.sql.transform.SqlResultTransformer;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

public class InsertPerformance {
//    static AFile WORKING_DIR;
//    static AFile ROOT_DIR;
//    NWrap<MelDriveServerService> mds = new NWrap<>(null);
//    StageDao stageDao;
//    private MelBoot melBoot;
//    Random random = new Random();
//    Order order = new Order();
//    OTimer timer = new OTimer("insert");

//    @Test
//    public void transaction() throws Exception {
//        before();
//        File dbFile = new File("performance.db");
//        if (dbFile.exists())
//            dbFile.delete();
//        SQLQueries sqlQueries = new SQLQueries(SQLConnector.createSqliteConnection(dbFile), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
//        String createStmt = "create table test(id integer not null primary key autoincrement,txt text)";
//        sqlQueries.execute(createStmt, null);
//    }
//
//    public void before() throws Exception {
//        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
//        AFile.configure(new DefaultFileConfiguration());
//        BashTools.init();
//        WORKING_DIR = AFile.instance("/home/xor/Downloads/perf.test");
//        BashTools.rmRf(WORKING_DIR);
//        ROOT_DIR = AFile.instance(WORKING_DIR, "root");
//
//        TestDirCreator.createFilesTestDir(ROOT_DIR, 4000);
//
//
//        RWLock lock = new RWLock().lockWrite();
//        MelAuthSettings settings = MelAuthSettings.createDefaultSettings();
//        settings.setWorkingDirectory(new File(WORKING_DIR.getAbsolutePath()));
//        melBoot = new MelBoot(settings, new PowerManager(settings), DriveBootloader.class);
//        melBoot.boot().done(mas -> N.r(() -> {
//            DriveCreateServiceHelper dcc = new DriveCreateServiceHelper(mas);
//            DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
//                mds.v = (MelDriveServerService) driveService;
//                lock.unlockWrite();
//            };
//            dcc.createServerService("test", ROOT_DIR, .5f, 300, false);
//        }));
//        lock.lockWrite();
//        stageDao = mds.v.getDriveDatabaseManager().getStageDao();
//    }
//
//    @Test
//    public void testDir() throws Exception {
//        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
//        AFile.configure(new DefaultFileConfiguration());
//        BashTools.init();
//        WORKING_DIR = AFile.instance("/home/xor/Downloads/perf.test");
//        BashTools.rmRf(WORKING_DIR);
//        ROOT_DIR = AFile.instance(WORKING_DIR, "root");
//
//        TestDirCreator.createFilesTestDir(ROOT_DIR, 4000);
//
//        RWLock lock = new RWLock().lockWrite();
//        MelAuthSettings settings = MelAuthSettings.createDefaultSettings();
//        settings.setWorkingDirectory(new File(WORKING_DIR.getAbsolutePath()));
//        melBoot = new MelBoot(settings, new PowerManager(settings), DriveBootloader.class);
//        melBoot.boot().done(mas -> N.r(() -> {
//            DriveCreateServiceHelper dcc = new DriveCreateServiceHelper(mas);
//            DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
//                mds.v = (MelDriveServerService) driveService;
//                lock.unlockWrite();
//            };
//            dcc.createServerService("test", ROOT_DIR, .5f, 300, false);
//        }));
//        lock.lockWrite();
//        stageDao = mds.v.getDriveDatabaseManager().getStageDao();
//        Lok.debug("done");
//    }
//
//
//    @Test
//    public void insert() throws Exception {
//        // create N entries then dive down the lsat one
//        StageSet stageSet = stageDao.createStageSet("test", 1L, null, 3L,2L);
//        Stage root = new Stage().setContentHash(UUID.randomUUID().toString())
//                .setDeleted(false)
//                .setiNode(random.nextLong())
//                .setIsDirectory(true)
//                .setModified(random.nextLong())
//                .setName("ROOT")
//                .setOrder(order.ord())
//                .setSize(random.nextLong())
//                .setSynced(true)
//                .setVersion(3L)
//                .setStageSet(stageSet.getId().v());
//        stageDao.insert(root);
//        iteration(root, 2000, 1L, 2000L);
//
//    }
//
//    private void iteration(Stage parent, final int N, long count, final long MAX) throws SqlQueriesException {
//        Stage stage = null;
//        while (count < MAX) {
//            timer.reset().start();
//            for (int n = 0; n < N; n++) {
//                stage = new Stage();
//                stage.setContentHash(UUID.randomUUID().toString())
//                        .setDeleted(false)
//                        .setiNode(random.nextLong())
//                        .setIsDirectory(true)
//                        .setModified(random.nextLong())
//                        .setName(UUID.randomUUID().toString())
//                        .setOrder(order.ord())
//                        .setSize(random.nextLong())
//                        .setSynced(true)
//                        .setVersion(3L)
//                        .setParentId(parent.getParentId())
//                        .setStageSet(parent.getStageSet());
//                stageDao.insert(stage);
//                count++;
//
//            }
//            parent = stage;
//            timer.stop();
//            System.out.println(count + ";" + timer.getDurationInMS());
//        }
//    }
//
//    @After
//    public void after() throws InterruptedException, IOException {
////        melBoot.shutDown();
////        BashTools.rmRf(WORKING_DIR);
//    }
}
