package de.mel.fxbundle.test;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.file.IFile;
import de.mel.auth.gui.RegisterHandlerFX;
import de.mel.auth.service.MelAuthFxLoader;
import de.mel.auth.service.MelBoot;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.tools.DBLokImpl;
import de.mel.auth.tools.N;
import de.mel.auth.tools.WaitLock;
import de.mel.contacts.ContactsBootloader;
import de.mel.contacts.ContactsFXBootloader;
import de.mel.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mel.dump.DumpBootloader;
import de.mel.dump.DumpFxBootloader;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.boot.FileSyncFXBootloader;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.fxbundle.AuthKonsoleReader;
import de.mel.sql.RWLock;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;
import de.mel.update.CurrentJar;
import javafx.embed.swing.JFXPanel;

import java.io.File;

/**
 * Created by xor on 1/15/17.
 */
@SuppressWarnings("Duplicates")
public class Scenario1 {
    private static void initMel() throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        CurrentJar.initCurrentJarClass(Scenario1.class);
        AbstractFile.configure( new DefaultFileConfiguration());
        BashTools.Companion.init();
    }

    public static void main(String[] args) throws Exception {
        initMel();
        // clean
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        CertificateManager.deleteDirectory(MelAuthSettings.DEFAULT_FILE);
        RWLock lock = new RWLock();
        lock.lockWrite();
        MelAuthSettings melAuthSettings = AuthKonsoleReader.readKonsole(MelStrings.update.VARIANT_FX, new String[0]);
        melAuthSettings.save();
        if (melAuthSettings.getPreserveLogLinesInDb() > 0L) {
            if (!melAuthSettings.getWorkingDirectory().exists())
                melAuthSettings.getWorkingDirectory().mkdirs();
            File logFile = new File(melAuthSettings.getWorkingDirectory(), "log.db");
            DBLokImpl.setupDBLockImpl(logFile, melAuthSettings.getPreserveLogLinesInDb());
        }
        final boolean canDisplay = N.result(() -> {
            new JFXPanel();
            return true;
        }, false);
        if (!canDisplay) {
            Lok.error("Could not initialize UI. Starting headless instead.");
            Lok.error("You won't be able to interact (pair, grant access, solve conflicts...) with Mel");
            Lok.error("Otherwise it will synchronize as usual.");
        }

        if (melAuthSettings.isHeadless() || !canDisplay) {
            MelBoot melBoot = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), FileSyncBootloader.class, ContactsBootloader.class, DumpBootloader.class);
            melBoot.boot().done(melAuthService -> {
                Lok.debug("Main.main.booted (headless)");
                lock.unlockWrite();
            }).fail(Throwable::printStackTrace);
        } else {
            MelBoot melBoot = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), FileSyncFXBootloader.class, ContactsFXBootloader.class, DumpFxBootloader.class);
            MelAuthFxLoader fxLoader = new MelAuthFxLoader();
            melBoot.addMelAuthAdmin(fxLoader);
            melBoot.boot().done(melAuthService -> {
//                RegisterHandlerFX registerHandlerFX = new RegisterHandlerFX();
//                registerHandlerFX.setup(fxLoader.getMelAuthAdminFX());
//                melAuthService.addRegisterHandler(registerHandlerFX);
                melAuthService.addRegisterHandler(new IRegisterHandler() {
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
                });
                melAuthService.addRegisteredHandler((melAuthService1, registered) -> {
                    N.forEach(melAuthService1.getDatabaseManager().getAllServices(), serviceType -> melAuthService1.getDatabaseManager().grant(serviceType.getServiceId().v(), registered.getId().v()));
                });
                Lok.debug("Main.main.booted");
                FileSyncCreateServiceHelper helper = new FileSyncCreateServiceHelper(melAuthService);
                N.r(() -> {
                    IFile root = AbstractFile.instance(args[0]);
                    helper.createServerService("test service", root, 0.5f, 20, false);
                });
                lock.unlockWrite();
            }).fail(Throwable::printStackTrace);
        }
        lock.lockWrite();
        Lok.debug("Main.main.end");
        new WaitLock().lock().lock();
    }
}
