package de.mein.auth.service;


import de.mein.Lok;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.gui.RegisterHandlerFX;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.tools.Eva;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.contacts.ContactsBootloader;
import de.mein.contacts.ContactsFXBootloader;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.db.dao.ContactsDao;
import de.mein.contacts.data.db.dao.PhoneBookDao;
import de.mein.contacts.service.ContactsService;
import de.mein.drive.DriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.bash.BashTools;
import de.mein.drive.boot.DriveFXBootloader;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.sql.*;
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
        MeinAuthSettings json2 = new DriveTest().createJson2();
        MeinBoot meinBoot = new MeinBoot(json2, new PowerManager(json2), DriveFXBootloader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        MeinBoot restartMeinBoot = new MeinBoot(json2, new PowerManager(json2), DriveFXBootloader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        driveTest.simpleClientConflictImpl(meinBoot, null);
        new WaitLock().lock().lock();
    }

    @Test
    public void startUpConflicts() throws Exception {
        DriveTest driveTest = new DriveTest();
        MeinAuthSettings json2 = new DriveTest().createJson2();
        MeinBoot meinBoot = new MeinBoot(json2, new PowerManager(json2), DriveFXBootloader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        driveTest.startUpConflicts(meinBoot);
        new WaitLock().lock().lock();
    }

    @Test
    public void complexConflict() throws Exception {
        Eva.enable();
        DriveTest driveTest = new DriveTest();
        MeinAuthSettings json1 = new DriveTest().createJson1();
        MeinAuthSettings json2 = new DriveTest().createJson2();


        MeinBoot meinBoot = new MeinBoot(json2, new PowerManager(json2), DriveFXBootloader.class, ContactsFXBootloader.class).addMeinAuthAdmin(new MeinAuthFxLoader());

//        MeinBoot restartMeinBoot = new MeinBoot(json1, new PowerManager(json1), DriveFXBootloader.class, ContactsFXBootloader.class).addMeinAuthAdmin(new MeinAuthFxLoader());
        driveTest.complexClientConflictImpl(meinBoot, null);
        new WaitLock().lock().lock();
    }


    @Test
    public void startEmptyClient() throws Exception {
        //CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir2());
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir2()).setName("Test Client").setGreeting("greeting2");
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
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        //todo continue gui
        MeinBoot boot1 = new MeinBoot(json1, new PowerManager(json1), DriveFXBootloader.class)
                .addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(result -> {
            result.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyClient.booted");
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    @Test
    public void startEmptyServer() throws Exception {
        File testdir = new File("testdir1");
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir1());
        N runner = new N(e -> e.printStackTrace());
        MeinStandAloneAuthFX standAloneAuth1;
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("Test Server").setGreeting("greeting1");
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
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinBoot boot1 = new MeinBoot(json1, new PowerManager(json1), DriveFXBootloader.class, ContactsFXBootloader.class);
        boot1.addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyServer.booted");
            });
//            N.r(() -> {
//                DriveCreateController createController = new DriveCreateController(meinAuthService);
//                createController.createDriveServerService("testiServer", testdir.getAbsolutePath(),0.1f,30);
//            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    private void connectAcceptingClient() throws Exception {
        AFile testdir = AFile.instance(new File("testdir2"));
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir2());
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir2()).setName("Test Client").setGreeting("greeting2");
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
        };
        IRegisteredHandler allowRegisteredHandler = (meinAuthService, registered) -> {
            DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
            for (ServiceJoinServiceType service : databaseManager.getAllServices()) {
                databaseManager.grant(service.getServiceId().v(), registered.getId().v());
            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinBoot boot1 = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveFXBootloader.class);
        boot1.addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(allowRegisterHandler);
            meinAuthService.addRegisteredHandler(allowRegisteredHandler);
//            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect("127.0.0.1", 8888, 8889, true);
                connected.done(result -> N.r(() -> {
                    DriveCreateController createController = new DriveCreateController(meinAuthService);
                    createController.createDriveClientService("drive client", testdir, 1L, tmp, 0.1f, 30, false);
                    Lok.debug("FxTest.connectAcceptingClient");
                }));

            });
        });
    }

    public static String tmp;

    @Test
    public void connectAccepting() throws Exception {
        AFile testdir = AFile.instance(new File("testdir1"));
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir1());
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("Test Server").setGreeting("greeting1");
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
        };
        IRegisteredHandler allowRegisteredHandler = (meinAuthService, registered) -> {
            DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
            for (ServiceJoinServiceType service : databaseManager.getAllServices()) {
                databaseManager.grant(service.getServiceId().v(), registered.getId().v());
            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinBoot boot1 = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveFXBootloader.class);
        boot1.addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(allowRegisterHandler);
            meinAuthService.addRegisteredHandler(allowRegisteredHandler);
//            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                DriveCreateController createController = new DriveCreateController(meinAuthService);
                DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> new Thread(() -> N.r(() -> {
                    FxTest.tmp = driveService.getUuid();
                    connectAcceptingClient();
                })).start();
                createController.createDriveServerService("testiServer", testdir, 0.1f, 30, false);
//                FxTest.tmp = serverService.getUuid();
//                connectAcceptingClient();
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    ;

    @Test
    public void startAcceptingServer() throws Exception {
        AFile testdir = AFile.instance(new File("testdir1"));
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir1());
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("Test Server").setGreeting("greeting1");
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
        };
        IRegisteredHandler allowRegisteredHandler = (meinAuthService, registered) -> {
            DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
            for (ServiceJoinServiceType service : databaseManager.getAllServices()) {
                try {
                    databaseManager.grant(service.getServiceId().v(), registered.getId().v());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinBoot boot1 = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveFXBootloader.class, ContactsFXBootloader.class);
        boot1.addMeinAuthAdmin(new MeinAuthFxLoader());
        boot1.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(allowRegisterHandler);
            meinAuthService.addRegisteredHandler(allowRegisteredHandler);
//            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                DriveCreateController createController = new DriveCreateController(meinAuthService);
                createController.createDriveServerService("testiServer", testdir, 0.1f, 30, false);
                // create contacts server too

                ContactsSettings settings = new ContactsSettings();
                settings.setRole(ContactStrings.ROLE_SERVER);
                settings.setMasterPhoneBookId(1L);
                settings.setJsonFile(new File(MeinBoot.Companion.getDefaultWorkingDir1(), "contactserversettings.json"));
                ContactsBootloader contactsBootloader = (ContactsBootloader) meinAuthService.getMeinBoot().getBootLoader(ContactStrings.NAME);
                ContactsService contactsService = contactsBootloader.createService("test contacts", settings);
                ContactsDao cDao = contactsService.getDatabaseManager().getContactsDao();
                PhoneBookDao phoneBookDao = contactsService.getDatabaseManager().getPhoneBookDao();


//                PhoneBook debugBook = new PhoneBook();
//                debugBook.getCreated().v(12L);
//                debugBook.getVersion().v(1L);
//                Contact contact = new Contact();
//                contact.getPhonebookId().v(debugBook.getId());
//                ContactAppendix name = new ContactAppendix(contact);
//                name.getMimeType().v("vnd.android.cursor.item/name");
//                name.setValue(0, "Adolf Bedolf");
//                name.getContactId().v(contact.getId());
//                contact.addAppendix(name);
//                ContactAppendix number = new ContactAppendix(contact);
//                number.getMimeType().v("vnd.android.cursor.item/phone_v2");
//                number.setValue(0,"6661234567");
//                number.getContactId().v(contact.getId());
//                contact.addAppendix(number);
//                contact.hash();
//                debugBook.addContact(contact);
//                //second contact
//                contact = new Contact();
//                contact.getPhonebookId().v(debugBook.getId());
//                name = new ContactAppendix(contact);
//                name.getMimeType().v("vnd.android.cursor.item/name");
//                name.setValue(0, "Server Only");
//                name.getContactId().v(contact.getId());
//                contact.addAppendix(name);
//                number = new ContactAppendix(contact);
//                number.getMimeType().v("vnd.android.cursor.item/phone_v2");
//                number.setValue(0,"000000000000");
//                number.getContactId().v(contact.getId());
//                contact.addAppendix(number);
//                contact.hash();
//                debugBook.addContact(contact);
//
//                debugBook.hash();
//                phoneBookDao.insertDeep(debugBook);
            });
        });
        lock.lockWrite().lockWrite();
        lock.unlockWrite();
    }

    /**
     * sync files to client, then shut him down. alter a file and set it to non-synced in fs database.
     * restart client.
     * expectation: client detects conflict between fs and the altered file. conflict dialog pops up.
     *
     * @throws Exception
     * @throws SqlQueriesException
     */
    @Test
    public void startupConflict() throws Exception, SqlQueriesException {
//        inject(true);
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir2());
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings json1 = MeinAuthSettings.createDefaultSettings();
        json1.setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = MeinAuthSettings.createDefaultSettings()
                .setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir2()).setName("MA2").setGreeting("greeting2");

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
        AFile rootServer = AFile.instance("server");
        AFile rootClient = AFile.instance("client");
        rootClient.mkdirs();
        rootServer.mkdirs();
        MeinBoot boot1 = new MeinBoot(json1, new PowerManager(json1), DriveFXBootloader.class);
        MeinBoot boot2 = new MeinBoot(json2, new PowerManager(json2), DriveFXBootloader.class);
        boot2.addMeinAuthAdmin(new MeinAuthAdminFX());
        json1.setJsonFile(MeinAuthSettings.DEFAULT_FILE);
        json1.save();
        json2.setJsonFile(MeinAuthSettings.DEFAULT_FILE_2);
        json2.save();
        boot1.boot().done(standAloneAuth1 -> {
            N.r(() -> {
                standAloneAuth1.addRegisterHandler(allowRegisterHandler);
                // create drive server

                new DriveCreateController(standAloneAuth1).createDriveServerService("server", rootServer, 0.4f, 200, false);
                runner.r(() -> {
                    Lok.debug("FxTest.driveGui.1.booted");
//                DriveBootloader.deVinjector = null;
                    boot2.boot().done(standAloneAuth2 -> {
                        Lok.debug("FxTest.driveGui.2.booted");
                        standAloneAuth2.addRegisterHandler(allowRegisterHandler);
                        runner.r(() -> {
                            standAloneAuth2.connect("localhost", 8888, 8889, true).done(result -> {

                            }).fail(Lok::error);
//                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
//                        connectPromise.done(integer -> {
//                            runner.r(() -> {
//                                Lok.debug("FxTest.driveGui.booted");
//                                //standAloneAuth2.getBrotCaster().discover(9966);
//                                //lock.unlockWrite();
//                            });
//                        });
                        });
                    });
                });
            });
        });
        lock.lockWrite();
        lock.lockWrite();
        lock.unlockWrite();
    }


    @Test
    public void startBoth() throws Exception, SqlQueriesException {
//        inject(true);
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir2());
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings json1 = MeinAuthSettings.createDefaultSettings();
        json1.setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = MeinAuthSettings.createDefaultSettings()
                .setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir2()).setName("MA2").setGreeting("greeting2");

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

        MeinBoot boot1 = new MeinBoot(json1, new PowerManager(json1), DriveFXBootloader.class);
        MeinBoot boot2 = new MeinBoot(json2, new PowerManager(json2), DriveFXBootloader.class);
        boot2.addMeinAuthAdmin(new MeinAuthAdminFX());
        json1.setJsonFile(MeinAuthSettings.DEFAULT_FILE);
        json1.save();
        json2.setJsonFile(MeinAuthSettings.DEFAULT_FILE_2);
        json2.save();
        boot1.boot().done(standAloneAuth1 -> {
            standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.driveGui.1.booted");
//                DriveBootloader.deVinjector = null;
                boot2.boot().done(standAloneAuth2 -> {
                    Lok.debug("FxTest.driveGui.2.booted");
                    standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
                    runner.r(() -> {
//                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
//                        connectPromise.done(integer -> {
//                            runner.r(() -> {
//                                Lok.debug("FxTest.driveGui.booted");
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
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir2());
        N runner = new N(e -> e.printStackTrace());
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir2()).setName("MA2").setGreeting("greeting2");
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

        MeinBoot boot1 = new MeinBoot(json1, new PowerManager(json1), DriveFXBootloader.class);
        MeinBoot boot2 = new MeinBoot(json2, new PowerManager(json2), DriveFXBootloader.class);
        boot1.boot().done(standAloneAuth1 -> {
            standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.driveGui.1.booted");
//                DriveBootloader.deVinjector = null;
                boot2.boot().done(standAloneAuth2 -> {
                    Lok.debug("FxTest.driveGui.2.booted");
                    standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
                    runner.r(() -> {
                        Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect("localhost", 8888, 8889, true);
                        connectPromise.done(integer -> {
                            runner.r(() -> {
                                Lok.debug("FxTest.driveGui.booted");
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
        AFile testdir1 = AFile.instance(new File("testdir1"));
        AFile testdir2 = AFile.instance(new File("testdir2"));
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir2());
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir2);
        TestDirCreator.createTestDir(testdir1);
        testdir2.mkdirs();

        // configure MeinAuth
        N runner = new N(e -> e.printStackTrace());

        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir1()).setName("MA1").setGreeting("greeting1");
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MeinBoot.Companion.getDefaultWorkingDir2()).setName("MA2").setGreeting("greeting2");
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
        };
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (meinAuthService, registered) -> {
            List<ServiceJoinServiceType> services = meinAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        lock.lockWrite();

        MeinBoot boot1 = new MeinBoot(json1, new PowerManager(json1), DriveFXBootloader.class);
        MeinBoot boot2 = new MeinBoot(json2, new PowerManager(json2), DriveFXBootloader.class);
        boot1.boot().done(standAloneAuth1 -> {
            runner.r(() -> {
                Lok.debug("FxTest.driveGui.1.booted");
                standAloneAuth1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                DriveBootloader.DEV_DRIVE_BOOT_LISTENER = serverService -> new Thread(() -> N.r(() -> {
                    boot2.boot().done(standAloneAuth2 -> {
                        Lok.debug("FxTest.driveGui.2.booted");
                        standAloneAuth2.addRegisterHandler(allowRegisterHandler);
                        runner.r(() -> {
                            // connect first. this step will register
                            Promise<MeinValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect("localhost", 8888, 8889, true);
                            connectPromise.done(meinValidationProcess -> {
                                runner.r(() -> {
                                    Lok.debug("FxTest.driveGui.connected");
                                    // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                                    DriveBootloader.DEV_DRIVE_BOOT_LISTENER = clientDriveService -> new Thread(() -> N.r(() -> {
                                        Lok.debug("FxTest attempting first syncFromServer");
                                        clientSyncListener.testStructure.setMaClient(standAloneAuth2)
                                                .setMaServer(standAloneAuth1)
                                                .setClientDriveService((MeinDriveClientService) clientDriveService)
                                                .setServerDriveService((MeinDriveServerService) serverService)
                                                .setTestdir1(testdir1)
                                                .setTestdir2(testdir2);
                                        clientDriveService.setSyncListener(clientSyncListener);
                                        ((MeinDriveClientService) clientDriveService).syncThisClient();
                                    })).start();
                                    new DriveCreateController(standAloneAuth2).createDriveClientService("client service", testdir2, 1l, serverService.getUuid(), 0.1f, 30, false);
                                });
                            });
                        });
                    });
                })).start();
                new DriveCreateController(standAloneAuth1).createDriveServerService("server service", testdir1, 0.1f, 30, false);
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }


    @Test
    public void firstSync() throws Exception {
        Eva.enable();
        DriveTest driveTest = new DriveTest();
        MeinAuthSettings json1 = new DriveTest().createJson1();
        MeinAuthSettings json2 = new DriveTest().createJson2();
        MeinBoot meinBoot = new MeinBoot(json2, new PowerManager(json2), DriveBootloader.class).addMeinAuthAdmin(new MeinAuthAdmin() {
            @Override
            public void onProgress(MeinNotification notification, int max, int current, boolean indeterminate) {

            }

            @Override
            public void onCancel(MeinNotification notification) {

            }

            @Override
            public void onFinish(MeinNotification notification) {

            }

            @Override
            public void start(MeinAuthService meinAuthService) {

            }

            @Override
            public void onNotificationFromService(IMeinService meinService, MeinNotification notification) {

            }

            @Override
            public void onChanged() {

            }

            @Override
            public void shutDown() {

            }
        });
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
                            Lok.debug(f.getName().v());
                        }
                        File newFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub2.txt");
                        newFile.createNewFile();
                    } else if (count == 1) {
                        Lok.debug("FxTest.onSyncDoneImpl :)");
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
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
    }


}
