package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinStrings;
import de.mein.auth.TestFileCreator;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.file.FFile;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.tools.Eva;
import de.mein.drive.DriveBootloader;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.sql.Hash;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
        AFile.configure(new DefaultFileConfiguration());
        lock = new RWLock();
    }

    //    @Test
    public void clientConflict() throws Exception {
        clientConflictImpl(null, null);
    }

    public void complexClientConflictImpl(MeinBoot clientMeinBoot, MeinBoot restartMeinBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final DriveSyncListener syncListener = new DriveSyncListener() {
            AFile file2;
            AFile file1;
            String rootPath;
            MeinDriveClientService meinDriveClientService;
            private DriveSyncListener ins = this;
            int count = 0;
            AtomicInteger failCount = new AtomicInteger(0);

            @Override
            public void onSyncFailed() {
                Lok.debug("DriveTest.onSyncFailed");
                if (failCount.getAndIncrement() == 0) {
                    N.r(() -> {
                        //if (!file2.exists())
                        Lok.debug("DriveTest.onSyncFailed.creating new file...");
                        rootPath = ins.testStructure.serverDriveService.getDriveSettings().getRootDirectory().getPath();
                        File delFile = new File(rootPath + File.separator + "samedir");
                        BashTools.rmRf(new FFile(delFile));
                        MeinBoot meinBoot = (restartMeinBoot != null) ? restartMeinBoot : new MeinBoot(json1, new PowerManager(json1), DriveBootloader.class);
                        Promise<MeinAuthService, Exception, Void> rebooted = meinBoot.boot();
                        rebooted.done(res -> N.r(() -> {
                            Lok.debug("DriveTest.alles ok");
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
            }

            private int transferCount = 0;

            @Override
            public void onTransfersDone() {
                if (transferCount == 0) {
                    N.r(() -> {
                        meinAuthService1.shutDown();
                        meinDriveClientService = (MeinDriveClientService) meinAuthService2.getMeinServices().iterator().next();
                        rootPath = ins.testStructure.clientDriveService.getDriveSettings().getRootDirectory().getPath();
                        file1 = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        file2 = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
                        TestFileCreator.saveFile("same1.client".getBytes(), file1);
                        TestFileCreator.saveFile("same2.client".getBytes(), file2);
                        File subDir = new File(rootPath + File.separator + "samedir" + File.separator + "samesub");
                        subDir.mkdirs();
                        AFile subFile = AFile.instance(subDir.getAbsolutePath() + File.separator + "samesub1.txt");
                        TestFileCreator.saveFile("samesub1.client".getBytes(), subFile);

                        String hash = Hash.md5(file1.inputStream());
                        Lok.debug("DriveTest.onTransfersDone.hash: " + file1 + " -> " + hash);
                        hash = Hash.md5((file2).inputStream());
                        Lok.debug("DriveTest.onTransfersDone.hash: " + file2 + " -> " + hash);
                    });

                }
                transferCount++;
            }

            @Override
            public void onSyncDoneImpl() {
                Lok.debug("DriveTest.onSyncDoneImpl");
                if (count == 1) {
                    Lok.debug("DriveTest.onSyncDoneImpl");
                }
                Lok.debug("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        };
        setup(false, syncListener, clientMeinBoot);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
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
                Lok.debug("DriveTest.onSyncDoneImpl");
                if (count == 1) {
                    Lok.debug("DriveTest.onSyncDoneImpl");
                }
                Lok.debug("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        };
        setup(null, syncListener, clientMeinBoot);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
    }

    public void clientConflictImpl(MeinBoot clientMeinBoot, MeinBoot restartMeinBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final DriveSyncListener syncListener = new DriveSyncListener() {
            AFile file2;
            AFile file1;
            String rootPath;
            MeinDriveClientService meinDriveClientService;
            private DriveSyncListener ins = this;
            int count = 0;
            int failCount = 0;

            @Override
            public void onSyncFailed() {
                Lok.debug("DriveTest.onSyncFailed");
                if (failCount == 0) {
                    N.r(() -> {
                        //if (!file2.exists())
                        Lok.debug("DriveTest.onSyncFailed.creating new file...");
                        rootPath = ins.testStructure.serverDriveService.getDriveSettings().getRootDirectory().getPath();
                        AFile newFile = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same3.txt");
                        AFile delFile = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
                        AFile f1 = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        delFile.delete();
                        TestFileCreator.saveFile("same3.server".getBytes(), newFile);
                        TestFileCreator.saveFile("same1.server".getBytes(), f1);
                        String hash = Hash.md5(f1.inputStream());
                        Lok.debug("DriveTest.onTransfersDone.hash: " + f1 + " -> " + hash);
                        hash = Hash.md5(newFile.inputStream());
                        Lok.debug("DriveTest.onTransfersDone.hash: " + newFile + " -> " + hash);
                        MeinBoot meinBoot = (restartMeinBoot != null) ? restartMeinBoot : new MeinBoot(json1, new PowerManager(json1), DriveBootloader.class);
                        Promise<MeinAuthService, Exception, Void> rebooted = meinBoot.boot();
                        rebooted.done(res -> N.r(() -> {
                            Lok.debug("DriveTest.alles ok");
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
                        file1 = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        file2 = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
                        TestFileCreator.saveFile("same1.client".getBytes(), file1);
                        TestFileCreator.saveFile("same2.client".getBytes(), file2);
                        String hash = Hash.md5(file1.inputStream());
                        Lok.debug("DriveTest.onTransfersDone.hash: " + file1 + " -> " + hash);
                        hash = Hash.md5(file2.inputStream());
                        Lok.debug("DriveTest.onTransfersDone.hash: " + file2 + " -> " + hash);
                    });

                }
                transferCount++;
            }

            @Override
            public void onSyncDoneImpl() {
                Lok.debug("DriveTest.onSyncDoneImpl");
                if (count == 1) {
                    Lok.debug("DriveTest.onSyncDoneImpl");
                }
                Lok.debug("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        };
        setup(false, syncListener, clientMeinBoot);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
    }

    public void simpleClientConflictImpl(MeinBoot clientMeinBoot, MeinBoot restartMeinBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final DriveSyncListener syncListener = new DriveSyncListener() {
            File file2;
            File file1;
            String rootPath;
            MeinDriveClientService meinDriveClientService;
            private DriveSyncListener ins = this;
            int count = 0;
            int failCount = 0;

            @Override
            public void onSyncFailed() {
                Lok.debug("DriveTest.onSyncFailed");
                if (failCount == 0) {
//                    N.r(() -> {
//                        //if (!file2.exists())
//                        Lok.debug("DriveTest.onSyncFailed.creating new file...");
//                        rootPath = ins.testStructure.serverDriveService.getDriveSettings().getRootDirectory().getPath();
//                        File newFile = new File(rootPath + File.separator + "samedir" + File.separator + "same3.txt");
//                        File delFile = new File(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
//                        File f1 = new File(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
//                        delFile.delete();
//                        TestFileCreator.saveFile("same3.server".getBytes(), newFile);
//                        TestFileCreator.saveFile("same1.server".getBytes(), f1);
//                        String hash = Hash.md5(f1);
//                        Lok.debug("DriveTest.onTransfersDone.hash: " + f1 + " -> " + hash);
//                        hash = Hash.md5(newFile);
//                        Lok.debug("DriveTest.onTransfersDone.hash: " + newFile + " -> " + hash);
//                        MeinBoot meinBoot = (restartMeinBoot != null) ? restartMeinBoot : new MeinBoot(json1, DriveBootloader.class);
//                        Promise<MeinAuthService, Exception, Void> rebooted = meinBoot.spawn();
//                        rebooted.done(res -> N.r(() -> {
//                            Lok.debug("DriveTest.alles ok");
////                            testStructure.setMaClient(meinAuthService2)
////                                    .setMaServer(meinAuthService1)
////                                    .setClientDriveService(clientDriveService)
////                                    .setServerDriveService(serverService)
////                                    .setTestdir1(testdir1)
////                                    .setTestdir2(testdir2);
////                            clientDriveService.setSyncListener(clientSyncListener);
//                        }));
//                    });
                }
                failCount++;
            }

            private int transferCount = 0;

            @Override
            public void onTransfersDone() {
//                if (transferCount == 0) {
//                    N.r(() -> {
//                        meinAuthService1.shutDown();
//                        meinDriveClientService = (MeinDriveClientService) meinAuthService2.getMeinServices().iterator().next();
//                        rootPath = ins.testStructure.clientDriveService.getDriveSettings().getRootDirectory().getPath();
//                        file1 = new File(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
//                        file2 = new File(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
//                        TestFileCreator.saveFile("same1.client".getBytes(), file1);
//                        TestFileCreator.saveFile("same2.client".getBytes(), file2);
//                        String hash = Hash.md5(file1);
//                        Lok.debug("DriveTest.onTransfersDone.hash: " + file1 + " -> " + hash);
//                        hash = Hash.md5(file2);
//                        Lok.debug("DriveTest.onTransfersDone.hash: " + file2 + " -> " + hash);
//                    });
//
//                }
//                transferCount++;
            }

            @Override
            public void onSyncDoneImpl() {
                Lok.debug("DriveTest.onSyncDoneImpl");
                if (count == 1) {
                    Lok.debug("DriveTest.onSyncDoneImpl");
                }
                Lok.debug("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        };
        setup(false, syncListener, clientMeinBoot);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
    }

    @Test
    public void firstTransfer() throws Exception {
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
                            Lok.debug(f.getName().v());
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
        Lok.debug("DriveTest.firstTransfer.END");
    }

    //    @Test
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
                    Promise<MeinIsolatedFileProcess, Exception, Void> isolated = testStructure.clientDriveService.getIsolatedProcess(MeinIsolatedFileProcess.class, certId, remoteServiceUuid);
                    isolated.done(meinIsolatedProcess -> {
                        Lok.debug("DriveTest.onSyncDoneImpl.SUCCESS");
                    }).fail(result -> {
                        Lok.debug("DriveTest.onSyncDoneImpl.FAIL");
                    });
                });

            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.isolation.END");
    }


    //    @Test
    public void clientMergeStages() throws Exception {
        // start both instances, shutdown server, change something in client directory
        setup(new DriveSyncListener() {
            AFile file2;
            AFile file1;
            String rootPath;
            MeinDriveClientService meinDriveClientService;
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
                Lok.debug("DriveTest.onSyncDoneImpl");
                if (count == 0) {
                    N.r(() -> {
                        meinAuthService1.shutDown();
                        meinDriveClientService = (MeinDriveClientService) meinAuthService2.getMeinServices().iterator().next();
                        rootPath = ins.testStructure.clientDriveService.getDriveSettings().getRootDirectory().getPath();
                        file1 = AFile.instance(rootPath + File.separator + "sub1" + File.separator + "newfile.1");
                        file2 = AFile.instance(rootPath + File.separator + "sub1" + File.separator + "newfile.2");
                        if (!file1.exists())
                            TestFileCreator.saveFile("newfile".getBytes(), file1);
                    });

                } else if (count == 1) {
                    Lok.debug("DriveTest.onSyncDoneImpl");
                    N.r(() -> {
                        if (!file2.exists())
                            TestFileCreator.saveFile("newfile.2".getBytes(), file1);
                    });
                }
                Lok.debug("DriveTest.onSyncDoneImpl.shot down." + count);
                count++;
            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
    }

    //    @Test
    public void restartServerAfterChangingFiles() throws Exception {
        CertificateManager.deleteDirectory(new FFile(MeinBoot.Companion.getDefaultWorkingDir1()));
        AFile testdir1 = AFile.instance("testdir1");
        CertificateManager.deleteDirectory(testdir1);
        TestDirCreator.createTestDir(testdir1);
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("MA1").setGreeting("greeting1");
        MeinBoot boot = new MeinBoot(json1, new PowerManager(json1));
        WaitLock waitLock = new WaitLock().lock();
        Promise<MeinAuthService, Exception, Void> promise = boot.boot();
        final MeinAuthService[] mas = new MeinAuthService[1];
        promise.done(result -> N.r(() -> {
            mas[0] = result;
            Promise<MeinDriveServerService, Exception, Void> driveBootedPromise = new DriveCreateController(result)
                    .createDriveServerServiceDeferred("server test", testdir1, 0.01f, 30);
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
        AFile newFile = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same3.txt");
        AFile delFile = AFile.instance(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
        delFile.delete();
        TestFileCreator.saveFile("newfile.2".getBytes(), newFile);
        boot = new MeinBoot(json1, new PowerManager(json1));
        promise = boot.boot();
        promise.done(result -> N.r(() -> {
            mas[0] = result;
        }));
        waitLock.lock().lock();
    }

    //@Test
    public void startSingleServer() throws Exception {
        startServer();
        RWLock lock = new RWLock();
        lock.lockWrite();
        lock.lockWrite();
        Lok.debug("DriveTest.startSingleServer.END");
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
                            Lok.debug(f.getName().v());
                        }
                        // TODO: checks go here
                        //lock.unlockWrite();
                    }
                    Lok.debug("DriveTest.onSyncDoneImpl.EEEEEEEEEEE " + getCount());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.firstSync.END");
    }

    //    @Test
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
                            Lok.debug(f.getName().v());
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
        Lok.debug("DriveTest.firstSync.END");
    }

    //    @Test
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
                            Lok.debug(f.getName().v());
                        }
                        File newFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub3.txt");
                        newFile.createNewFile();
                    } else if (getCount() == 1) {
                        Lok.debug("DriveFXTest.onSyncDoneImpl :)");
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
        Lok.debug("DriveTest.addFile.END");
    }

    //    @Test
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
                            Lok.debug(f.getName().v());
                        }
                        File deleteFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub2.txt");
                        deleteFile.delete();
                    } else if (getCount() == 1) {
                        Lok.debug("DriveFXTest.onSyncDoneImpl :)");
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
        Lok.debug("DriveTest.deleteFsEntry.END");
    }

    private Map<Long, GenericFSEntry> genList2Map(List<GenericFSEntry> entries) {
        Map<Long, GenericFSEntry> map = new HashMap<>();
        for (GenericFSEntry entry : entries) {
            map.put(entry.getId().v(), entry);
        }
        return map;
    }

    private void setup(DriveSyncListener clientSyncListener) throws Exception {
        setup(false, clientSyncListener, null);
    }


    private void startServer() throws Exception {
        //setup working directories & directories with test data
        AFile testdir1 = AFile.instance("testdir1");
        AFile testdir2 = AFile.instance("testdir2");
        CertificateManager.deleteDirectory(new FFile(MeinBoot.Companion.getDefaultWorkingDir1()));
        //CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir1);
        TestDirCreator.createTestDir(testdir1);


        // configure MeinAuth
        N runner = new N(e -> e.printStackTrace());

        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("MA1").setGreeting("greeting1");
        // we want accept all registration attempts automatically
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {

            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
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
            public void setup(MeinAuthAdmin meinAuthAdmin) {

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

        MeinBoot boot1 = new MeinBoot(json1, new PowerManager(json1), DriveBootloader.class);
        boot1.boot().done(ma1 -> {
            runner.runTry(() -> {
                Lok.debug("DriveTest.driveGui.booted");
                meinAuthService1 = ma1;
                meinAuthService1.addRegisterHandler(allowRegisterHandler);
                meinAuthService1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                RWLock lock = new RWLock().lockWrite();
                DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
                    lock.lockWrite();
                };
                new DriveCreateController(meinAuthService1).createDriveServerService("server service", testdir1, 0.01f, 30, false);
                lock.lockWrite();
                Lok.debug("DriveTest.startServer.booted");
            });
        });

    }

    /**
     * @param identicalTestDirs  does not create a second directory if null
     * @param clientSyncListener
     * @param clientMeinBoot
     */
    private void setup(Boolean identicalTestDirs, DriveSyncListener clientSyncListener, MeinBoot clientMeinBoot) throws Exception {
        //setup working directories & directories with test data
        AFile testdir1 = AFile.instance("testdir1");
        AFile testdir2 = AFile.instance("testdir2");
        CertificateManager.deleteDirectory(new FFile(MeinBoot.Companion.getDefaultWorkingDir1()));
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir2());
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir2);
        TestDirCreator.createTestDir(testdir1, 1);
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
                Lok.debug("registration complete");
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
            public void setup(MeinAuthAdmin meinAuthAdmin) {

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

        MeinBoot boot1 = new MeinBoot(json1, new PowerManager(json1), DriveBootloader.class);
        MeinBoot boot2;
        if (clientMeinBoot != null)
            boot2 = clientMeinBoot;
        else
            boot2 = new MeinBoot(json2, new PowerManager(json2));
        boot1.boot().done(ma1 -> {
            runner.runTry(() -> {
                Lok.debug("DriveFXTest.driveGui.1.booted");
                meinAuthService1 = ma1;
                meinAuthService1.addRegisterHandler(allowRegisterHandler);
                meinAuthService1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> N.r(() -> {
                    MeinDriveServerService serverService = (MeinDriveServerService) driveService;
                    boot2.boot().done(ma2 -> {
                        Lok.debug("DriveFXTest.driveGui.2.booted");
                        meinAuthService2 = ma2;
                        meinAuthService2.addRegisterHandler(allowRegisterHandler);
                        meinAuthService2.addRegisteredHandler((meinAuthService, registered) -> runner.runTry(() -> {
                            // connect first. this step will register
                            Promise<MeinValidationProcess, Exception, Void> connectPromise = meinAuthService2.connect("localhost", 8888, 8889, false);
                            connectPromise.done(meinValidationProcess -> new Thread(() -> {
                                        runner.runTry(() -> {
                                            Lok.debug("DriveFXTest.driveGui.connected");
                                            // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                                            DriveBootloader.DEV_DRIVE_BOOT_LISTENER = clientDriveService -> {
                                                Lok.debug("DriveFXTest attempting first syncFromServer");
                                                clientSyncListener.testStructure.setMaClient(meinAuthService2)
                                                        .setMaServer(meinAuthService1)
                                                        .setClientDriveService((MeinDriveClientService) clientDriveService)
                                                        .setServerDriveService(serverService)
                                                        .setTestdir1(testdir1)
                                                        .setTestdir2(testdir2);
                                                clientDriveService.setSyncListener(clientSyncListener);
                                            };
                                            new DriveCreateController(meinAuthService2).createDriveClientService("client service", testdir2, 1l, serverService.getUuid(), 0.01f, 30, false);
                                        });
                                    }).start()
                            );

                        }));
                        runner.runTry(() -> {
                            meinAuthService2.connect("localhost", 8888, 8889, true);
                        });
                    });
                });
                new DriveCreateController(meinAuthService1).createDriveServerService("server service", testdir1, 0.01f, 30, false);
            });
        });
        //lock.lockWrite();
        //lock.unlockWrite();
    }

    public static MeinAuthSettings createJson2() {
        MeinAuthSettings settings = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir2()).setName("MA2").setGreeting("greeting2").setVariant(MeinStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(MeinBoot.Companion.getDefaultWorkingDir2(), MeinBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        return settings;
    }

    public static MeinAuthSettings createJson1() {
        MeinAuthSettings settings = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("MA1").setGreeting("greeting1").setVariant(MeinStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(MeinBoot.Companion.getDefaultWorkingDir1(), MeinBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        return settings;
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
