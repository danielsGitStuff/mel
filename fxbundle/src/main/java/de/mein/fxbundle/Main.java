package de.mein.fxbundle;

import de.mein.AuthKonsoleReader;
import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.gui.RegisterHandlerFX;
import de.mein.auth.service.MeinAuthFxLoader;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.F;
import de.mein.auth.tools.WaitLock;
import de.mein.contacts.ContactsFXBootloader;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.drive.bash.BashTools;
import de.mein.drive.boot.DriveFXBootLoader;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import de.mein.update.CurrentJar;
import javafx.embed.swing.JFXPanel;

import java.io.IOException;

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
        MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveFXBootLoader.class, ContactsFXBootloader.class);
        meinBoot.addMeinAuthAdmin(new MeinAuthFxLoader());
        meinBoot.boot().done(meinAuthService -> {
            meinAuthService.addRegisterHandler(new RegisterHandlerFX());
            Lok.debug("Main.main.booted");
            lock.unlockWrite();
        }).fail(exc -> {
            exc.printStackTrace();
        });
        lock.lockWrite();
        lock.lockWrite();
        Lok.debug("Main.main.end");
        new WaitLock().lock().lock();
    }
}
