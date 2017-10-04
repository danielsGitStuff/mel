package de.mein.drive.serialization;

import de.mein.auth.TestFileCreator;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.sql.Hash;
import de.mein.auth.tools.Lok;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.bash.BashTools;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.sql.RWLock;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 12/12/16.
 */
@SuppressWarnings("Duplicates")
public class DriveTest {
    private static MeinAuthService meinAuthService1;
    private static MeinAuthService meinAuthService2;
    private static RWLock lock = new RWLock();
    private static N runner = new N(Throwable::printStackTrace);
    private static MeinAuthSettings json2;
    private static MeinAuthSettings json1;

    private static void run(N.INoTryRunnable noTryRunnable) {
        runner.runTry(noTryRunnable);
    }

    @After
    public void clean() {
        meinAuthService1 = meinAuthService2 = null;
        lock = null;
    }

    @Before
    public void before() {
        lock = new RWLock();
    }

    @Test
    public void clientConflict() throws Exception {
        clientConflictImpl(null, null);
    }

    public void complexClientConflictImpl(MeinBoot clientMeinBoot, MeinBoot restartMeinBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final DriveSyncListener syncListener = new DriveSyncListener() {
            public File file2;
            public File file1;
            public String rootPath;
            public MeinDriveClientService meinDriveClientService;
            private DriveSyncListener ins = this;
            int count = 0;
            int failCount = 0;

            @Override
            public void onSyncFailed() {
                System.out.println("DriveTest.onSyncFailed");
                if (failCount == 0) {
                    N.r(() -> {
                        //if (!file2.exists())
                        System.out.println("DriveTest.onSyncFailed.creating new file...");
                        rootPath = ins.testStructure.serverDriveService.getDriveSettings().getRootDirectory().getPath();
                        File delFile = new File(rootPath + File.separator + "samedir");
                        BashTools.rmRf(delFile);
                        MeinBoot meinBoot = (restartMeinBoot != null) ? restartMeinBoot : new MeinBoot(json1, DriveBootLoader.class);
                        Promise<MeinAuthService, Exception, Void> rebooted = meinBoot.boot();
                        rebooted.done(res -> N.r(() -> {
                            System.out.println("DriveTest.alles ok");
//                            testStructure.setMaClient(meinAuthService2)
//                                    .setMaServer(meinAuthService1)
//                                    .setClientDriveService(clientDriveService)
//                                    .setServerDriveService(serverService)
//                                    .setTestdir1(testdir1)
//                                    .setTestdir2(testdir2);
//                            clientDriveService.setSyncListener(clientSyncListener);
                        }));
                    });
                }
                failCount++;
            }

            private int transferCount = 0;

            @Override
            public void onTransfersDone() {
                if (transferCount == 0) {
                    N.r(() -> {
                        meinAuthService1.shutDown();
                        meinDriveClientService = (MeinDriveClientService) meinAuthService2.getMeinServices().iterator().next();
                        rootPath = ins.testStructure.clientDriveService.getDriveSettings().getRootDirectory().getPath();
                        file1 = new File(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        file2 = new File(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
                        TestFileCreator.saveFile("same1.client".getBytes(), file1);
                        TestFileCreator.saveFile("same2.client".getBytes(), file2);
                        File subDir = new File(rootPath + File.separator + "samedir" + File.separator + "samesub");
                        subDir.mkdirs();
                        File subFile = new File(subDir.getAbsolutePath() + File.separator + "samesub1.txt");
                        TestFileCreator.saveFile("samesub1.client".getBytes(), subFile);

                        String hash = Hash.md5(file1);
                        System.out.println("DriveTest.onTransfersDone.hash: " + file1 + " -> " + hash);
                        hash = Hash.md5(file2);
                        System.out.println("DriveTest.onTransfersDone.hash: " + file2 + " -> " + hash);
                    });

                }
                transferCount++;
            }

            @Override
            public void onSyncDoneImpl() {
                System.out.println("DriveTest.onSyncDoneImpl");
                if (count == 1) {
                    System.out.println("DriveTest.onSyncDoneImpl");
                }
                System.out.println("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        };
        setup(false, syncListener, clientMeinBoot);
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.clientMergeStages.END");
    }

    public void simpleTransferFromServerToClient(MeinBoot clientMeinBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final DriveSyncListener syncListener = new DriveSyncListener() {
            public File file2;
            public File file1;
            public String rootPath;
            public MeinDriveClientService meinDriveClientService;
            private DriveSyncListener ins = this;
            int count = 0;

            @Override
            public void onSyncFailed() {
            }

            private int transferCount = 0;

            @Override
            public void onTransfersDone() {
            }

            @Override
            public void onSyncDoneImpl() {
                System.out.println("DriveTest.onSyncDoneImpl");
                if (count == 1) {
                    System.out.println("DriveTest.onSyncDoneImpl");
                }
                System.out.println("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        };
        setup(null, syncListener, clientMeinBoot);
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.clientMergeStages.END");
    }

    public void clientConflictImpl(MeinBoot clientMeinBoot, MeinBoot restartMeinBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final DriveSyncListener syncListener = new DriveSyncListener() {
            public File file2;
            public File file1;
            public String rootPath;
            public MeinDriveClientService meinDriveClientService;
            private DriveSyncListener ins = this;
            int count = 0;
            int failCount = 0;

            @Override
            public void onSyncFailed() {
                System.out.println("DriveTest.onSyncFailed");
                if (failCount == 0) {
                    N.r(() -> {
                        //if (!file2.exists())
                        System.out.println("DriveTest.onSyncFailed.creating new file...");
                        rootPath = ins.testStructure.serverDriveService.getDriveSettings().getRootDirectory().getPath();
                        File newFile = new File(rootPath + File.separator + "samedir" + File.separator + "same3.txt");
                        File delFile = new File(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
                        File f1 = new File(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        delFile.delete();
                        TestFileCreator.saveFile("same3.server".getBytes(), newFile);
                        TestFileCreator.saveFile("same1.server".getBytes(), f1);
                        String hash = Hash.md5(f1);
                        System.out.println("DriveTest.onTransfersDone.hash: " + f1 + " -> " + hash);
                        hash = Hash.md5(newFile);
                        System.out.println("DriveTest.onTransfersDone.hash: " + newFile + " -> " + hash);
                        MeinBoot meinBoot = (restartMeinBoot != null) ? restartMeinBoot : new MeinBoot(json1, DriveBootLoader.class);
                        Promise<MeinAuthService, Exception, Void> rebooted = meinBoot.boot();
                        rebooted.done(res -> N.r(() -> {
                            System.out.println("DriveTest.alles ok");
//                            testStructure.setMaClient(meinAuthService2)
//                                    .setMaServer(meinAuthService1)
//                                    .setClientDriveService(clientDriveService)
//                                    .setServerDriveService(serverService)
//                                    .setTestdir1(testdir1)
//                                    .setTestdir2(testdir2);
//                            clientDriveService.setSyncListener(clientSyncListener);
                        }));
                    });
                }
                failCount++;
            }

            private int transferCount = 0;

            @Override
            public void onTransfersDone() {
                if (transferCount == 0) {
                    N.r(() -> {
                        meinAuthService1.shutDown();
                        meinDriveClientService = (MeinDriveClientService) meinAuthService2.getMeinServices().iterator().next();
                        rootPath = ins.testStructure.clientDriveService.getDriveSettings().getRootDirectory().getPath();
                        file1 = new File(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        file2 = new File(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
                        TestFileCreator.saveFile("same1.client".getBytes(), file1);
                        TestFileCreator.saveFile("same2.client".getBytes(), file2);
                        String hash = Hash.md5(file1);
                        System.out.println("DriveTest.onTransfersDone.hash: " + file1 + " -> " + hash);
                        hash = Hash.md5(file2);
                        System.out.println("DriveTest.onTransfersDone.hash: " + file2 + " -> " + hash);
                    });

                }
                transferCount++;
            }

            @Override
            public void onSyncDoneImpl() {
                System.out.println("DriveTest.onSyncDoneImpl");
                if (count == 1) {
                    System.out.println("DriveTest.onSyncDoneImpl");
                }
                System.out.println("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        };
        setup(false, syncListener, clientMeinBoot);
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.clientMergeStages.END");
    }

    @Test
    public void firstTransfer() throws Exception {
        System.setOut(new Lok(System.out).setPrint(false));
        setup(new DriveSyncListener() {

            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

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
        System.out.println("DriveTest.firstTransfer.END");
    }

    @Test
    public void isolation() throws Exception {
        setup(new DriveSyncListener() {
            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

            }

            @Override
            public void onSyncDoneImpl() {
                run(() -> {
                    Long certId = testStructure.clientDriveService.getDriveSettings().getClientSettings().getServerCertId();
                    String remoteServiceUuid = testStructure.clientDriveService.getDriveSettings().getClientSettings().getServerServiceUuid();
                    String ownServiceUuid = testStructure.clientDriveService.getUuid();
//                 String address, int port, int portCert
                    DeferredObject<MeinIsolatedFileProcess, Exception, Void> isolated = testStructure.maClient.connectToService(MeinIsolatedFileProcess.class, certId, remoteServiceUuid, ownServiceUuid, null, null, null);
                    isolated.done(meinIsolatedProcess -> {
                        System.out.println("DriveTest.onSyncDoneImpl.SUCCESS");
                    }).fail(result -> {
                        System.out.println("DriveTest.onSyncDoneImpl.FAIL");
                    });
                });

            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.isolation.END");
    }


    @Test
    public void clientMergeStages() throws Exception {
        // start both instances, shutdown server, change something in client directory
        setup(new DriveSyncListener() {
            public File file2;
            public File file1;
            public String rootPath;
            public MeinDriveClientService meinDriveClientService;
            private DriveSyncListener ins = this;
            int count = 0;

            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

            }

            @Override
            public void onSyncDoneImpl() {
                System.out.println("DriveTest.onSyncDoneImpl");
                if (count == 0) {
                    N.r(() -> {
                        meinAuthService1.shutDown();
                        meinDriveClientService = (MeinDriveClientService) meinAuthService2.getMeinServices().iterator().next();
                        rootPath = ins.testStructure.clientDriveService.getDriveSettings().getRootDirectory().getPath();
                        file1 = new File(rootPath + File.separator + "sub1" + File.separator + "newfile.1");
                        file2 = new File(rootPath + File.separator + "sub1" + File.separator + "newfile.2");
                        if (!file1.exists())
                            TestFileCreator.saveFile("newfile".getBytes(), file1);
                    });

                } else if (count == 1) {
                    System.out.println("DriveTest.onSyncDoneImpl");
                    N.r(() -> {
                        if (!file2.exists())
                            TestFileCreator.saveFile("newfile.2".getBytes(), file1);
                    });
                }
                System.out.println("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.clientMergeStages.END");
    }

    @Test
    public void restartServerAfterChangingFiles() throws Exception {
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        File testdir1 = new File("testdir1");
        CertificateManager.deleteDirectory(testdir1);
        TestDirCreator.createTestDir(testdir1);
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
        MeinBoot boot = new MeinBoot(json1);
        WaitLock waitLock = new WaitLock().lock();
        Promise<MeinAuthService, Exception, Void> promise = boot.boot();
        final MeinAuthService[] mas = new MeinAuthService[1];
        promise.done(result -> N.r(() -> {
            mas[0] = result;
            Promise<MeinDriveServerService, Exception, Void> driveBootedPromise = new DriveCreateController(result)
                    .createDriveServerServiceDeferred("server test", testdir1.getAbsolutePath());
            driveBootedPromise.done(result1 -> N.r(() -> {
                result1.getIndexer().getIndexerStartedDeferred().done(result2 -> N.r(() -> {
                    result.shutDown();
                    waitLock.unlock();
                }));

            }));
        }));
        waitLock.lock();
        MeinDriveServerService driveServerService = (MeinDriveServerService) mas[0].getMeinServices().iterator().next();
        String rootPath = driveServerService.getDriveSettings().getRootDirectory().getPath();
        File newFile = new File(rootPath + File.separator + "samedir" + File.separator + "same3.txt");
        File delFile = new File(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
        delFile.delete();
        TestFileCreator.saveFile("newfile.2".getBytes(), newFile);
        boot = new MeinBoot(json1);
        promise = boot.boot();
        promise.done(result -> N.r(() -> {
            mas[0] = result;
        }));
        waitLock.lock().lock();
    }

    @Test
    public void startSingleServer() throws Exception {
        startServer();
        RWLock lock = new RWLock();
        lock.lockWrite();
        lock.lockWrite();
        System.out.println("DriveTest.startSingleServer.END");
    }

    @Test
    public void firstSyncServer2Client() throws Exception {
        setup(new DriveSyncListener() {

            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

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
                    System.out.println("DriveTest.onSyncDoneImpl.EEEEEEEEEEE " + getCount());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.firstSync.END");
    }

    @Test
    public void firstSyncClient2Server() throws Exception {
        setup(true, new DriveSyncListener() {

            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

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
                        lock.unlockWrite();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, null);
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.firstSync.END");
    }

    @Test
    public void addFile() throws Exception {
        setup(new DriveSyncListener() {

            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

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
                        File newFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub3.txt");
                        newFile.createNewFile();
                    } else if (getCount() == 1) {
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.addFile.END");
    }

    @Test
    public void deleteFile() throws Exception {
        setup(true, new DriveSyncListener() {

            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

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
                        File deleteFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub2.txt");
                        deleteFile.delete();
                    } else if (getCount() == 1) {
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, null);
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.deleteFsEntry.END");
    }

    public Map<Long, GenericFSEntry> genList2Map(List<GenericFSEntry> entries) {
        Map<Long, GenericFSEntry> map = new HashMap<>();
        for (GenericFSEntry entry : entries) {
            map.put(entry.getId().v(), entry);
        }
        return map;
    }

    public void setup(DriveSyncListener clientSyncListener) throws Exception {
        setup(false, clientSyncListener, null);
    }


    public void startServer() throws Exception {
        //setup working directories & directories with test data
        File testdir1 = new File("testdir1");
        File testdir2 = new File("testdir2");
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        //CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir2);
        TestDirCreator.createTestDir(testdir1);


        // configure MeinAuth
        N runner = new N(e -> e.printStackTrace());

        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
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

        MeinBoot boot1 = new MeinBoot(json1, DriveBootLoader.class);
        boot1.boot().done(ma1 -> {
            runner.runTry(() -> {
                System.out.println("DriveTest.driveGui.booted");
                meinAuthService1 = ma1;
                meinAuthService1.addRegisterHandler(allowRegisterHandler);
                meinAuthService1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                MeinDriveServerService serverService = new DriveCreateController(meinAuthService1).createDriveServerService("server service", testdir1.getAbsolutePath());
                System.out.println("DriveTest.startServer.booted");
            });
        });

    }

    /**
     * @param identicalTestDirs  does not create a second directory if null
     * @param clientSyncListener
     * @param clientMeinBoot
     * @throws Exception
     */
    public void setup(Boolean identicalTestDirs, DriveSyncListener clientSyncListener, MeinBoot clientMeinBoot) throws Exception {
        //setup working directories & directories with test data
        File testdir1 = new File("testdir1");
        File testdir2 = new File("testdir2");
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir2);
        TestDirCreator.createTestDir(testdir1);
        if (identicalTestDirs != null) {
            if (identicalTestDirs)
                TestDirCreator.createTestDir(testdir2);
            else
                TestDirCreator.createTestDir(testdir2, " lel");
        }


        // configure MeinAuth
        N runner = new N(e -> e.printStackTrace());

        json1 = createJson1();
        json2 = createJson2();
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

        MeinBoot boot1 = new MeinBoot(json1, DriveBootLoader.class);
        MeinBoot boot2;
        if (clientMeinBoot != null)
            boot2 = clientMeinBoot;
        else
            boot2 = new MeinBoot(json2);
        boot1.boot().done(ma1 -> {
            runner.runTry(() -> {
                System.out.println("DriveFXTest.driveGui.1.booted");
                meinAuthService1 = ma1;
                meinAuthService1.addRegisterHandler(allowRegisterHandler);
                meinAuthService1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                MeinDriveServerService serverService = new DriveCreateController(meinAuthService1).createDriveServerService("server service", testdir1.getAbsolutePath());
                boot2.boot().done(ma2 -> {
                    System.out.println("DriveFXTest.driveGui.2.booted");
                    meinAuthService2 = ma2;
                    meinAuthService2.addRegisterHandler(allowRegisterHandler);
                    runner.runTry(() -> {
                        // connect first. this step will register
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = meinAuthService2.connect( "localhost", 8888, 8889, true);
                        connectPromise.done(meinValidationProcess -> {
                            runner.runTry(() -> {
                                System.out.println("DriveFXTest.driveGui.connected");
                                // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                                Promise<MeinDriveClientService, Exception, Void> promise = new DriveCreateController(meinAuthService2).createDriveClientService("client service", testdir2.getAbsolutePath(), 1l, serverService.getUuid());
                                promise.done(clientDriveService -> runner.runTry(() -> {
                                            System.out.println("DriveFXTest attempting first syncThisClient");
                                            clientSyncListener.testStructure.setMaClient(meinAuthService2)
                                                    .setMaServer(meinAuthService1)
                                                    .setClientDriveService(clientDriveService)
                                                    .setServerDriveService(serverService)
                                                    .setTestdir1(testdir1)
                                                    .setTestdir2(testdir2);
                                            clientDriveService.setSyncListener(clientSyncListener);
                                            //clientDriveService.syncThisClient();
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

    public static MeinAuthSettings createJson2() {
        return new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("MA2").setGreeting("greeting2");
    }

    public static MeinAuthSettings createJson1() {
        return new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("MA1").setGreeting("greeting1");
    }

    public void startUpConflicts(MeinBoot meinBoot) throws Exception {
        setup(false, new DriveSyncListener() {
            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

            }

            @Override
            public void onSyncDoneImpl() {

            }
        }, meinBoot);
    }
}
