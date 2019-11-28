package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelStrings;
import de.mel.auth.TestFileCreator;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.file.IFile;
import de.mel.auth.file.StandardFile;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.socket.process.reg.IRegisteredHandler;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.ShutDownDeferredManager;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.serialization.TestDirCreator;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.sql.Hash;
import de.mel.auth.tools.N;
import de.mel.auth.tools.WaitLock;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.FileSyncSyncListener;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.FsFile;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.sql.RWLock;
import org.jdeferred.Promise;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Created by xor on 12/12/16.
 */
@SuppressWarnings("Duplicates")
public class DriveTest {
    private static MelAuthService melAuthService1;
    private static MelAuthService melAuthService2;
    private static RWLock lock = new RWLock();
    private static N runner = new N(Throwable::printStackTrace);
    private static MelAuthSettings json2;
    private static MelAuthSettings json1;
    private IFile testdir1;
    private IFile testdir2;

    private static void run(N.INoTryRunnable noTryRunnable) {
        runner.runTry(noTryRunnable);
    }

    @After
    public void after() throws IOException {
        CountLock shutdownLock = new CountLock().lock();
        ShutDownDeferredManager shut = new ShutDownDeferredManager();
        N.r(() -> shut.when(melAuthService1.shutDown()));
        N.r(() -> shut.when(melAuthService2.shutDown()));
        shut.digest().done(result -> shutdownLock.unlock());
        shutdownLock.lock();
        melAuthService1 = melAuthService2 = null;
        lock = null;
        BashTools.Companion.rmRf(MelBoot.Companion.getDefaultWorkingDir1());
        BashTools.Companion.rmRf(MelBoot.Companion.getDefaultWorkingDir2());
        BashTools.Companion.rmRf(testdir1);
        BashTools.Companion.rmRf(testdir2);
    }

    @Before
    public void before() throws IOException {
        AbstractFile.configure(new DefaultFileConfiguration());
        BashTools.Companion.init();
        lock = new RWLock();
        testdir1 = AbstractFile.instance("testdir1");
        testdir2 = AbstractFile.instance("testdir2");
        BashTools.Companion.rmRf(MelBoot.Companion.getDefaultWorkingDir1());
        BashTools.Companion.rmRf(MelBoot.Companion.getDefaultWorkingDir2());
        BashTools.Companion.rmRf(testdir1);
        BashTools.Companion.rmRf(testdir2);
    }

    //    @Test
    public void clientConflict() throws Exception {
        clientConflictImpl(null, null);
    }

