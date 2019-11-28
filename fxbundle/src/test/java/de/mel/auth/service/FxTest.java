package de.mel.auth.service;


import de.mel.Lok;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelNotification;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.access.DatabaseManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.AbstractFileWriter;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.file.IFile;
import de.mel.auth.gui.RegisterHandlerFX;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.socket.process.reg.IRegisteredHandler;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.tools.Eva;
import de.mel.auth.tools.N;
import de.mel.auth.tools.WaitLock;
import de.mel.contacts.ContactsBootloader;
import de.mel.contacts.ContactsFXBootloader;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.data.ContactsSettings;
import de.mel.contacts.data.db.dao.ContactsDao;
import de.mel.contacts.data.db.dao.PhoneBookDao;
import de.mel.contacts.service.ContactsService;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.FileSyncSyncListener;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.boot.FileSyncFXBootloader;
import de.mel.filesync.serialization.TestDirCreator;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.FsFile;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.sql.*;
import org.jdeferred.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 09.09.2016.
 */
@SuppressWarnings("Duplicates")
public class FxTest {

    private static MelStandAloneAuthFX standAloneAuth2;
    private static MelStandAloneAuthFX standAloneAuth1;
    private static RWLock lock = new RWLock();

    @Test
    public void conflict() throws Exception {
        DriveTest driveTest = new DriveTest();
        MelAuthSettings json2 = new DriveTest().createJson2();
        MelBoot melBoot = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class).addMelAuthAdmin(new MelAuthFxLoader());
        MelBoot restartMelBoot = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class).addMelAuthAdmin(new MelAuthFxLoader());
        driveTest.simpleClientConflictImpl(melBoot, null);
        new WaitLock().lock().lock();
    }

    @Test
    public void startUpConflicts() throws Exception {
        DriveTest driveTest = new DriveTest();
        MelAuthSettings json2 = new DriveTest().createJson2();
        MelBoot melBoot = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class).addMelAuthAdmin(new MelAuthFxLoader());
        driveTest.startUpConflicts(melBoot);
        new WaitLock().lock().lock();
    }

    @Test
    public void complexConflict() throws Exception {
        Eva.enable();
        DriveTest driveTest = new DriveTest();
        driveTest.before();
        MelAuthSettings json1 = new DriveTest().createJson1();
        MelAuthSettings json2 = new DriveTest().createJson2();


        MelBoot melBoot = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class, ContactsFXBootloader.class).addMelAuthAdmin(new MelAuthFxLoader());

//        MelBoot restartMelBoot = new MelBoot(json1, new PowerManager(json1), DriveFXBootloader.class, ContactsFXBootloader.class).addMelAuthAdmin(new MelAuthFxLoader());
        driveTest.complexClientConflictImpl(melBoot, null);
        new WaitLock().lock().lock();
    }


    @Test
    public void startEmptyClient() throws Exception {
        //CertificateManager.deleteDirectory(MelBoot.defaultWorkingDir1);
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir2());
        N runner = new N(e -> e.printStackTrace());
        MelAuthSettings json1 = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("Test Client");
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
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        //todo continue gui
        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncFXBootloader.class)
                .addMelAuthAdmin(new MelAuthFxLoader());
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
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        N runner = new N(e -> e.printStackTrace());
        MelStandAloneAuthFX standAloneAuth1;
        MelAuthSettings json1 = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("Test Server");
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
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncFXBootloader.class, ContactsFXBootloader.class);
        boot1.addMelAuthAdmin(new MelAuthFxLoader());
        boot1.boot().done(melAuthService -> {
            melAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyServer.booted");
            });
