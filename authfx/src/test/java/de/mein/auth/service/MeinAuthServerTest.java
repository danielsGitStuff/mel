package de.mein.auth.service;

import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.sql.RWLock;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.junit.Test;


@SuppressWarnings("Duplicates")
public class MeinAuthServerTest {
    RWLock lock;
    N runner;
    private IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
        @Override
        public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
            listener.onCertificateAccepted(request, certificate);
        }

        @Override
        public void onRegistrationCompleted(Certificate partnerCertificate) {

        }
    };


    /*
    private IRegisteredHandler registeredHandler = (meinAuthService, registered) -> {
        Service service = meinAuthService.getDatabaseManager().getServiceByUuid(SERVICE_UUID);
        meinAuthService.getDatabaseManager().grant(service.getId().v(), registered.getId().v());
    };*/
    private MeinStandAloneAuthFX standAloneAuth2;
    private MeinStandAloneAuthFX standAloneAuth1;

    private MeinAuthSettings json1;
    private MeinAuthSettings json2;


    public void prep() throws Exception {
        runner = new N(e -> e.printStackTrace());

//        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
//        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        lock = new RWLock();

        json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1).setName("wd1").setGreeting("greeting1");
        json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(9998)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir2).setName("wd2").setGreeting("greeting2");
        standAloneAuth1 = new MeinStandAloneAuthFX(json1);
        standAloneAuth2 = new MeinStandAloneAuthFX(json2);
    }

    @Test
    public void acceptRegistration() throws Exception, SqlQueriesException {

        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        init();
        lock.lockWrite();
        standAloneAuth1.addRegisterHandler(allowRegisterHandler);
        standAloneAuth2.addRegisterHandler(allowRegisterHandler);

        MeinBoot boot1 = new MeinBoot();
        MeinBoot boot2 = new MeinBoot();
        boot1.boot(standAloneAuth1).done(result -> {
            runner.r(() -> {
                boot2.boot(standAloneAuth2).done(result1 -> {
                    runner.r(() -> {
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(1l, "localhost", 8888, 8889, true);
                        connectPromise.done(integer -> {
                            System.out.println("MeinAuthServerTest.acceptRegistration.registered");
                            lock.unlockWrite();
                        });
                    });
                });
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    public void init() {
        try {
            prep();
        } catch (Exception e) {
            e.printStackTrace();
        }
        /*CertificateManager.deleteDirectory(wd1);
        CertificateManager.deleteDirectory(wd2);
        lock = new RWLock();
        runner = new NoTryRunner(e -> e.printStackTrace());*/
    }

    @Test
    public void gui() throws Exception, SqlQueriesException {
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir2);
        init();
        IDBCreatedListener admin = databaseManager -> {
            ServiceType serviceType = databaseManager.createServiceType("test type", "test type desc");
            databaseManager.createService(serviceType.getId().v(), "service name");
        };
//        standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
//        standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
        standAloneAuth1.addRegisterHandler(allowRegisterHandler);
        standAloneAuth2.addRegisterHandler(allowRegisterHandler);
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot();
        MeinBoot boot2 = new MeinBoot();
        boot1.boot(standAloneAuth1).done(result -> {
            runner.r(() -> {
                System.out.println("MeinAuthServerTest.gui.1.booted");
                boot2.boot(standAloneAuth2).done(result1 -> {
                    System.out.println("MeinAuthServerTest.gui.2.booted");
                    runner.r(() -> {
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
                        connectPromise.done(integer -> {
                            runner.r(() -> {
                                System.out.println("MeinAuthServerTest.gui.booted");
                                standAloneAuth2.getBrotCaster().discover(9966);
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

    @Test
    public void gui2() throws Exception, SqlQueriesException {
        lock.lockWrite();
        standAloneAuth1.boot().done(result -> {

        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    @Test
    public void rejectRegistration() throws Exception, SqlQueriesException {
        init();
        lock.lockWrite();
        standAloneAuth1.addRegisterHandler(new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateRejected(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        });
        standAloneAuth2.addRegisterHandler(allowRegisterHandler);
        standAloneAuth1.boot().done(result -> {
            standAloneAuth2.boot().done(result1 -> {
                runner.r(() -> {
                    Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
                    connectPromise.done(integer -> {
                        System.out.println("MeinAuthServerTest.rejectRegistration.registered");
                        lock.unlockWrite();
                    }).fail(result2 -> {
                        System.out.println("MeinAuthServerTest.rejectRegistration.fail");
                        lock.unlockWrite();
                    });
                });
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }
}
