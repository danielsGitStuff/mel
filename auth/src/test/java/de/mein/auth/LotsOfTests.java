package de.mein.auth;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.MeinTestBootloader;
import de.mein.auth.service.MeinTestService;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.transfer.FileTransferDetail;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * Created by xor on 12/15/16.
 */
@SuppressWarnings("Duplicates")
public class LotsOfTests {
    private static MeinAuthService standAloneAuth1;
    private static MeinAuthService standAloneAuth2;
    private static MeinTestService meinTestService1;
    private static MeinTestService meinTestService2;
    private static final String serviceUuid1 = "test uuid no. 0";
    private static final String serviceUuid2 = "test uuid no. 1";
    private static RWLock lock = new RWLock();
    private static N runner = new N(Throwable::printStackTrace);
    private File testdir1;
    private File testdir2;

    private static void run(N.INoTryRunnable noTryRunnable) {
        runner.runTry(noTryRunnable);
    }

    private static abstract class OnConnectedListener {

        public abstract void onConnected();
    }

    @Test
    public void isolation() throws Exception {
        setup(new OnConnectedListener() {
            @Override
            public void onConnected() {
                run(() -> {
                    DeferredObject<MeinIsolatedFileProcess, Exception, Void> isolated = standAloneAuth1.connectToService(MeinIsolatedFileProcess.class, 1L, "test uuid no. 1", "test uuid no. 0", null, null, null);
                    isolated.done(meinIsolatedProcess -> {
                        System.out.println("DriveTest.onSyncDoneImpl.SUCCESS");
                        lock.unlockWrite();
                    }).fail(result -> {
                        System.out.println("DriveTest.onSyncDoneImpl.FAIL");
                        Assert.fail("did not connect");
                    });
                });
            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.isolation.END");
    }

    /**
     * instance 1 sends to 2 after exchanging FileTransferDetails
     *
     * @throws Exception
     */
    @Test
    public void sendFile() throws Exception {

        setup(new OnConnectedListener() {
            @Override
            public void onConnected() {
                run(() -> {
                    URL otters = getClass().getClassLoader().getResource("otters.jpg");
                    File source = new File(otters.getFile());//= new File(testdir1.getAbsolutePath() + File.separator + "blob.file");
                    FileTransferDetail fileTransferDetail1 = new FileTransferDetail(source, 7, 0, source.length());
                    FileTransferDetail fileTransferDetail2 = new FileTransferDetail(new File("otters.transferred.jpg"), 7, 0, source.length());

                    DeferredObject<MeinIsolatedFileProcess, Exception, Void> isolated = standAloneAuth1.connectToService(MeinIsolatedFileProcess.class, 1L, serviceUuid2, serviceUuid1, null, null, null);
                    isolated.done(fileProcess -> run(() -> {
                        System.out.println("LotsOfTests.onConnected1");
                        MeinIsolatedFileProcess iso1 = (MeinIsolatedFileProcess) meinTestService1.getIsolatedProcess(1L, serviceUuid2);
                        MeinIsolatedFileProcess iso2 = (MeinIsolatedFileProcess) meinTestService2.getIsolatedProcess(1L, serviceUuid1);
                        System.out.println("LotsOfTests.onConnected2");
                        iso2.addFilesReceiving(fileTransferDetail2);
                        System.out.println("LotsOfTests.onConnected3");
                        iso1.sendFile(fileTransferDetail1);
                        System.out.println("DriveTest.onSyncDoneImpl.SUCCESS");
                        //fileProcess.sendFile(source);
                        //lock.unlockWrite();
                    })).fail(result -> {
                        System.out.println("DriveTest.onSyncDoneImpl.FAIL");
                        Assert.fail("did not connect");
                    });
                });
            }
        });
        lock.lockWrite();
        lock.unlockWrite();
        System.out.println("DriveTest.isolation.END");
    }

    public ServiceJoinServiceType getOnlyService(MeinAuthService ma) throws SqlQueriesException {
        ServiceJoinServiceType service = ma.getDatabaseManager().getAllServices().get(0);
        return service;
    }

    public void setup(OnConnectedListener onConnectedListener) throws Exception, SqlQueriesException {
        //setup working directories & directories with test data
        testdir1 = new File("testdir1");
        testdir2 = new File("testdir2");
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir2);
        testdir2.mkdirs();

        // configure MeinAuth
        MeinBoot.addBootLoaderClass(MeinTestBootloader.class);
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
            standAloneAuth1 = ma1;
            standAloneAuth1.addRegisterHandler(allowRegisterHandler);
            standAloneAuth1.addRegisteredHandler(registeredHandler);
            runner.runTry(() -> {
                System.out.println("LotsOfTests.setup.1.booted");
                // setup the server Service
                boot2.boot().done(ma2 -> {
                    standAloneAuth2 = ma2;
                    standAloneAuth2.addRegisterHandler(allowRegisterHandler);
                    System.out.println("LotsOfTests.setup.2.booted");
                    meinTestService1 = (MeinTestService) standAloneAuth1.getMeinService("test uuid no. 0");
                    meinTestService2 = (MeinTestService) standAloneAuth2.getMeinService("test uuid no. 1");
                    runner.runTry(() -> {
                        // connect first. this step will register
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
                        connectPromise.done(meinValidationProcess -> {
                            runner.runTry(() -> {
                                System.out.println("LotsOfTests.setup.connected");
                                onConnectedListener.onConnected();
                                // MAs know each other at this point. setup the client Service. it wants some data from the steps before
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
