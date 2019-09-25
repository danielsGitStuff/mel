package de.mel.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by xor on 12/15/16.
 */
@SuppressWarnings("Duplicates")
public class LotsOfTests {
//    private static MelAuthService standAloneAuth1;
//    private static MelAuthService standAloneAuth2;
//    private static MelTestService melTestService1;
//    private static MelTestService melTestService2;
//    private static final String serviceUuid1 = "test uuid no. 0";
//    private static final String serviceUuid2 = "test uuid no. 1";
//    private static RWLock lock = new RWLock();
//    private static N runner = new N(Throwable::printStackTrace);
//    private File testdir1;
//    private File testdir2;
//
//    private static void run(N.INoTryRunnable noTryRunnable) {
//        runner.runTry(noTryRunnable);
//    }
//
//    private static abstract class OnConnectedListener {
//
//        public abstract void onConnected();
//    }
//
//
//    @Test
//    public void isolation() throws Exception {
//        setup(new OnConnectedListener() {
//            @Override
//            public void onConnected() {
//                run(() -> {
//                    DeferredObject<MelIsolatedFileProcess, Exception, Void> isolated = standAloneAuth1.connectToService(MelIsolatedFileProcess.class, 1L, "test uuid no. 1", "test uuid no. 0", null, null, null);
//                    isolated.done(melIsolatedProcess -> {
//                        Lok.debug("DriveTest.onSyncDoneImpl.SUCCESS");
//                        lock.unlockWrite();
//                    }).fail(result -> {
//                        Lok.debug("DriveTest.onSyncDoneImpl.FAIL");
//                        Assert.fail("did not connect");
//                    });
//                });
//            }
//        });
//        lock.lockWrite();
//        lock.unlockWrite();
//        Lok.debug("DriveTest.isolation.END");
//    }
//
//    /**
//     * instance 1 sends to 2 after exchanging FileTransferDetails
//     *
//     * @throws Exception
//     */
//    @Test
//    public void sendFile() throws Exception {
//        AFile target = AFile.instance("otters.transferred.jpg");
//        target.delete();
//        setup(new OnConnectedListener() {
//            @Override
//            public void onConnected() {
//                run(() -> {
//                    URL otters = getClass().getClassLoader().getResource("otters.jpg");
//                    AFile source = AFile.instance(otters.getFile());//= new File(testdir1.getAbsolutePath() + File.separator + "blob.file");
//                    FileTransferDetail fileTransferDetail1 = new FileTransferDetail(source, 7, 0, source.length());
//                    fileTransferDetail1.openRead();
//                    FileTransferDetail fileTransferDetail2 = new FileTransferDetail(target, 7, 0, source.length());
//                    fileTransferDetail2.setTransferDoneListener(fileTransferDetail -> {
//                        lock.unlockWrite();
//                    });
//                    DeferredObject<MelIsolatedFileProcess, Exception, Void> isolated = standAloneAuth1.connectToService(MelIsolatedFileProcess.class, 1L, serviceUuid2, serviceUuid1, null, null, null);
//                    isolated.done(fileProcess -> run(() -> {
//                        Lok.debug("LotsOfTests.onConnected1");
////                        MelIsolatedFileProcess iso1 = (MelIsolatedFileProcess) melTestService1.getIsolatedProcess(1L, serviceUuid2);
////                        MelIsolatedFileProcess iso2 = (MelIsolatedFileProcess) melTestService2.getIsolatedProcess(1L, serviceUuid1);
////                        Lok.debug("LotsOfTests.onConnected2");
////                        iso2.addFilesReceiving(fileTransferDetail2);
////                        Lok.debug("LotsOfTests.onConnected3");
////                        iso1.sendFile(fileTransferDetail1);
////                        Lok.debug("DriveTest.onSyncDoneImpl.SUCCESS");
//                    })).fail(result -> {
//                        Lok.debug("DriveTest.onSyncDoneImpl.FAIL");
//                        Assert.fail("did not connect");
//                    });
//                });
//            }
//        });
//        lock.lockWrite();
//        lock.unlockWrite();
//
//        if (!target.exists())
//            Lok.debug("LotsOfTests.sendFile.debugf43g");
//        assertTrue(target.exists());
//        Lok.debug("DriveTest.isolation.END");
//    }
//
//    @Before
//    public void before(){
//        AFile.configure(new DefaultFileConfiguration());
//        MelTestBootloader.count = 0;
//    }
//
//    /**
//     * instance 1 sends to 2 after exchanging FileTransferDetails
//     *
//     * @throws Exception
//     */
//    @Test
//    public void sendError() throws Exception {
//        AFile target = AFile.instance("otters.transferred.jpg");
//        target.delete();
//        setup(new OnConnectedListener() {
//            @Override
//            public void onConnected() {
//                run(() -> {
//                    URL otters = getClass().getClassLoader().getResource("otters.jpg");
//                    AFile source = AFile.instance(otters.getFile());//= new File(testdir1.getAbsolutePath() + File.separator + "blob.file");
//                    FileTransferDetail fileTransferDetail1 = new FileTransferDetail(source, 7, 0, source.length());
//                    fileTransferDetail1.setError(true);
//                    fileTransferDetail1.openRead();
//                    FileTransferDetail fileTransferDetail2 = new FileTransferDetail(target, 7, 0, source.length());
//                    fileTransferDetail2.setTransferFailedListener(fileTransferDetail -> {
//                        lock.unlockWrite();
//                    });
//                    DeferredObject<MelIsolatedFileProcess, Exception, Void> isolated = standAloneAuth1.connectToService(MelIsolatedFileProcess.class, 1L, serviceUuid2, serviceUuid1, null, null, null);
//                    isolated.done(fileProcess -> run(() -> {
//                        Lok.debug("LotsOfTests.onConnected1");
//                        MelIsolatedFileProcess iso1 = (MelIsolatedFileProcess) melTestService1.getIsolatedProcess(1L, serviceUuid2);
//                        MelIsolatedFileProcess iso2 = (MelIsolatedFileProcess) melTestService2.getIsolatedProcess(1L, serviceUuid1);
//                        Lok.debug("LotsOfTests.onConnected2");
//                        iso2.addFilesReceiving(fileTransferDetail2);
//                        Lok.debug("LotsOfTests.onConnected3");
//                        iso1.sendFile(fileTransferDetail1);
//                        Lok.debug("DriveTest.onSyncDoneImpl.SUCCESS");
//                        //fileProcess.sendFile(source);
//                        //lock.unlockWrite();
//                    })).fail(result -> {
//                        Lok.debug("DriveTest.onSyncDoneImpl.FAIL");
//                        Assert.fail("did not connect");
//                    });
//                });
//            }
//        });
//        lock.lockWrite();
//        lock.unlockWrite();
//
//        if (target.exists())
//            Lok.debug("LotsOfTests.sendFile.debugf43g");
//        assertFalse(target.exists());
//        Lok.debug("DriveTest.isolation.END");
//    }
//
//    public ServiceJoinServiceType getOnlyService(MelAuthService ma) throws SqlQueriesException {
//        ServiceJoinServiceType service = ma.getDatabaseManager().getAllServices().get(0);
//        return service;
//    }
//
//    @After
//    public void after(){
//        standAloneAuth1.shutDown();
//        standAloneAuth2.shutDown();
//    }
//
//    public void setup(OnConnectedListener onConnectedListener) throws Exception, SqlQueriesException {
//        //setup working directories & directories with test data
//        testdir1 = new File("testdir1");
//        testdir2 = new File("testdir2");
//        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
//        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir2());
//        CertificateManager.deleteDirectory(testdir1);
//        CertificateManager.deleteDirectory(testdir2);
//        testdir2.mkdirs();
//
//        // configure MelAuth
//        N runner = new N(e -> e.printStackTrace());
//
//        MelAuthSettings json1 = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
//                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
//                .setWorkingDirectory((MelBoot.Companion.getDefaultWorkingDir1())).setName("MA1").setGreeting("greeting1").setVariant(MelStrings.update.VARIANT_JAR);
//        MelAuthSettings json2 = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
//                .setBrotcastPort(9966) // does not listen! only one listener seems possible
//                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
//                .setWorkingDirectory((MelBoot.Companion.getDefaultWorkingDir2())).setName("MA2").setGreeting("greeting2").setVariant(MelStrings.update.VARIANT_JAR);
//
//        // we want accept all registration attempts automatically
//        IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
//            @Override
//            public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
//                listener.onCertificateAccepted(request, certificate);
//            }
//
//            @Override
//            public void onRegistrationCompleted(Certificate partnerCertificate) {
//
//            }
//
//            @Override
//            public void onRemoteRejected(Certificate partnerCertificate) {
//
//            }
//
//            @Override
//            public void onLocallyRejected(Certificate partnerCertificate) {
//
//            }
//
//            @Override
//            public void onRemoteAccepted(Certificate partnerCertificate) {
//
//            }
//
//            @Override
//            public void onLocallyAccepted(Certificate partnerCertificate) {
//
//            }
//        };
//        // we want to allow every registered Certificate to talk to all available Services
//        IRegisteredHandler registeredHandler = (melAuthService, registered) -> {
//            List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
//            for (ServiceJoinServiceType serviceJoinServiceType : services) {
//                melAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
//            }
//        };
//        lock.lockWrite();
//        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1)).addBootLoaderClass(MelTestBootloader.class);
//        MelBoot boot2 = new MelBoot(json2, new PowerManager(json2)).addBootLoaderClass(MelTestBootloader.class);
//        boot1.boot().done(ma1 -> {
//            standAloneAuth1 = ma1;
//            standAloneAuth1.addRegisterHandler(allowRegisterHandler);
//            standAloneAuth1.addRegisteredHandler(registeredHandler);
//            runner.runTry(() -> {
//                Lok.debug("LotsOfTests.setup.1.booted");
//                // setup the server Service
//                boot2.boot().done(ma2 -> {
//                    standAloneAuth2 = ma2;
//                    standAloneAuth2.addRegisterHandler(allowRegisterHandler);
//                    Lok.debug("LotsOfTests.setup.2.booted");
//                    melTestService1 = (MelTestService) standAloneAuth1.getMelService("test uuid no. 0");
//                    melTestService2 = (MelTestService) standAloneAuth2.getMelService("test uuid no. 1");
//                    runner.runTry(() -> {
//                        // connect first. this step will register
//                        Promise<MelValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect("localhost", 8888, 8889, true);
//                        connectPromise.done(melValidationProcess -> {
//                            runner.runTry(() -> {
//                                Lok.debug("LotsOfTests.setup.connected");
//                                onConnectedListener.onConnected();
//                                // MAs know each other at this point. setup the client Service. it wants some data from the steps before
//                            });
//                        });
//                    });
//                });
//            });
//        });
//        //lock.lockWrite();
//        //lock.unlockWrite();
//    }
}
