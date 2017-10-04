package de.mein;

import de.mein.KonsoleHandler;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.gui.RegisterHandlerFX;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.service.MeinAuthFxLoader;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.tools.WaitLock;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.drive.boot.DriveFXBootLoader;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import javafx.embed.swing.JFXPanel;
import mein.de.contacts.ContactsFXBootloader;

import java.io.IOException;

/**
 * Created by xor on 1/15/17.
 */
@SuppressWarnings("Duplicates")
public class Main {
    private static void init() throws IOException {
        new JFXPanel();
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
    }

    public static void main(String[] args) throws Exception {
        init();
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinAuthSettings meinAuthSettings = new KonsoleHandler().start(args);
        meinAuthSettings.save();
        MeinBoot meinBoot = new MeinBoot(meinAuthSettings, DriveFXBootLoader.class, ContactsFXBootloader.class);
        meinBoot.addMeinAuthAdmin(new MeinAuthFxLoader());
        meinBoot.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            System.out.println("Main.main.booted");
            lock.unlockWrite();
        }).fail(exc -> {
            exc.printStackTrace();
        });
        lock.lockWrite();
        lock.lockWrite();
        System.out.println("Main.main.end");
        new WaitLock().lock().lock();
    }

//    private static void init() throws IOException {
//        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
//        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
//    }
//
//    public static MeinAuthSettings createJson() {
//        return new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
//                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
//                .setWorkingDirectory(new File(MeinBoot.DEFAULT_WORKING_DIR_NAME)).setName("MeinAuthFX").setGreeting("greetz");
//    }
//
//    public static void main(String[] args) throws IOException, IllegalAccessException, JsonSerializationException, JsonDeserializationException {
//        init();
//        MeinAuthSettings settings;
//        File workingDir = new File(MeinBoot.DEFAULT_WORKING_DIR_NAME);
//        File settingsFile = new File(workingDir, MeinBoot.DEFAULT_WORKING_DIR_NAME);
//        if (settingsFile.exists()) {
//            settings = (MeinAuthSettings) MeinAuthSettings.load(settingsFile);
//        } else {
//            settings = createJson();
//        }
//        settings.save();
//        MeinBoot meinBoot = new MeinBoot(settings);
//        meinBoot.addMeinAuthAdmin(new MeinAuthFxLo)
//    }


}
