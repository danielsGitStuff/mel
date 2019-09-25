package de.mel.auth.service;


@SuppressWarnings("Duplicates")
public class MelAuthServerTest {
//    RWLock lock;
//    N runner;
//    private IRegisterHandler allowRegisterHandler = new IRegisterHandler() {
//        @Override
//        public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
//            listener.onCertificateAccepted(request, certificate);
//        }
//
//        @Override
//        public void onRegistrationCompleted(Certificate partnerCertificate) {
//
//        }
//
//        @Override
//        public void onRemoteRejected(Certificate partnerCertificate) {
//
//        }
//
//        @Override
//        public void onLocallyRejected(Certificate partnerCertificate) {
//
//        }
//
//        @Override
//        public void onRemoteAccepted(Certificate partnerCertificate) {
//
//        }
//
//        @Override
//        public void onLocallyAccepted(Certificate partnerCertificate) {
//
//        }
//    };
//
//
//    /*
//    private IRegisteredHandler registeredHandler = (melAuthService, registered) -> {
//        Service service = melAuthService.getDatabaseManager().getServiceByUuid(SERVICE_UUID);
//        melAuthService.getDatabaseManager().grant(service.getId().v(), registered.getId().v());
//    };*/
//    private MelStandAloneAuthFX standAloneAuth2;
//    private MelStandAloneAuthFX standAloneAuth1;
//
//    private MelAuthSettings json1;
//    private MelAuthSettings json2;
//
//
//    public void prep() throws Exception {
//        runner = new N(e -> e.printStackTrace());
//
////        CertificateManager.deleteDirectory(MelBoot.defaultWorkingDir1);
////        CertificateManager.deleteDirectory(MelBoot.defaultWorkingDir2);
//        lock = new RWLock();
//
//        json1 = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
//                .setBrotcastListenerPort(9966).setBrotcastPort(9966)
//                .setWorkingDirectory(MelBoot.defaultWorkingDir1).setName("wd1").setGreeting("greeting1");
//        json2 = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
//                .setBrotcastPort(9966) // does not listen! only one listener seems possible
//                .setBrotcastListenerPort(9998)
//                .setWorkingDirectory(MelBoot.defaultWorkingDir2).setName("wd2").setGreeting("greeting2");
//
//    }
//
//    @Test
//    public void acceptRegistration() throws Exception, SqlQueriesException {
//
//        CertificateManager.deleteDirectory(MelBoot.defaultWorkingDir1);
//        CertificateManager.deleteDirectory(MelBoot.defaultWorkingDir2);
//        init();
//        lock.lockWrite();
//        MelBoot boot1 = new MelBoot(json1);
//        MelBoot boot2 = new MelBoot(json2);
//        boot1.boot().done(standAloneAuth1 -> {
//            standAloneAuth1.addRegisterHandler(allowRegisterHandler);
//
//            runner.r(() -> {
//                boot2.boot().done(standAloneAuth2 -> {
//                    standAloneAuth2.addRegisterHandler(allowRegisterHandler);
//
//                    runner.r(() -> {
//                        Promise<MelValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect( "localhost", 8888, 8889, true);
//                        connectPromise.done(integer -> {
//                            Lok.debug("MelAuthServerTest.acceptRegistration.registered");
//                            lock.unlockWrite();
//                        });
//                    });
//                });
//            });
//        });
//        lock.lockWrite();
//        lock.unlockWrite();
//    }
//
//    public void init() {
//        try {
//            prep();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        /*CertificateManager.deleteDirectory(wd1);
//        CertificateManager.deleteDirectory(wd2);
//        lock = new RWLock();
//        runner = new NoTryRunner(e -> e.printStackTrace());*/
//    }
//
//    @Test
//    public void gui() throws Exception, SqlQueriesException {
//        CertificateManager.deleteDirectory(MelBoot.defaultWorkingDir1);
//        CertificateManager.deleteDirectory(MelBoot.defaultWorkingDir2);
//        init();
//        IDBCreatedListener admin = databaseManager -> {
//            ServiceType serviceType = databaseManager.createServiceType("test type", "test type desc");
//            databaseManager.createService(serviceType.getId().v(), "service name");
//        };
////        standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
////        standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
//
//        lock.lockWrite();
//
//        MelBoot boot1 = new MelBoot(json1);
//        MelBoot boot2 = new MelBoot(json2);
//        boot1.boot().done(standAloneAuth1 -> {
//            standAloneAuth1.addRegisterHandler(allowRegisterHandler);
//            runner.r(() -> {
//                Lok.debug("MelAuthServerTest.gui.1.booted");
//                boot2.boot().done(standAloneAuth2 -> {
//                    standAloneAuth2.addRegisterHandler(allowRegisterHandler);
//                    Lok.debug("MelAuthServerTest.gui.2.booted");
//                    runner.r(() -> {
//                        Promise<MelValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect( "localhost", 8888, 8889, true);
//                        connectPromise.done(integer -> {
//                            runner.r(() -> {
//                                Lok.debug("MelAuthServerTest.gui.booted");
//                                standAloneAuth2.getBrotCaster().discover(9966);
//                                //lock.unlockWrite();
//                            });
//                        });
//                    });
//                });
//            });
//        });
//        lock.lockWrite();
//        lock.unlockWrite();
//    }
//
//    @Test
//    public void rejectRegistration() throws Exception, SqlQueriesException {
//        init();
//        lock.lockWrite();
//        MelBoot melBoot1 = new MelBoot(json1);
//        MelBoot melBoot2 = new MelBoot(json2);
//        melBoot1.boot().done(standAloneAuth1 -> {
//            standAloneAuth1.addRegisterHandler(new IRegisterHandler() {
//                @Override
//                public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
//                    listener.onCertificateRejected(request, certificate);
//                }
//
//                @Override
//                public void onRegistrationCompleted(Certificate partnerCertificate) {
//
//                }
//
//                @Override
//                public void onRemoteRejected(Certificate partnerCertificate) {
//
//                }
//
//                @Override
//                public void onLocallyRejected(Certificate partnerCertificate) {
//
//                }
//
//                @Override
//                public void onRemoteAccepted(Certificate partnerCertificate) {
//
//                }
//
//                @Override
//                public void onLocallyAccepted(Certificate partnerCertificate) {
//
//                }
//            });
//        });
//        melBoot2.boot().done(standAloneAuth2 -> {
//            standAloneAuth2.addRegisterHandler(allowRegisterHandler);
//            runner.r(() -> {
//                Promise<MelValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect( "localhost", 8888, 8889, true);
//                connectPromise.done(integer -> {
//                    Lok.debug("MelAuthServerTest.rejectRegistration.registered");
//                    lock.unlockWrite();
//                }).fail(result2 -> {
//                    Lok.debug("MelAuthServerTest.rejectRegistration.fail");
//                    lock.unlockWrite();
//                });
//            });
//        });
//        lock.lockWrite();
//        lock.unlockWrite();
//    }
}
