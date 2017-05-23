package de;

import de.mein.auth.service.MeinBoot;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsFile;
import de.mein.sql.RWLock;

import org.jdeferred.Promise;

import java.io.File;
import java.util.List;

/**
 * Created by xor on 2/2/17.
 */
public class Test1 {
    private static MeinAuthService standAloneAuth1;
    private static MeinAuthService standAloneAuth2;
    private static RWLock lock = new RWLock();

    public static void main(String... args) throws Exception {
        setup(new DriveSyncListener() {

            @Override
            public void onSyncFailed() {
                System.out.println("Test1.onSyncFailed");
            }

            @Override
            public void onSyncDoneImpl() {
                try {
                    if (getCount() == 0) {
                        DriveDatabaseManager dbManager = testStructure.clientDriveService.getDriveDatabaseManager();
                        List<FsFile> rootFiles = dbManager.getFsDao().getFilesByFsDirectory(null);
                        for (FsFile f : rootFiles) {
                            System.out.println(f.getName().v());
                        }
                        // TODO: checks go here
                        //lock.unlockWrite();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.firstSync.END");
    }

    public static void setup(DriveSyncListener clientSyncListener) throws Exception {
        setup(false, clientSyncListener);
    }

    public static void setup(boolean identicalTestDirs, DriveSyncListener clientSyncListener) throws Exception {
        //setup working directories & directories with test data
        File testdir1 = new File("testdir1");
        File testdir2 = new File("testdir2");
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir2);
        TestDirCreator.createTestDir(testdir1);
        if (identicalTestDirs)
            TestDirCreator.createTestDir(testdir2);
        else
            testdir2.mkdirs();


        // configure MeinAuth
        MeinBoot.addBootLoaderClass(DriveBootLoader.class);
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

        MeinBoot boot1 = new MeinBoot(json1);
        MeinBoot boot2 = new MeinBoot(json2);
        boot1.boot().done(ma1 -> {
            runner.runTry(() -> {
                System.out.println("DriveFXTest.driveGui.1.booted");
                standAloneAuth1 = ma1;
                standAloneAuth1.addRegisteredHandler(registeredHandler);
                standAloneAuth1.addRegisterHandler(allowRegisterHandler);
                // setup the server Service
                MeinDriveServerService serverService = new DriveCreateController(standAloneAuth1).createDriveServerService("server service", testdir1.getAbsolutePath());
                boot2.boot().done(ma2 -> {
                    System.out.println("DriveFXTest.driveGui.2.booted");
                    standAloneAuth2 = ma2;
                    standAloneAuth2.addRegisterHandler(allowRegisterHandler);
                    runner.runTry(() -> {
                        // connect first. this step will register
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
                        connectPromise.done(meinValidationProcess -> {
                            runner.runTry(() -> {
                                System.out.println("DriveFXTest.driveGui.connected");
                                // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                                Promise<MeinDriveClientService, Exception, Void> promise = new DriveCreateController(standAloneAuth2).createDriveClientService("client service", testdir2.getAbsolutePath(), 1l, serverService.getUuid());
                                promise.done(clientDriveService -> runner.runTry(() -> {
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
        //lock.lockWrite();
        //lock.unlockWrite();
    }
}