    public void complexClientConflictImpl(MelBoot clientMelBoot, MelBoot restartMelBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final FileSyncSyncListener syncListener = new FileSyncSyncListener() {
            IFile file2;
            IFile file1;
            String rootPath;
            MelFileSyncClientService melDriveClientService;
            private FileSyncSyncListener ins = this;
            int count = 0;
            AtomicInteger failCount = new AtomicInteger(0);

            @Override
            public void onSyncFailed() {
                Lok.debug("DriveTest.onSyncFailed");
                if (failCount.getAndIncrement() == 0) {
                    N.r(() -> {
                        //if (!file2.exists())
                        Lok.debug("DriveTest.onSyncFailed.creating new file...");
                        rootPath = ins.testStructure.serverDriveService.getFileSyncSettings().getRootDirectory().getPath();
                        File delFile = new File(rootPath + File.separator + "samedir");
                        BashTools.Companion.rmRf(new StandardFile(delFile));
                        MelBoot melBoot = (restartMelBoot != null) ? restartMelBoot : new MelBoot(json1, new PowerManager(json1), FileSyncBootloader.class);
                        Promise<MelAuthService, Exception, Void> rebooted = melBoot.boot();
                        rebooted.done(res -> N.r(() -> {
                            Lok.debug("DriveTest.alles ok");
//                            testStructure.setMaClient(melAuthService2)
//                                    .setMaServer(melAuthService1)
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
                        melAuthService1.shutDown();
                        melDriveClientService = (MelFileSyncClientService) melAuthService2.getMelServices().iterator().next();
                        rootPath = ins.testStructure.clientDriveService.getFileSyncSettings().getRootDirectory().getPath();
                        file1 = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        file2 = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
                        TestFileCreator.saveFile("same1.client".getBytes(), file1);
                        TestFileCreator.saveFile("same2.client".getBytes(), file2);
                        File subDir = new File(rootPath + File.separator + "samedir" + File.separator + "samesub");
                        subDir.mkdirs();
                        IFile subFile = AbstractFile.instance(subDir.getAbsolutePath() + File.separator + "samesub1.txt");
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
        setup(false, syncListener, clientMelBoot);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
    }

    public void simpleTransferFromServerToClient(MelBoot clientMelBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final FileSyncSyncListener syncListener = new FileSyncSyncListener() {
            public File file2;
            public File file1;
            public String rootPath;
            public MelFileSyncClientService melDriveClientService;
            private FileSyncSyncListener ins = this;
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
        setup(null, syncListener, clientMelBoot);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
    }

    public void clientConflictImpl(MelBoot clientMelBoot, MelBoot restartMelBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final FileSyncSyncListener syncListener = new FileSyncSyncListener() {
            IFile file2;
            IFile file1;
            String rootPath;
            MelFileSyncClientService melDriveClientService;
            private FileSyncSyncListener ins = this;
            int count = 0;
            int failCount = 0;

            @Override
            public void onSyncFailed() {
                Lok.debug("DriveTest.onSyncFailed");
                if (failCount == 0) {
                    N.r(() -> {
                        //if (!file2.exists())
                        Lok.debug("DriveTest.onSyncFailed.creating new file...");
                        rootPath = ins.testStructure.serverDriveService.getFileSyncSettings().getRootDirectory().getPath();
                        IFile newFile = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same3.txt");
                        IFile delFile = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
                        IFile f1 = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        delFile.delete();
                        TestFileCreator.saveFile("same3.server".getBytes(), newFile);
                        TestFileCreator.saveFile("same1.server".getBytes(), f1);
                        String hash = Hash.md5(f1.inputStream());
                        Lok.debug("DriveTest.onTransfersDone.hash: " + f1 + " -> " + hash);
                        hash = Hash.md5(newFile.inputStream());
                        Lok.debug("DriveTest.onTransfersDone.hash: " + newFile + " -> " + hash);
                        MelBoot melBoot = (restartMelBoot != null) ? restartMelBoot : new MelBoot(json1, new PowerManager(json1), FileSyncBootloader.class);
                        Promise<MelAuthService, Exception, Void> rebooted = melBoot.boot();
                        rebooted.done(res -> N.r(() -> {
                            Lok.debug("DriveTest.alles ok");
//                            testStructure.setMaClient(melAuthService2)
//                                    .setMaServer(melAuthService1)
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
                        melAuthService1.shutDown();
                        melDriveClientService = (MelFileSyncClientService) melAuthService2.getMelServices().iterator().next();
                        rootPath = ins.testStructure.clientDriveService.getFileSyncSettings().getRootDirectory().getPath();
                        file1 = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same1.txt");
                        file2 = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
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
        setup(false, syncListener, clientMelBoot);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
    }

    public void simpleClientConflictImpl(MelBoot clientMelBoot, MelBoot restartMelBoot) throws Exception {
        // start both instances, shutdown server, change something in client directory
        final FileSyncSyncListener syncListener = new FileSyncSyncListener() {
            File file2;
            File file1;
            String rootPath;
            MelFileSyncClientService melDriveClientService;
            private FileSyncSyncListener ins = this;
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
//                        MelBoot melBoot = (restartMelBoot != null) ? restartMelBoot : new MelBoot(json1, DriveBootloader.class);
//                        Promise<MelAuthService, Exception, Void> rebooted = melBoot.spawn();
//                        rebooted.done(res -> N.r(() -> {
//                            Lok.debug("DriveTest.alles ok");
////                            testStructure.setMaClient(melAuthService2)
////                                    .setMaServer(melAuthService1)
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
//                        melAuthService1.shutDown();
//                        melDriveClientService = (MelDriveClientService) melAuthService2.getMelServices().iterator().next();
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
        setup(false, syncListener, clientMelBoot);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.clientMergeStages.END");
    }

//    @Test
//    public void isolation() throws Exception {
//        setup(null,new DriveSyncListener() {
//            @Override
//            public void onSyncFailed() {
//
//            }
//
//            @Override
//            public void onTransfersDone() {
//
//            }
//
//            @Override
//            public void onSyncDoneImpl() {
//                run(() -> {
//                    Long certId = testStructure.clientDriveService.getDriveSettings().getClientSettings().getServerCertId();
//                    String remoteServiceUuid = testStructure.clientDriveService.getDriveSettings().getClientSettings().getServerServiceUuid();
//                    String ownServiceUuid = testStructure.clientDriveService.getUuid();
////                 String address, int port, int portCert
//                    Promise<MelIsolatedFileProcess, Exception, Void> isolated = testStructure.clientDriveService.getIsolatedProcess(MelIsolatedFileProcess.class, certId, remoteServiceUuid);
//                    isolated.done(melIsolatedProcess -> {
//                        Lok.debug("DriveTest.onSyncDoneImpl.SUCCESS");
//                        assertTrue(true);
//                        lock.unlockWrite();
//                    }).fail(result -> {
//                        Lok.debug("DriveTest.onSyncDoneImpl.FAIL");
//                        fail();
//                        lock.unlockWrite();
//                    });
//                });
//
//            }
//        },null);
//        lock.lockWrite();
//        lock.unlockWrite();
//        Lok.debug("DriveTest.isolation.END");
//    }


    //    @Test
    public void clientMergeStages() throws Exception {
        // start both instances, shutdown server, change something in client directory
        setup(new FileSyncSyncListener() {
            IFile file2;
            IFile file1;
            String rootPath;
            MelFileSyncClientService melFileSyncClientService;
            private FileSyncSyncListener ins = this;
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
                        melAuthService1.shutDown();
                        melFileSyncClientService = (MelFileSyncClientService) melAuthService2.getMelServices().iterator().next();
                        rootPath = ins.testStructure.clientDriveService.getFileSyncSettings().getRootDirectory().getPath();
                        file1 = AbstractFile.instance(rootPath + File.separator + "sub1" + File.separator + "newfile.1");
                        file2 = AbstractFile.instance(rootPath + File.separator + "sub1" + File.separator + "newfile.2");
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
        CertificateManager.deleteDirectory(new StandardFile(MelBoot.Companion.getDefaultWorkingDir1()));
        CertificateManager.deleteDirectory(testdir1);
        TestDirCreator.createTestDir(testdir1);
        MelAuthSettings json1 = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MA1");
        MelBoot boot = new MelBoot(json1, new PowerManager(json1));
        WaitLock waitLock = new WaitLock().lock();
        Promise<MelAuthService, Exception, Void> promise = boot.boot();
        final MelAuthService[] mas = new MelAuthService[1];
        promise.done(result -> N.r(() -> {
            mas[0] = result;
            Promise<MelFileSyncServerService, Exception, Void> driveBootedPromise = new FileSyncCreateServiceHelper(result)
                    .createDriveServerServiceDeferred("server test", testdir1, 0.01f, 30);
            driveBootedPromise.done(result1 -> N.r(() -> {
                result1.getIndexer().getIndexerStartedDeferred().done(result2 -> N.r(() -> {
                    result.shutDown();
                    waitLock.unlock();
                }));

            }));
        }));
        waitLock.lock();
        MelFileSyncServerService driveServerService = (MelFileSyncServerService) mas[0].getMelServices().iterator().next();
        String rootPath = driveServerService.getFileSyncSettings().getRootDirectory().getPath();
        IFile newFile = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same3.txt");
        IFile delFile = AbstractFile.instance(rootPath + File.separator + "samedir" + File.separator + "same2.txt");
        delFile.delete();
        TestFileCreator.saveFile("newfile.2".getBytes(), newFile);
        boot = new MelBoot(json1, new PowerManager(json1));
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

    /**
     * this test fails when run in batch with the others of the project. need to investigate
     * @throws Exception
     */
//    //@Test
    public void firstSyncServer2Client() throws Exception {
        setup(null, new FileSyncSyncListener() {

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
                        FileSyncDatabaseManager dbManager = testStructure.clientDriveService.getFileSyncDatabaseManager();
                        List<FsFile> rootFiles = dbManager.getFsDao().getFilesByFsDirectory(null);
                        for (FsFile f : rootFiles) {
                            Lok.debug(f.getName().v());
                        }
                        // TODO refine, checks go here
                        lock.unlockWrite();
                    }
                    Lok.debug("DriveTest.onSyncDoneImpl.EEEEEEEEEEE " + getCount());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, null);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.firstSync.END");
    }

    //todo refine: this has to use a transfer done listener instead
//    @Test
    public void firstSyncClient2Server() throws Exception {
        setup(null, new FileSyncSyncListener() {

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
                        FileSyncDatabaseManager dbManager = testStructure.clientDriveService.getFileSyncDatabaseManager();
                        List<FsFile> rootFiles = dbManager.getFsDao().getFilesByFsDirectory(null);
                        for (FsFile f : rootFiles) {
                            Lok.debug(f.getName().v());
                        }
                        // TODO refine, checks go here
                        Lok.debug("o lala");
                        lock.unlockWrite();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, null, true);
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("DriveTest.firstSync.END");
    }

    //    @Test
    public void addFile() throws Exception {
        setup(new FileSyncSyncListener() {

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
                        FileSyncDatabaseManager dbManager = testStructure.clientDriveService.getFileSyncDatabaseManager();
                        List<FsFile> rootFiles = dbManager.getFsDao().getFilesByFsDirectory(null);
                        for (FsFile f : rootFiles) {
                            Lok.debug(f.getName().v());
                        }
                        File newFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub3.txt");
                        newFile.createNewFile();
                    } else if (getCount() == 1) {
                        Lok.debug("DriveFXTest.onSyncDoneImpl :)");
                        Map<Long, GenericFSEntry> entries1 = genList2Map(testStructure.serverDriveService.getFileSyncDatabaseManager().getFsDao().getDelta(0));
                        Map<Long, GenericFSEntry> entries2 = genList2Map(testStructure.clientDriveService.getFileSyncDatabaseManager().getFsDao().getDelta(0));
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

    //todo refine: interactivity
//    @Test
    public void deleteFile() throws Exception {
        setup(true, new FileSyncSyncListener() {

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
                        FileSyncDatabaseManager dbManager = testStructure.clientDriveService.getFileSyncDatabaseManager();
                        List<FsFile> rootFiles = dbManager.getFsDao().getFilesByFsDirectory(null);
                        for (FsFile f : rootFiles) {
                            Lok.debug(f.getName().v());
                        }
                        File deleteFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub2.txt");
                        deleteFile.delete();
                    } else if (getCount() == 1) {
                        Lok.debug("DriveFXTest.onSyncDoneImpl :)");
                        Map<Long, GenericFSEntry> entries1 = genList2Map(testStructure.serverDriveService.getFileSyncDatabaseManager().getFsDao().getDelta(0));
                        Map<Long, GenericFSEntry> entries2 = genList2Map(testStructure.clientDriveService.getFileSyncDatabaseManager().getFsDao().getDelta(0));
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

    private void setup(FileSyncSyncListener clientSyncListener) throws Exception {
        setup(false, clientSyncListener, null);
    }


    private void startServer() throws Exception {
        //setup working directories & directories with test data
        CertificateManager.deleteDirectory(new StandardFile(MelBoot.Companion.getDefaultWorkingDir1()));
        //CertificateManager.deleteDirectory(MelBoot.defaultWorkingDir2);
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir1);
        TestDirCreator.createTestDir(testdir1);


        // configure MelAuth
        N runner = new N(e -> e.printStackTrace());

        MelAuthSettings json1 = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MA1");
        // we want accept all registration attempts automatically
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {

            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
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
        };
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (melAuthService, registered) -> {
            List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                melAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        lock.lockWrite();

        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncBootloader.class);
        boot1.boot().done(ma1 -> {
            runner.runTry(() -> {
                Lok.debug("DriveTest.driveGui.booted");
                melAuthService1 = ma1;
                melAuthService1.addRegisterHandler(allowRegisterHandler);
                melAuthService1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                RWLock lock = new RWLock().lockWrite();
                FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
                    lock.lockWrite();
                };
                new FileSyncCreateServiceHelper(melAuthService1).createServerService("server service", testdir1, 0.01f, 30, false);
                lock.lockWrite();
                Lok.debug("DriveTest.startServer.booted");
            });
        });

    }

    private void setup(Boolean identicalTestDirs, FileSyncSyncListener clientSyncListener, MelBoot clientMelBoot) throws Exception {
        setup(identicalTestDirs, clientSyncListener, clientMelBoot, false);
    }

    /**
     * @param identicalTestDirs  does not create a second directory if null
     * @param clientSyncListener
     * @param clientMelBoot
     */
    private void setup(Boolean identicalTestDirs, FileSyncSyncListener clientSyncListener, MelBoot clientMelBoot, boolean swapTestDirs) throws Exception {
        //setup working directories & directories with test data
        if (swapTestDirs) {
            testdir1.mkdirs();
            IFile tmp = testdir1;
            testdir1 = testdir2;
            testdir2 = tmp;
        }
        TestDirCreator.createTestDir(testdir1, 1);
        if (identicalTestDirs != null) {
            if (identicalTestDirs)
                TestDirCreator.createTestDir(testdir2);
            else
                TestDirCreator.createTestDir(testdir2, " lel");
        }
        // swap back
        if (swapTestDirs) {
            IFile tmp = testdir1;
            testdir1 = testdir2;
            testdir2 = tmp;
        }

        // configure MelAuth
        N runner = new N(e -> e.printStackTrace());

        json1 = createJson1();
        json2 = createJson2();
        // we want accept all registration attempts automatically
        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
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
            public void setup(MelAuthAdmin melAuthAdmin) {

            }
        };
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (melAuthService, registered) -> {
            List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                melAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        lock.lockWrite();

        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncBootloader.class);
        MelBoot boot2;
        if (clientMelBoot != null)
            boot2 = clientMelBoot;
        else
            boot2 = new MelBoot(json2, new PowerManager(json2), FileSyncBootloader.class);
        boot1.boot().done(ma1 -> {
            runner.runTry(() -> {
                Lok.debug("DriveFXTest.driveGui.1.booted");
                melAuthService1 = ma1;
                melAuthService1.addRegisterHandler(allowRegisterHandler);
                melAuthService1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> N.r(() -> {
                    MelFileSyncServerService serverService = (MelFileSyncServerService) driveService;
                    boot2.boot().done(ma2 -> {
                        Lok.debug("DriveFXTest.driveGui.2.booted");
                        melAuthService2 = ma2;
                        melAuthService2.addRegisterHandler(allowRegisterHandler);
                        melAuthService2.addRegisteredHandler((melAuthService, registered) -> runner.runTry(() -> {
                            // connect first. this step will register
                            Promise<MelValidationProcess, Exception, Void> connectPromise = melAuthService2.connect("localhost", ma1.getSettings().getPort(), ma1.getSettings().getDeliveryPort(), false);
                            connectPromise.done(melValidationProcess -> new Thread(() -> {
                                        runner.runTry(() -> {
                                            Lok.debug("DriveFXTest.driveGui.connected");
                                            // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                                            FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = clientDriveService -> {
                                                Lok.debug("DriveFXTest attempting first syncFromServer");
                                                clientSyncListener.testStructure.setMaClient(melAuthService2)
                                                        .setMaServer(melAuthService1)
                                                        .setClientDriveService((MelFileSyncClientService) clientDriveService)
                                                        .setServerDriveService(serverService)
                                                        .setTestdir1(testdir1)
                                                        .setTestdir2(testdir2);
                                                clientDriveService.setSyncListener(clientSyncListener);
                                            };
                                            new FileSyncCreateServiceHelper(melAuthService2).createClientService("client service", testdir2, 1l, serverService.getUuid(), 0.01f, 30, false);
                                        });
                                    }).start()
                            );

                        }));
                        runner.runTry(() -> {
                            melAuthService2.connect("localhost", 8888, 8889, true);
                        });
                    });
                });
                new FileSyncCreateServiceHelper(melAuthService1).createServerService("server service", testdir1, 0.01f, 30, false);
            });
        });
        //lock.lockWrite();
        //lock.unlockWrite();
    }

    public static MelAuthSettings createJson2() {
        MelAuthSettings settings = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("MA2").setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(MelBoot.Companion.getDefaultWorkingDir2(), MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        return settings;
    }

    public static MelAuthSettings createJson1() {
        MelAuthSettings settings = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MA1").setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(MelBoot.Companion.getDefaultWorkingDir1(), MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        return settings;
    }

    public void startUpConflicts(MelBoot melBoot) throws Exception {
        setup(false, new FileSyncSyncListener() {
            @Override
            public void onSyncFailed() {

            }

            @Override
            public void onTransfersDone() {

            }

            @Override
            public void onSyncDoneImpl() {

            }
        }, melBoot);
    }
}
