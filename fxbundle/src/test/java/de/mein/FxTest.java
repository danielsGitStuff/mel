package de.mein;


import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.RegisterHandlerFX;
import de.mein.auth.service.MeinAuthFxLoader;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.MeinStandAloneAuthFX;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.MeinLogger;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.contacts.ContactsFXBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.boot.DriveFXBootLoader;
import de.mein.drive.serialization.DriveTest;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 09.09.2016.
 */
@SuppressWarnings("Duplicates")
public class FxTest {

    private static MeinStandAloneAuthFX standAloneAuth2;
    private static MeinStandAloneAuthFX standAloneAuth1;
    private static RWLock lock = new RWLock();

    @Test
    public void conflict() throws Exception {
        DriveTest driveTest = new DriveTest();
        MeinBoot meinBoot = new MeinBoot(new DriveTest().createJson2(), DriveFXBootLoader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        MeinBoot restartMeinBoot = new MeinBoot(new DriveTest().createJson2(), DriveFXBootLoader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        driveTest.clientConflictImpl(meinBoot, null);
        new WaitLock().lock().lock();
    }

    @Test
    public void startUpConflicts() throws Exception {
        DriveTest driveTest = new DriveTest();
        MeinBoot meinBoot = new MeinBoot(driveTest.createJson2(), DriveFXBootLoader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        driveTest.startUpConflicts(meinBoot);
        new WaitLock().lock().lock();
    }

    @Test
    public void complexConflict() throws Exception {
        DriveTest driveTest = new DriveTest();
        MeinBoot meinBoot = new MeinBoot(new DriveTest().createJson2(), DriveFXBootLoader.class).addMeinAuthAdmin(new MeinAuthFxLoader());

        MeinBoot restartMeinBoot = new MeinBoot(new DriveTest().createJson2(), DriveFXBootLoader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        driveTest.complexClientConflictImpl(meinBoot, null);
        new WaitLock().lock().lock();
    }

    @Test
    public void startEmptyClient() throws Exception {
        //CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("Test Client").setGreeting("greeting2");
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        //todo continue gui
        MeinBoot boot1 = new MeinBoot(json1, DriveFXBootLoader.class)
                .addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(result -> {
            result.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                System.out.println("FxTest.startEmptyClient.booted");
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    @Test
    public void startEmptyServer() throws Exception {
        File testdir = new File("testdir1");
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        N runner = new N(e -> e.printStackTrace());
        MeinStandAloneAuthFX standAloneAuth1;
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("Test Server").setGreeting("greeting1");
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinBoot boot1 = new MeinBoot(json1, DriveFXBootLoader.class);
        boot1.addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                System.out.println("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                DriveCreateController createController = new DriveCreateController(meinAuthService);
                createController.createDriveServerService("testiServer", testdir.getAbsolutePath());
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    private void connectAcceptingClient() throws Exception {
        File testdir = new File("testdir2");
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("Test Client").setGreeting("greeting2");
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        };
        IRegisteredHandler allowRegisteredHandler = (meinAuthService, registered) -> {
            DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
            for (ServiceJoinServiceType service : databaseManager.getAllServices()) {
                databaseManager.grant(service.getServiceId().v(), registered.getId().v());
            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinBoot boot1 = new MeinBoot(meinAuthSettings, DriveFXBootLoader.class);
        boot1.addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(allowRegisterHandler);
            meinAuthService.addRegisteredHandler(allowRegisteredHandler);
//            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                System.out.println("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect("127.0.0.1", 8888, 8889, true);
                connected.done(result -> N.r(() -> {
                    DriveCreateController createController = new DriveCreateController(meinAuthService);
                    Promise<MeinDriveClientService, Exception, Void> clientBooted = createController.createDriveClientService("drive client", testdir.getAbsolutePath(), 1L, tmp);
                    System.out.println("FxTest.connectAcceptingClient");
                    clientBooted.done(result1 -> System.out.println("FxTest.connectAcceptingClient.j89veaj4"));
                }));

            });
        });
    }

    public static String tmp;

    @Test
    public void connectAccepting() throws Exception {
        MeinLogger.redirectSysOut(100, true);
        File testdir = new File("testdir1");
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("Test Server").setGreeting("greeting1");
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        };
        IRegisteredHandler allowRegisteredHandler = (meinAuthService, registered) -> {
            DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
            for (ServiceJoinServiceType service : databaseManager.getAllServices()) {
                databaseManager.grant(service.getServiceId().v(), registered.getId().v());
            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinBoot boot1 = new MeinBoot(meinAuthSettings, DriveFXBootLoader.class);
        boot1.addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(allowRegisterHandler);
            meinAuthService.addRegisteredHandler(allowRegisteredHandler);
//            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                System.out.println("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                DriveCreateController createController = new DriveCreateController(meinAuthService);
                MeinDriveServerService serverService = createController.createDriveServerService("testiServer", testdir.getAbsolutePath());
                FxTest.tmp = serverService.getUuid();
                connectAcceptingClient();
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    ;

    @Test
    public void startAcceptingServer() throws Exception {
        File testdir = new File("testdir1");
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("Test Server").setGreeting("greeting1");
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        };
        IRegisteredHandler allowRegisteredHandler = (meinAuthService, registered) -> {
            DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
            for (ServiceJoinServiceType service : databaseManager.getAllServices()) {
                databaseManager.grant(service.getServiceId().v(), registered.getId().v());
            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinBoot boot1 = new MeinBoot(meinAuthSettings, DriveFXBootLoader.class, ContactsFXBootloader.class);
        boot1.addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(allowRegisterHandler);
            meinAuthService.addRegisteredHandler(allowRegisteredHandler);
//            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                System.out.println("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                DriveCreateController createController = new DriveCreateController(meinAuthService);
                createController.createDriveServerService("testiServer", testdir.getAbsolutePath());
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }


    @Test
    public void startBoth() throws Exception, SqlQueriesException {
//        inject(true);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("MA2").setGreeting("greeting2");

        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);

            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        };



/*
        IDBCreatedListener admin = databaseManager -> {
            ServiceType serviceType = databaseManager.createServiceType("test type", "test type desc");
            databaseManager.createService(serviceType.getId().v(), "service uuid");
        };*/
        //standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
        //standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
        /*standAloneAuth1.addRegisteredHandler((meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        });*/
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot(json1, DriveFXBootLoader.class);
        MeinBoot boot2 = new MeinBoot(json2, DriveFXBootLoader.class);
        boot1.boot().done(standAloneAuth1 -> {
            standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                System.out.println("FxTest.driveGui.1.booted");
//                DriveBootLoader.deVinjector = null;
                boot2.boot().done(standAloneAuth2 -> {
                    System.out.println("FxTest.driveGui.2.booted");
                    standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
                    runner.r(() -> {
//                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
//                        connectPromise.done(integer -> {
//                            runner.r(() -> {
//                                System.out.println("FxTest.driveGui.booted");
//                                //standAloneAuth2.getBrotCaster().discover(9966);
//                                //lock.unlockWrite();
//                            });
//                        });
                    });
                });
            });
        });
        lock.lockWrite();
        lock.lockWrite();
        lock.unlockWrite();
    }

    @Test
    public void driveGui() throws Exception, SqlQueriesException {
//        inject(true);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("MA2").setGreeting("greeting2");
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        };



/*
        IDBCreatedListener admin = databaseManager -> {
            ServiceType serviceType = databaseManager.createServiceType("test type", "test type desc");
            databaseManager.createService(serviceType.getId().v(), "service uuid");
        };*/
        //standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
        //standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
        /*standAloneAuth1.addRegisteredHandler((meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        });*/
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot(json1, DriveFXBootLoader.class);
        MeinBoot boot2 = new MeinBoot(json2, DriveFXBootLoader.class);
        boot1.boot().done(standAloneAuth1 -> {
            standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                System.out.println("FxTest.driveGui.1.booted");
//                DriveBootLoader.deVinjector = null;
                boot2.boot().done(standAloneAuth2 -> {
                    System.out.println("FxTest.driveGui.2.booted");
                    standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
                    runner.r(() -> {
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect("localhost", 8888, 8889, true);
                        connectPromise.done(integer -> {
                            runner.r(() -> {
                                System.out.println("FxTest.driveGui.booted");
                                //standAloneAuth2.getBrotCaster().discover(9966);
                                //lock.unlockWrite();
                            });
                        });
                    });
                });
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }


    public void setup(DriveSyncListener clientSyncListener) throws Exception, SqlQueriesException {
        //setup working directories & directories with test data
        File testdir1 = new File("testdir1");
        File testdir2 = new File("testdir2");
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir2);
        TestDirCreator.createTestDir(testdir1);
        testdir2.mkdirs();

        // configure MeinAuth
        N runner = new N(e -> e.printStackTrace());

        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("MA2").setGreeting("greeting2");
        // we want accept all registration attempts automatically
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);

            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        };
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot(json1, DriveFXBootLoader.class);
        MeinBoot boot2 = new MeinBoot(json2, DriveFXBootLoader.class);
        boot1.boot().done(standAloneAuth1 -> {
            runner.r(() -> {
                System.out.println("FxTest.driveGui.1.booted");
                standAloneAuth1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                MeinDriveServerService serverService = new DriveCreateController(standAloneAuth1).createDriveServerService("server service", testdir1.getAbsolutePath());
                boot2.boot().done(standAloneAuth2 -> {
                    System.out.println("FxTest.driveGui.2.booted");
                    standAloneAuth2.addRegisterHandler(allowRegisterHandler);

                    runner.r(() -> {
                        // connect first. this step will register
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect("localhost", 8888, 8889, true);
                        connectPromise.done(meinValidationProcess -> {
                            runner.r(() -> {
                                System.out.println("FxTest.driveGui.connected");
                                // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                                Promise<MeinDriveClientService, Exception, Void> promise = new DriveCreateController(standAloneAuth2).createDriveClientService("client service", testdir2.getAbsolutePath(), 1l, serverService.getUuid());
                                promise.done(clientDriveService -> runner.r(() -> {
                                            System.out.println("FxTest attempting first syncThisClient");
                                            clientSyncListener.testStructure.setMaClient(standAloneAuth2)
                                                    .setMaServer(standAloneAuth1)
                                                    .setClientDriveService(clientDriveService)
                                                    .setServerDriveService(serverService)
                                                    .setTestdir1(testdir1)
                                                    .setTestdir2(testdir2);
                                            clientDriveService.setSyncListener(clientSyncListener);
                                            clientDriveService.syncThisClient();
                                        }
                                ));
                            });
                        });
                    });
                });
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }


    @Test
    public void firstSync() throws Exception {
        DriveTest driveTest = new DriveTest();
        MeinBoot meinBoot = new MeinBoot(new DriveTest().createJson2(), DriveFXBootLoader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        driveTest.simpleTransferFromServerToClient(meinBoot);
        new WaitLock().lock().lock();
    }


    @Test
    public void addFile() throws Exception {
        setup(new DriveSyncListener() {
            private int count = 0;

            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

            }

            @Override
            public void onSyncDoneImpl() {
                try {
                    if (count == 0) {
                        DriveDatabaseManager dbManager = testStructure.clientDriveService.getDriveDatabaseManager();
                        List<FsFile> rootFiles = dbManager.getFsDao().getFilesByFsDirectory(null);
                        for (FsFile f : rootFiles) {
                            System.out.println(f.getName().v());
                        }
                        File newFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub2.txt");
                        newFile.createNewFile();
                    } else if (count == 1) {
                        System.out.println("FxTest.onSyncDoneImpl :)");
                        Map<Long, GenericFSEntry> entries1 = genList2Map(testStructure.serverDriveService.getDriveDatabaseManager().getFsDao().getDelta(0));
                        Map<Long, GenericFSEntry> entries2 = genList2Map(testStructure.clientDriveService.getDriveDatabaseManager().getFsDao().getDelta(0));
                        Map<Long, GenericFSEntry> cp1 = new HashMap<>(entries1);
                        cp1.forEach((id, entry) -> {
                            if (entries2.containsKey(id)) {
                                entries1.remove(id);
                                entries2.remove(id);
                            }
                        });
                        assertEquals(0, entries1.size());
                        assertEquals(0, entries2.size());
                        lock.unlockWrite();
                    }
                    count++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public Map<Long, GenericFSEntry> genList2Map(List<GenericFSEntry> entries) {
        Map<Long, GenericFSEntry> map = new HashMap<>();
        for (GenericFSEntry entry : entries) {
            map.put(entry.getId().v(), entry);
        }
        return map;
    }

    @After
    public void clean() {
        standAloneAuth1 = standAloneAuth2 = null;
        lock = null;
    }

    @Before
    public void before() {
        lock = new RWLock();
    }


}
