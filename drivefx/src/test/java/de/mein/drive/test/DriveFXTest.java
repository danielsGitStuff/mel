package de.mein.drive.test;


import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.gui.RegisterHandlerFX;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinStandAloneAuthFX;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.NoTryRunner;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.*;
import de.mein.drive.boot.DriveFXBootLoader;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.FsFile;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 09.09.2016.
 */
@SuppressWarnings("Duplicates")
public class DriveFXTest {

    private static MeinStandAloneAuthFX standAloneAuth2;
    private static MeinStandAloneAuthFX standAloneAuth1;
    private static RWLock lock = new RWLock();

    @Test
    public void startEmptyClient() throws Exception {
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        MeinBoot.addBootLoaderClass(DriveFXBootLoader.class);
        NoTryRunner runner = new NoTryRunner(e -> e.printStackTrace());
        MeinStandAloneAuthFX standAloneAuth1;
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("Test Client").setGreeting("greeting2");
        standAloneAuth1 = new MeinStandAloneAuthFX(json1);
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
        standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot();
        boot1.boot(standAloneAuth1).done(result -> {
            runner.run(() -> {
                System.out.println("DriveFXTest.startEmptyClient.booted");
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    @Test
    public void startEmptyServer() throws Exception {
//        inject(true);
        //CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
//        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        MeinBoot.addBootLoaderClass(DriveFXBootLoader.class);
        NoTryRunner runner = new NoTryRunner(e -> e.printStackTrace());
        MeinStandAloneAuthFX standAloneAuth1;
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("Test Server").setGreeting("greeting1");
        standAloneAuth1 = new MeinStandAloneAuthFX(json1);
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
        standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot();
        boot1.boot(standAloneAuth1).done(result -> {
            runner.run(() -> {
                System.out.println("DriveFXTest.startEmptyServer.booted");
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

//    private void inject(Boolean isServer) {
//        DriveBootLoader.deVinjector = new DriveBootLoader.DEVinjector() {
//            @Override
//            public void injectOnNoServices(DriveBootLoader driveBootLoader, MeinAuthService meinAuthService, ServiceType type) throws SqlQueriesException, JsonDeserializationException, JsonSerializationException, IOException, SQLException, IllegalAccessException, ClassNotFoundException {
//                DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
//                MeinDriveService meinDriveService = (isServer) ? new MeinDriveServerService(meinAuthService) : new MeinDriveClientService(meinAuthService);
//                Service service = databaseManager.createService(type.getId().v(), "injected name");
//                meinDriveService.setUuid(service.getUuid().v());
//                meinDriveService.start();
//                meinAuthService.registerMeinService(meinDriveService);
//                String role = (isServer) ? DriveStrings.ROLE_SERVER : DriveStrings.ROLE_CLIENT;
//                RootDirectory rootDir = new RootDirectory().setPath("/home/xor/Documents/seminar");
//                rootDir.setOriginalFile(new File("/home/xor/Documents/seminar"));
//                rootDir.backup();
//                driveBootLoader.startIndexer(meinDriveService, new DriveSettings().setRole(role).setRootDirectory(rootDir));
//            }
//        };
//    }
//
//    private void inject(Boolean isServer, File rootDirFile) {
//        DriveBootLoader.deVinjector = new DriveBootLoader.DEVinjector() {
//            @Override
//            public void injectOnNoServices(DriveBootLoader driveBootLoader, MeinAuthService meinAuthService, ServiceType type) throws SqlQueriesException, JsonDeserializationException, JsonSerializationException, IOException, SQLException, IllegalAccessException, ClassNotFoundException {
//                DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
//                MeinDriveService meinDriveService = (isServer) ? new MeinDriveServerService(meinAuthService) : new MeinDriveClientService(meinAuthService);
//                Service service = databaseManager.createService(type.getId().v(), "injected name");
//                meinDriveService.setUuid(service.getUuid().v());
//                meinDriveService.start();
//                meinAuthService.registerMeinService(meinDriveService);
//                String role = (isServer) ? DriveStrings.ROLE_SERVER : DriveStrings.ROLE_CLIENT;
//                RootDirectory rootDir = new RootDirectory().setPath(rootDirFile.getAbsolutePath());
//                rootDir.setOriginalFile(new File(rootDirFile.getAbsolutePath()));
//                rootDir.backup();
//                driveBootLoader.startIndexer(meinDriveService, new DriveSettings().setRole(role).setRootDirectory(rootDir));
//            }
//        };
//    }

    @Test
    public void startBoth() throws Exception, SqlQueriesException {
//        inject(true);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        MeinBoot.addBootLoaderClass(DriveFXBootLoader.class);
        NoTryRunner runner = new NoTryRunner(e -> e.printStackTrace());
        MeinStandAloneAuthFX standAloneAuth2;
        MeinStandAloneAuthFX standAloneAuth1;
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("MA2").setGreeting("greeting2");
        standAloneAuth1 = new MeinStandAloneAuthFX(json1);
        standAloneAuth2 = new MeinStandAloneAuthFX(json2);
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
        standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
        standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
        /*standAloneAuth1.addRegisteredHandler((meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        });*/
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot();
        MeinBoot boot2 = new MeinBoot();
        boot1.boot(standAloneAuth1).done(result -> {
            runner.run(() -> {
                System.out.println("DriveFXTest.driveGui.1.booted");
//                DriveBootLoader.deVinjector = null;
                boot2.boot(standAloneAuth2).done(result1 -> {
                    System.out.println("DriveFXTest.driveGui.2.booted");
                    runner.run(() -> {
                       /* Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
                        connectPromise.done(integer -> {
                            runner.run(() -> {
                                System.out.println("DriveFXTest.driveGui.booted");
                                //standAloneAuth2.getBrotCaster().discover(9966);
                                //lock.unlockWrite();
                            });
                        });*/
                    });
                });
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    @Test
    public void driveGui() throws Exception, SqlQueriesException {
//        inject(true);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        MeinBoot.addBootLoaderClass(DriveFXBootLoader.class);
        NoTryRunner runner = new NoTryRunner(e -> e.printStackTrace());
        MeinStandAloneAuthFX standAloneAuth2;
        MeinStandAloneAuthFX standAloneAuth1;
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("MA2").setGreeting("greeting2");
        standAloneAuth1 = new MeinStandAloneAuthFX(json1);
        standAloneAuth2 = new MeinStandAloneAuthFX(json2);
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
        standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
        standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
        /*standAloneAuth1.addRegisteredHandler((meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        });*/
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot();
        MeinBoot boot2 = new MeinBoot();
        boot1.boot(standAloneAuth1).done(result -> {
            runner.run(() -> {
                System.out.println("DriveFXTest.driveGui.1.booted");
//                DriveBootLoader.deVinjector = null;
                boot2.boot(standAloneAuth2).done(result1 -> {
                    System.out.println("DriveFXTest.driveGui.2.booted");
                    runner.run(() -> {
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
                        connectPromise.done(integer -> {
                            runner.run(() -> {
                                System.out.println("DriveFXTest.driveGui.booted");
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
        MeinBoot.addBootLoaderClass(DriveFXBootLoader.class);
        NoTryRunner runner = new NoTryRunner(e -> e.printStackTrace());

        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("MA2").setGreeting("greeting2");
        standAloneAuth1 = new MeinStandAloneAuthFX(json1);
        standAloneAuth2 = new MeinStandAloneAuthFX(json2);
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
        standAloneAuth1.addRegisterHandler(allowRegisterHandler);
        standAloneAuth2.addRegisterHandler(allowRegisterHandler);
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        standAloneAuth1.addRegisteredHandler(registeredHandler);
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot();
        MeinBoot boot2 = new MeinBoot();
        boot1.boot(standAloneAuth1).done(result -> {
            runner.run(() -> {
                System.out.println("DriveFXTest.driveGui.1.booted");
                // setup the server Service
                MeinDriveServerService serverService = new DriveCreateController(standAloneAuth1).createDriveServerService("server service", testdir1.getAbsolutePath());
                boot2.boot(standAloneAuth2).done(result1 -> {
                    System.out.println("DriveFXTest.driveGui.2.booted");
                    runner.run(() -> {
                        // connect first. this step will register
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
                        connectPromise.done(meinValidationProcess -> {
                            runner.run(() -> {
                                System.out.println("DriveFXTest.driveGui.connected");
                                // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                                Promise<MeinDriveClientService, Exception, Void> promise = new DriveCreateController(standAloneAuth2).createDriveClientService("client service", testdir2.getAbsolutePath(), 1l, serverService.getUuid());
                                promise.done(clientDriveService -> runner.run(() -> {
                                            System.out.println("DriveFXTest attempting first syncThisClient");
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
        setup(new DriveSyncListener() {

            @Override
            public void onSyncDoneImpl() {
                try {
                    if (getCount() == 0) {
                        DriveDatabaseManager dbManager = testStructure.clientDriveService.getDriveDatabaseManager();
                        List<FsFile> rootFiles = dbManager.getFsDao().getFilesByFsDirectory(null);
                        for (FsFile f : rootFiles) {
                            System.out.println(f.getName().v());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    @Test
    public void addFile() throws Exception {
        setup(new DriveSyncListener() {
            private int count = 0;

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
                        System.out.println("DriveFXTest.onSyncDoneImpl :)");
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
