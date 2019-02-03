package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.N;
import de.mein.auth.tools.NWrap;
import de.mein.auth.tools.Order;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.core.serialize.serialize.tools.OTimer;
import de.mein.drive.DriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.bash.BashTools;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

public class InsertPerformance {
    static AFile WORKING_DIR;
    static AFile ROOT_DIR;
    NWrap<MeinDriveServerService> mds = new NWrap<>(null);
    StageDao stageDao;
    private MeinBoot meinBoot;
    Random random = new Random();
    Order order = new Order();
    OTimer timer = new OTimer("insert");

    @Before
    public void init() throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
        WORKING_DIR = AFile.instance("perf.test");
        ROOT_DIR = AFile.instance(WORKING_DIR, "root");


        RWLock lock = new RWLock().lockWrite();
        MeinAuthSettings settings = MeinAuthSettings.createDefaultSettings();
        settings.setWorkingDirectory(new File(WORKING_DIR.getAbsolutePath()));
        meinBoot = new MeinBoot(settings, new PowerManager(settings), DriveBootloader.class);
        meinBoot.boot().done(mas -> N.r(() -> {
            DriveCreateController dcc = new DriveCreateController(mas);
            mds.v = dcc.createDriveServerService("test", ROOT_DIR, .5f, 300);
            lock.unlockWrite();
        }));
        lock.lockWrite();
        stageDao = mds.v.getDriveDatabaseManager().getStageDao();
    }


    @Test
    public void insert() throws Exception {
        // create N entries then dive down the lsat one
        StageSet stageSet = stageDao.createStageSet("test", 1L, null, 3L);
        Stage root = new Stage().setContentHash(UUID.randomUUID().toString())
                .setDeleted(false)
                .setiNode(random.nextLong())
                .setIsDirectory(true)
                .setModified(random.nextLong())
                .setName("ROOT")
                .setOrder(order.ord())
                .setSize(random.nextLong())
                .setSynced(true)
                .setVersion(3L)
                .setStageSet(stageSet.getId().v());
        stageDao.insert(root);
        iteration(root, 2000, 1L, 200000L);

    }

    private void iteration(Stage parent, final int N, long count, final long MAX) throws SqlQueriesException {
        Stage stage = null;
        while (count < MAX) {
            timer.reset().start();
            for (int n = 0; n < N; n++) {
                stage = new Stage();
                stage.setContentHash(UUID.randomUUID().toString())
                        .setDeleted(false)
                        .setiNode(random.nextLong())
                        .setIsDirectory(true)
                        .setModified(random.nextLong())
                        .setName(UUID.randomUUID().toString())
                        .setOrder(order.ord())
                        .setSize(random.nextLong())
                        .setSynced(true)
                        .setVersion(3L)
                        .setParentId(parent.getParentId())
                        .setStageSet(parent.getStageSet());
                stageDao.insert(stage);
                count++;

            }
            parent = stage;
            timer.stop();
            System.out.println( count + ";" + timer.getDurationInMS());
        }
    }

    @After
    public void after() throws InterruptedException, IOException {
        meinBoot.shutDown();
        BashTools.rmRf(WORKING_DIR);
    }
}
