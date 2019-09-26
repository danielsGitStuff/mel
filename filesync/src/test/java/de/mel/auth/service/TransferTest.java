package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.file.AFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.CountdownLock;
import de.mel.auth.tools.N;
import de.mel.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mel.drive.DriveBootloader;
import de.mel.drive.DriveCreateServiceHelper;
import de.mel.drive.DriveSyncListener;
import de.mel.drive.bash.BashTools;
import de.mel.drive.service.MelDriveClientService;
import de.mel.drive.service.MelDriveServerService;
import de.mel.sql.Hash;
import de.mel.sql.RWLock;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;
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
    private MelDriveServerService serverService;
    private int PORT_COUNT = 6666;
    private boolean bootedOnce;
    private MelDriveClientService clientService;

    static interface Scopee {
        void run(MelAuthService melAuthService) throws Exception;
    }

    public void init(File workingDirectory, Scopee initDrive) throws Exception {

        RWLock lock = new RWLock();
        lock.lockWrite();
        MelAuthSettings melAuthSettings = MelAuthSettings.createDefaultSettings();
        melAuthSettings.setPort(PORT_COUNT++);
        melAuthSettings.setDeliveryPort(PORT_COUNT++);
        if (bootedOnce) {
            melAuthSettings.setBrotcastListenerPort(BCL_PORT1);
            melAuthSettings.setBrotcastPort(BCL_PORT2);
        } else {
            melAuthSettings.setBrotcastListenerPort(BCL_PORT2);
            melAuthSettings.setBrotcastPort(BCL_PORT1);
        }
        bootedOnce = true;

        melAuthSettings.setWorkingDirectory(workingDirectory);
        //setupServer dirs
        CertificateManager.deleteDirectory(melAuthSettings.getWorkingDirectory());
        melAuthSettings.getWorkingDirectory().mkdirs();
        AFile testDir = AFile.instance(AFile.instance(melAuthSettings.getWorkingDirectory()), ROOT_DIR_NAME);
        testDir.mkdirs();
        Lok.debug(testDir.getAbsolutePath() + " /// " + testDir.exists());
        melAuthSettings.save();
        MelBoot melBoot = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), DriveBootloader.class);
        melBoot.boot().done(melAuthService -> {
            Lok.debug("Main.main.booted (DEV)");

            new Thread(() -> N.r(() -> initDrive.run(melAuthService))).start();
            melAuthService.addRegisterHandler(new IRegisterHandler() {
                @Override
                public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
                    N.r(() -> N.forEach(melAuthService.getCertificateManager().getTrustedCertificates(), oldeCert -> melAuthService.getCertificateManager().deleteCertificate(oldeCert)));
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

                @Override
                public void setup(MelAuthAdmin melAuthAdmin) {

                }
            });
            melAuthService.addRegisteredHandler((melAuthService1, registered) -> {
                N.forEach(melAuthService.getDatabaseManager().getAllServices(), serviceJoinServiceType -> melAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v()));
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

    private MelDriveServerService setupServer(File workingDir) throws Exception {
        AtomicReference<MelDriveServerService> serverService = new AtomicReference<>();
        CountdownLock bootLock = new CountdownLock(1);
        init(workingDir, melAuthService -> {
            AFile root = AFile.instance(AFile.instance(workingDir), ROOT_DIR_NAME);
            Path path = Paths.get(root.getAbsolutePath() + File.separator + "text.txt");
            StringBuilder builder = new StringBuilder("start...");
            N.forLoop(1, 2000, (stoppable, index) -> builder.append(index).append("/"));
            Files.write(path, builder.toString().getBytes());
            DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
                serverService.set((MelDriveServerService) driveService);
                SERVER_SERVICE_UUID = serverService.get().getUuid();
                bootLock.unlock();
            };
            DriveCreateServiceHelper createController = new DriveCreateServiceHelper(melAuthService);
            createController.createServerService("server", root, .5f, 666, false);
        });
        bootLock.lock();
        return serverService.get();
    }

    private MelDriveClientService setupClient(File workingDir) throws Exception {
        AtomicReference<MelDriveClientService> clientService = new AtomicReference<>();
        CountLock bootLock = new CountLock();
        init(workingDir, melAuthService -> {
            Promise<MelValidationProcess, Exception, Void> paired = melAuthService.connect("localhost", 6666, 6667, true);
            paired.done(result -> new Thread(() -> N.r(() -> {
                AFile root = AFile.instance(AFile.instance(workingDir), ROOT_DIR_NAME);
                DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> N.r(() -> {
                    clientService.set((MelDriveClientService) driveService);
                    bootLock.unlock();
                });
                new DriveCreateServiceHelper(melAuthService).createClientService("server", root, 1L, SERVER_SERVICE_UUID, 0.5f, 666, false);
            })
            ).start());
        });
        bootLock.lock().lock();
        return clientService.get();
    }

//todo refine, this is tested elsewhere
//    @Test
    public void transfer() throws Exception {
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
