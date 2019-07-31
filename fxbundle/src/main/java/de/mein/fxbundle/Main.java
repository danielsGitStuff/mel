package de.mein.fxbundle;

import de.mein.AuthKonsoleReader;
import de.mein.Lok;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.gui.RegisterHandlerFX;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.service.MeinAuthFxLoader;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.DBLokImpl;
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
import de.mein.sql.*;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import de.mein.update.CurrentJar;
import javafx.embed.swing.JFXPanel;

import java.io.File;

/**
 * Created by xor on 1/15/17.
 */
@SuppressWarnings("Duplicates")
public class Main {
    private static void initMein() throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        CurrentJar.initCurrentJarClass(Main.class);
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
    }

    public static void main(String[] args) throws Exception {
        initMein();
//        F.rmRf(MeinBoot.defaultWorkingDir1);
//        F.rmRf(MeinAuthSettings.DEFAULT_FILE);
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinAuthSettings meinAuthSettings = AuthKonsoleReader.readKonsole(MeinStrings.update.VARIANT_FX, args);
        meinAuthSettings.save();
        if (meinAuthSettings.getPreserveLogLinesInDb() > 0L) {
            if (!meinAuthSettings.getWorkingDirectory().exists())
                meinAuthSettings.getWorkingDirectory().mkdirs();
            File logFile = new File(meinAuthSettings.getWorkingDirectory(), "log.db");
            DBLokImpl.setupDBLockImpl(logFile, meinAuthSettings.getPreserveLogLinesInDb());
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

        if (meinAuthSettings.isHeadless() || !canDisplay) {
            MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveBootloader.class, ContactsBootloader.class);
            meinBoot.boot().done(meinAuthService -> {
                Lok.debug("Main.main.booted (headless)");
                lock.unlockWrite();
            }).fail(exc -> {
                exc.printStackTrace();
            });
        } else {
            MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveFXBootloader.class, ContactsFXBootloader.class);
            MeinAuthFxLoader fxLoader = new MeinAuthFxLoader();
            meinBoot.addMeinAuthAdmin(fxLoader);
            meinBoot.boot().done(meinAuthService -> {
                RegisterHandlerFX registerHandlerFX = new RegisterHandlerFX();
                registerHandlerFX.setup(fxLoader.getMeinAuthAdminFX());
                meinAuthService.addRegisterHandler(registerHandlerFX);
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
