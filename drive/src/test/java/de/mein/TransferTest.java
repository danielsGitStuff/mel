package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.CountLock;
import de.mein.auth.tools.CountdownLock;
import de.mein.auth.tools.N;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.drive.DriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.bash.BashTools;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.sql.Hash;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import org.jdeferred.Promise;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("Duplicates")
public class TransferTest {

    private static final String ROOT_DIR_NAME = "root";
    private static String SERVER_SERVICE_UUID = "serverserviceuuid";
    private static final Integer BCL_PORT1 = 6000;
    private static final Integer BCL_PORT2 = 6001;
    private File wd1;
    private File wd2;
    private MeinDriveServerService serverService;
    private int PORT_COUNT = 6666;
    private boolean bootedOnce;
    private MeinDriveClientService clientService;

    static interface Scopee {
        void run(MeinAuthService meinAuthService) throws Exception;
    }

    public void init(File workingDirectory, Scopee initDrive) throws Exception {

        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinAuthSettings meinAuthSettings = MeinAuthSettings.createDefaultSettings();
        meinAuthSettings.setPort(PORT_COUNT++);
        meinAuthSettings.setDeliveryPort(PORT_COUNT++);
        if (bootedOnce) {
            meinAuthSettings.setBrotcastListenerPort(BCL_PORT1);
            meinAuthSettings.setBrotcastPort(BCL_PORT2);
        } else {
            meinAuthSettings.setBrotcastListenerPort(BCL_PORT2);
            meinAuthSettings.setBrotcastPort(BCL_PORT1);
        }
        bootedOnce = true;

        meinAuthSettings.setWorkingDirectory(workingDirectory);
        //setupServer dirs
        CertificateManager.deleteDirectory(meinAuthSettings.getWorkingDirectory());
        meinAuthSettings.getWorkingDirectory().mkdirs();
        AFile testDir = AFile.instance(AFile.instance(meinAuthSettings.getWorkingDirectory()), ROOT_DIR_NAME);
        testDir.mkdirs();
        Lok.debug(testDir.getAbsolutePath() + " /// " + testDir.exists());
        meinAuthSettings.save();
        MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveBootloader.class);
        meinBoot.boot().done(meinAuthService -> {
            Lok.debug("Main.main.booted (DEV)");
            N.r(() -> initDrive.run(meinAuthService));
            meinAuthService.addRegisterHandler(new IRegisterHandler() {
                @Override
                public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                    N.r(() -> N.forEach(meinAuthService.getCertificateManager().getTrustedCertificates(), oldeCert -> meinAuthService.getCertificateManager().deleteCertificate(oldeCert)));
                    listener.onCertificateAccepted(request, certificate);
                }

                @Override
                public void onRegistrationCompleted(Certificate partnerCertificate) {

                }

                @Override
                public void onRemoteRejected(Certificate partnerCertificate) {

                }

                @Override
                public void onLocallyRejected(Certificate partnerCertificate) {

                }

                @Override
                public void onRemoteAccepted(Certificate partnerCertificate) {

                }

                @Override
                public void onLocallyAccepted(Certificate partnerCertificate) {

                }
            });
            meinAuthService.addRegisteredHandler((meinAuthService1, registered) -> {
                N.forEach(meinAuthService.getDatabaseManager().getAllServices(), serviceJoinServiceType -> meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v()));
            });
            lock.unlockWrite();
        }).fail(exc -> {
            exc.printStackTrace();
        });

        lock.lockWrite();
//        lock.lockWrite();
        Lok.debug("Main.main.end");
//        new WaitLock().lock().lock();
    }

    @Before
    public void before() throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();

        wd1 = new File("wd1");
        wd2 = new File("wd2");
        serverService = setupServer(wd1);
        clientService = setupClient(wd2);

    }

    private MeinDriveServerService setupServer(File workingDir) throws Exception {
        AtomicReference<MeinDriveServerService> serverService = new AtomicReference<>();
        CountdownLock bootLock = new CountdownLock(1);
        init(workingDir, meinAuthService -> {
            AFile root = AFile.instance(AFile.instance(workingDir), ROOT_DIR_NAME);
            Path path = Paths.get(root.getAbsolutePath() + File.separator + "text.txt");
            StringBuilder builder = new StringBuilder("start...");
            N.forLoop(1,2000,(stoppable, index) -> builder.append(index).append("/"));
            Files.write(path, builder.toString().getBytes());
            serverService.set(new DriveCreateController(meinAuthService).createDriveServerService("server", root, 0.5f, 666));
            SERVER_SERVICE_UUID = serverService.get().getUuid();
            bootLock.unlock();
        });
        bootLock.lock();
        return serverService.get();
    }

    private MeinDriveClientService setupClient(File workingDir) throws Exception {
        AtomicReference<MeinDriveClientService> clientService = new AtomicReference<>();
        CountLock bootLock = new CountLock();
        init(workingDir, meinAuthService -> {
            Promise<MeinValidationProcess, Exception, Void> paired = meinAuthService.connect("localhost", 6666, 6667, true);
            paired.done(result -> N.r(() -> {
                AFile root = AFile.instance(AFile.instance(workingDir), ROOT_DIR_NAME);
                Promise<MeinDriveClientService, Exception, Void> ready = new DriveCreateController(meinAuthService).createDriveClientService("server", root, 1L, SERVER_SERVICE_UUID, 0.5f, 666);
                ready.done(result1 -> N.r(() -> {
                    clientService.set(result1);
                    bootLock.unlock();
                }));
            }));
        });
        bootLock.lock().lock();
        return clientService.get();
    }




    @Test
    public void transfer() throws Exception{
        Lok.debug("lel");
        CountLock doneLock = new CountLock();
        final int[] transferCount = {0};
        clientService.setSyncListener(new DriveSyncListener() {
            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {
                transferCount[0]++;
                Lok.debug("transfer count = " + transferCount[0]);
                if (transferCount[0] == 1)
                    doneLock.unlock();
            }

            @Override
            public void onSyncDoneImpl() {

            }
        });
        doneLock.lock().lock();
        // check for integrity
        // simply wait until the file moving has finished
        Thread.sleep(500);
        File rootServer = new File(serverService.getDriveSettings().getRootDirectory().getPath());
        File rootClient = new File(clientService.getDriveSettings().getRootDirectory().getPath());
        N.forEach(rootServer.listFiles(File::isFile), file -> {
            String name = file.getName();
            File clientFile = new File(rootClient, name);
            assertTrue(clientFile.exists());
            String md5Server = Hash.md5(new FileInputStream(file));
            String md5Client = Hash.md5(new FileInputStream(clientFile));
            assertEquals(md5Server, md5Client);
        });
    }
}