//            N.r(() -> {
//                DriveCreateController createController = new DriveCreateController(melAuthService);
//                createController.createDriveServerService("testiServer", testdir.getAbsolutePath(),0.1f,30);
//            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }

    private void connectAcceptingClient() throws Exception {
        IFile testdir = AbstractFile.instance(new File("testdir2"));
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir2());
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MelAuthSettings melAuthSettings = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("Test Client");
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
        };
        IRegisteredHandler allowRegisteredHandler = (melAuthService, registered) -> {
            DatabaseManager databaseManager = melAuthService.getDatabaseManager();
            for (ServiceJoinServiceType service : databaseManager.getAllServices()) {
                databaseManager.grant(service.getServiceId().v(), registered.getId().v());
            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MelBoot boot1 = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), FileSyncFXBootloader.class);
        boot1.addMelAuthAdmin(new MelAuthFxLoader());
        boot1.boot().done(melAuthService -> {
            melAuthService.addRegisterHandler(allowRegisterHandler);
            melAuthService.addRegisteredHandler(allowRegisteredHandler);
//            melAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                Promise<MelValidationProcess, Exception, Void> connected = melAuthService.connect("127.0.0.1", 8888, 8889, true);
                connected.done(result -> N.r(() -> {
                    FileSyncCreateServiceHelper createController = new FileSyncCreateServiceHelper(melAuthService);
                    createController.createClientService("drive client", testdir, 1L, tmp, 0.1f, 30, false);
                    Lok.debug("FxTest.connectAcceptingClient");
                }));

            });
        });
    }

    public static String tmp;

    @Test
    public void connectAccepting() throws Exception {
        IFile testdir = AbstractFile.instance(new File("testdir1"));
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MelAuthSettings melAuthSettings = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("Test Server");
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
        };
        IRegisteredHandler allowRegisteredHandler = (melAuthService, registered) -> {
            DatabaseManager databaseManager = melAuthService.getDatabaseManager();
            for (ServiceJoinServiceType service : databaseManager.getAllServices()) {
                databaseManager.grant(service.getServiceId().v(), registered.getId().v());
            }
        };
        RWLock lock = new RWLock();
        lock.lockWrite();
        MelBoot boot1 = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), FileSyncFXBootloader.class);
        boot1.addMelAuthAdmin(new MelAuthFxLoader());
        boot1.boot().done(melAuthService -> {
            melAuthService.addRegisterHandler(allowRegisterHandler);
            melAuthService.addRegisteredHandler(allowRegisteredHandler);
//            melAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                FileSyncCreateServiceHelper createController = new FileSyncCreateServiceHelper(melAuthService);
                FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> new Thread(() -> N.r(() -> {
                    FxTest.tmp = driveService.getUuid();
                    connectAcceptingClient();
                })).start();
                createController.createServerService("testiServer", testdir, 0.1f, 30, false);
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
        IFile testdir = AbstractFile.instance(new File("testdir1"));
        CertificateManager.deleteDirectory(testdir);
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        TestDirCreator.createTestDir(testdir);
        N runner = new N(e -> e.printStackTrace());
        MelAuthSettings melAuthSettings = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("Test Server");
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
        };
        IRegisteredHandler allowRegisteredHandler = (melAuthService, registered) -> {
            DatabaseManager databaseManager = melAuthService.getDatabaseManager();
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
        MelBoot boot1 = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), FileSyncFXBootloader.class, ContactsFXBootloader.class);
        boot1.addMelAuthAdmin(new MelAuthFxLoader());
        boot1.boot().done(melAuthService -> {
            melAuthService.addRegisterHandler(allowRegisterHandler);
            melAuthService.addRegisteredHandler(allowRegisteredHandler);
//            melAuthService.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.startEmptyServer.booted");
            });
            N.r(() -> {
                FileSyncCreateServiceHelper createController = new FileSyncCreateServiceHelper(melAuthService);
                createController.createServerService("testiServer", testdir, 0.1f, 30, false);
                // create contacts server too

                ContactsSettings settings = new ContactsSettings();
                settings.setRole(ContactStrings.ROLE_SERVER);
                settings.setMasterPhoneBookId(1L);
                settings.setJsonFile(new File(MelBoot.Companion.getDefaultWorkingDir1(), "contactserversettings.json"));
                ContactsBootloader contactsBootloader = (ContactsBootloader) melAuthService.getMelBoot().getBootLoader(ContactStrings.TYPE);
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

    @Test
    public void startupConflict2() throws Exception {
        Eva.enable();
        MelAuthSettings json1 = MelAuthSettings.createDefaultSettings();
        json1.setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MA1");
        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncBootloader.class);
        boot1.boot().done(mas1 -> N.r(() -> {

            MelAuthSettings json2 = MelAuthSettings.createDefaultSettings()
                    .setPort(8890).setDeliveryPort(8891)
                    .setBrotcastPort(9966) // does not listen! only one listener seems possible
                    .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                    .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("MA2");
            MelBoot boot2 = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class);
            boot2.addMelAuthAdmin(new MelAuthFxLoader());
            boot2.boot().done(result -> N.r(() -> {
                Lok.debug("NANANA");
                Lok.debug("NANANA");
                Lok.debug("NANANA");
                Lok.debug("NANANA");
                Lok.debug("NANANA");
                Lok.debug("NANANA");
                Lok.debug("NANANA");
                Lok.debug("NANANA");
                Lok.debug("NANANA");
                Lok.debug("NANANA");
            }));
        }));


        lock.lockWrite().lockWrite();
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
    public void startupConflict1() throws Exception, SqlQueriesException {
//        inject(true);
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir2());
        N runner = new N(e -> e.printStackTrace());
        MelAuthSettings json1 = MelAuthSettings.createDefaultSettings();
        json1.setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MA1");
        MelAuthSettings json2 = MelAuthSettings.createDefaultSettings()
                .setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("MA2");

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
        };

        lock.lockWrite();
        IFile rootServer = AbstractFile.instance("t1");
        IFile rootClient = AbstractFile.instance("t2");
        BashTools.Companion.rmRf(rootClient);
        BashTools.Companion.rmRf(rootClient);
        IFile alteredFile = AbstractFile.instance("t2" + File.separator + "samedir" + File.separator + "same1.txt");
        TestDirCreator.createTestDir(rootServer);
        rootClient.mkdirs();
        rootServer.mkdirs();
        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncFXBootloader.class);
        MelBoot boot2 = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class);
        json1.setJsonFile(MelAuthSettings.DEFAULT_FILE);
        json1.save();
        json2.setJsonFile(MelAuthSettings.DEFAULT_FILE_2);
        json2.save();
        AtomicReference<MelFileSyncClientService> clientService = new AtomicReference<>();
        AtomicReference<MelAuthService> client = new AtomicReference<>();
        FileSyncSyncListener syncListener = new FileSyncSyncListener() {
            @Override
            public void onSyncFailed() {
                Lok.debug();
            }

            @Override
            public void onTransfersDone() {
                N.r(() -> {
                    // wait for FileDistributor
                    Thread.sleep(1000);
                    // change fs table
                    FsDao fsDao = clientService.get().getFileSyncDatabaseManager().getFsDao();
                    FsFile alterFs = fsDao.getFsFileByFile(new File(alteredFile.getAbsolutePath()));
                    alterFs.getSynced().v(false);
                    alterFs.getiNode().nul();
                    alterFs.getModified().nul();
                    fsDao.update(alterFs);
                    client.get().shutDown();
                    Thread.sleep(2000);
                    AbstractFileWriter out = alteredFile.writer();
                    out.append("hurrdurr".getBytes(), 0);
                    out.close();
                    System.exit(0);
//                    MelBoot boot3 = new MelBoot(json2, new PowerManager(json2), DriveFXBootloader.class);
//                    boot3.addMelAuthAdmin(new MelAuthFxLoader());
//                    boot3.boot();
                });
            }

            @Override
            public void onSyncDoneImpl() {
                Lok.debug();
            }
        };
        boot1.boot().done(mas1 -> {
            N.r(() -> {
                new FileSyncCreateServiceHelper(mas1).createServerService("server", rootServer, 0.4f, 200, false);
                mas1.addRegisterHandler(allowRegisterHandler);
                mas1.addRegisteredHandler((melAuthService, registered) ->
                        N.forEach(mas1.getDatabaseManager().getAllServices(),
                                serviceJoinServiceType -> mas1.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v())));
                // create drive server

                N.r(() -> {
                    Lok.debug("FxTest.driveGui.1.booted");
//                DriveBootloader.deVinjector = null;
                    boot2.boot().done(mas2 -> {
                        Lok.debug("FxTest.driveGui.2.booted");
                        mas2.addRegisterHandler(allowRegisterHandler);
                        runner.r(() -> {
                            client.set(mas2);
                            mas2.addRegisteredHandler((melAuthService, registered) -> N.r(() -> {
                                String serverServiceUuid = mas1.getDatabaseManager().getAllServices().iterator().next().getUuid().v();
                                new FileSyncCreateServiceHelper(mas2).createClientService("client", rootClient, 1L, serverServiceUuid, 0.5f, 300, false);
                                clientService.set((MelFileSyncClientService) mas2.getMelServices().iterator().next());
                                clientService.get().setSyncListener(syncListener);
                            }));
                            mas2.connect("localhost", 8888, 8889, true);
//                        Promise<MelValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
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
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir2());
        N runner = new N(e -> e.printStackTrace());
        MelAuthSettings json1 = MelAuthSettings.createDefaultSettings();
        json1.setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MA1");
        MelAuthSettings json2 = MelAuthSettings.createDefaultSettings()
                .setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("MA2");

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
        };



/*
        IDBCreatedListener admin = databaseManager -> {
            ServiceType serviceType = databaseManager.createServiceType("test type", "test type desc");
            databaseManager.createService(serviceType.getId().v(), "service uuid");
        };*/
        //standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
        //standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
        /*standAloneAuth1.addRegisteredHandler((melAuthService, registered) -> {
            List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                melAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        });*/
        lock.lockWrite();

        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncFXBootloader.class);
        MelBoot boot2 = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class);
        boot2.addMelAuthAdmin(new MelAuthAdminFX());
        json1.setJsonFile(MelAuthSettings.DEFAULT_FILE);
        json1.save();
        json2.setJsonFile(MelAuthSettings.DEFAULT_FILE_2);
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
//                        Promise<MelValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect(null, "localhost", 8888, 8889, true);
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
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir2());
        N runner = new N(e -> e.printStackTrace());
        MelAuthSettings json1 = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MA1");
        MelAuthSettings json2 = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("MA2");
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
        };



/*
        IDBCreatedListener admin = databaseManager -> {
            ServiceType serviceType = databaseManager.createServiceType("test type", "test type desc");
            databaseManager.createService(serviceType.getId().v(), "service uuid");
        };*/
        //standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
        //standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
        /*standAloneAuth1.addRegisteredHandler((melAuthService, registered) -> {
            List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                melAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        });*/
        lock.lockWrite();

        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncFXBootloader.class);
        MelBoot boot2 = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class);
        boot1.boot().done(standAloneAuth1 -> {
            standAloneAuth1.addRegisterHandler(new RegisterHandlerFX());
            runner.r(() -> {
                Lok.debug("FxTest.driveGui.1.booted");
//                DriveBootloader.deVinjector = null;
                boot2.boot().done(standAloneAuth2 -> {
                    Lok.debug("FxTest.driveGui.2.booted");
                    standAloneAuth2.addRegisterHandler(new RegisterHandlerFX());
                    runner.r(() -> {
                        Promise<MelValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect("localhost", 8888, 8889, true);
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


    public void setup(FileSyncSyncListener clientSyncListener) throws Exception, SqlQueriesException {
        //setup working directories & directories with test data
        IFile testdir1 = AbstractFile.instance(new File("testdir1"));
        IFile testdir2 = AbstractFile.instance(new File("testdir2"));
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir2());
        CertificateManager.deleteDirectory(testdir1);
        CertificateManager.deleteDirectory(testdir2);
        TestDirCreator.createTestDir(testdir1);
        testdir2.mkdirs();

        // configure MelAuth
        N runner = new N(e -> e.printStackTrace());

        MelAuthSettings json1 = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MA1");
        MelAuthSettings json2 = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("MA2");
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
        };
        // we want to allow every registered Certificate to talk to all available Services
        IRegisteredHandler registeredHandler = (melAuthService, registered) -> {
            List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
            for (ServiceJoinServiceType serviceJoinServiceType : services) {
                melAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
            }
        };
        lock.lockWrite();

        MelBoot boot1 = new MelBoot(json1, new PowerManager(json1), FileSyncFXBootloader.class);
        MelBoot boot2 = new MelBoot(json2, new PowerManager(json2), FileSyncFXBootloader.class);
        boot1.boot().done(standAloneAuth1 -> {
            runner.r(() -> {
                Lok.debug("FxTest.driveGui.1.booted");
                standAloneAuth1.addRegisteredHandler(registeredHandler);
                // setup the server Service
                FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = serverService -> new Thread(() -> N.r(() -> {
                    boot2.boot().done(standAloneAuth2 -> {
                        Lok.debug("FxTest.driveGui.2.booted");
                        standAloneAuth2.addRegisterHandler(allowRegisterHandler);
                        runner.r(() -> {
                            // connect first. this step will register
                            Promise<MelValidationProcess, Exception, Void> connectPromise = standAloneAuth2.connect("localhost", 8888, 8889, true);
                            connectPromise.done(melValidationProcess -> {
                                runner.r(() -> {
                                    Lok.debug("FxTest.driveGui.connected");
                                    // MAs know each other at this point. setup the client Service. it wants some data from the steps before
                                    FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = clientDriveService -> new Thread(() -> N.r(() -> {
                                        Lok.debug("FxTest attempting first syncFromServer");
                                        clientSyncListener.testStructure.setMaClient(standAloneAuth2)
                                                .setMaServer(standAloneAuth1)
                                                .setClientDriveService((MelFileSyncClientService) clientDriveService)
                                                .setServerDriveService((MelFileSyncServerService) serverService)
                                                .setTestdir1(testdir1)
                                                .setTestdir2(testdir2);
                                        clientDriveService.setSyncListener(clientSyncListener);
                                        ((MelFileSyncClientService) clientDriveService).syncThisClient();
                                    })).start();
                                    new FileSyncCreateServiceHelper(standAloneAuth2).createClientService("client service", testdir2, 1l, serverService.getUuid(), 0.1f, 30, false);
                                });
                            });
                        });
                    });
                })).start();
                new FileSyncCreateServiceHelper(standAloneAuth1).createServerService("server service", testdir1, 0.1f, 30, false);
            });
        });
        lock.lockWrite();
        lock.unlockWrite();
    }


    @Test
    public void firstSync() throws Exception {
        Eva.enable();
        DriveTest driveTest = new DriveTest();
        MelAuthSettings json1 = new DriveTest().createJson1();
        MelAuthSettings json2 = new DriveTest().createJson2();
        MelBoot melBoot = new MelBoot(json2, new PowerManager(json2), FileSyncBootloader.class).addMelAuthAdmin(new MelAuthAdmin() {
            @Override
            public void onProgress(MelNotification notification, int max, int current, boolean indeterminate) {

            }

            @Override
            public void onCancel(MelNotification notification) {

            }

            @Override
            public void onFinish(MelNotification notification) {

            }

            @Override
            public void start(MelAuthService melAuthService) {

            }

            @Override
            public void onNotificationFromService(IMelService melService, MelNotification notification) {

            }

            @Override
            public void onChanged() {

            }

            @Override
            public void shutDown() {

            }
        });
        driveTest.simpleTransferFromServerToClient(melBoot);
        new WaitLock().lock().lock();
    }


    @Test
    public void addFile() throws Exception {
        setup(new FileSyncSyncListener() {
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
                        FileSyncDatabaseManager dbManager = testStructure.clientDriveService.getFileSyncDatabaseManager();
                        List<FsFile> rootFiles = dbManager.getFsDao().getFilesByFsDirectory(null);
                        for (FsFile f : rootFiles) {
                            Lok.debug(f.getName().v());
                        }
                        File newFile = new File(testStructure.testdir1.getAbsolutePath() + "/sub1/sub2.txt");
                        newFile.createNewFile();
                    } else if (count == 1) {
                        Lok.debug("FxTest.onSyncDoneImpl :)");
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
        AbstractFile.configure(new DefaultFileConfiguration());
        BashTools.Companion.init();
    }


}
