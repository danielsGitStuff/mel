package de.mein.fxbundle;

import de.mein.AuthKonsoleReader;
import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.gui.RegisterHandlerFX;
import de.mein.auth.service.MeinAuthFxLoader;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.contacts.ContactsBootloader;
import de.mein.contacts.ContactsFXBootloader;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.drive.DriveBootloader;
import de.mein.drive.bash.BashTools;
import de.mein.drive.boot.DriveFXBootloader;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import de.mein.update.CurrentJar;
import javafx.embed.swing.JFXPanel;

/**
 * Created by xor on 1/15/17.
 */
@SuppressWarnings("Duplicates")
public class Main {
    private static void init() throws Exception {
        new JFXPanel();
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        CurrentJar.initCurrentJarClass(Main.class);
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
    }

    public static void main(String[] args) throws Exception {
        init();
//        F.rmRf(MeinBoot.defaultWorkingDir1);
//        F.rmRf(MeinAuthSettings.DEFAULT_FILE);
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinAuthSettings meinAuthSettings = AuthKonsoleReader.readKonsole(MeinStrings.update.VARIANT_FX, args);
        meinAuthSettings.save();
        // todo debug, remove
        if (args.length == 1 && args[0].equals("-dev")) {
            MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveBootloader.class, ContactsBootloader.class);
            meinBoot.boot().done(meinAuthService -> {
                Lok.debug("Main.main.booted (DEV)");
                meinAuthService.addRegisterHandler(new IRegisterHandler() {
                    @Override
                    public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                        N.r(() -> N.forEach(meinAuthService.getCertificateManager().getTrustedCertificates(), oldeCert -> meinAuthService.getCertificateManager().deleteCertificate(oldeCert)));
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
                meinAuthService.addRegisteredHandler((meinAuthService1, registered) -> {
                    N.forEach(meinAuthService.getDatabaseManager().getAllServices(), serviceJoinServiceType -> meinAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v()));
                });
                lock.unlockWrite();
            }).fail(exc -> {
                exc.printStackTrace();
            });
        } else if (meinAuthSettings.isHeadless()) {
            MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveBootloader.class, ContactsBootloader.class);
            meinBoot.boot().done(meinAuthService -> {
                Lok.debug("Main.main.booted (headless)");
                lock.unlockWrite();
            }).fail(exc -> {
                exc.printStackTrace();
            });
        } else {
            MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveFXBootloader.class, ContactsFXBootloader.class);
            meinBoot.addMeinAuthAdmin(new MeinAuthFxLoader());
            meinBoot.boot().done(meinAuthService -> {
                meinAuthService.addRegisterHandler(new RegisterHandlerFX());
                Lok.debug("Main.main.booted");
                lock.unlockWrite();
            }).fail(exc -> {
                exc.printStackTrace();
            });
        }
        lock.lockWrite();
        lock.lockWrite();
        Lok.debug("Main.main.end");
        new WaitLock().lock().lock();
    }
}
