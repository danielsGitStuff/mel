package de.mel.fxbundle;

import de.mel.AuthKonsoleReader;
import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.file.AFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.gui.RegisterHandlerFX;
import de.mel.auth.service.MelAuthFxLoader;
import de.mel.auth.service.MelBoot;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.DBLokImpl;
import de.mel.auth.tools.N;
import de.mel.auth.tools.WaitLock;
import de.mel.contacts.ContactsBootloader;
import de.mel.contacts.ContactsFXBootloader;
import de.mel.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.bash.BashTools;
import de.mel.drive.boot.FileSyncFXBootloader;
import de.mel.dump.DumpBootloader;
import de.mel.dump.DumpFxBootloader;
import de.mel.sql.*;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;
import de.mel.update.CurrentJar;
import javafx.embed.swing.JFXPanel;

import java.io.File;

/**
 * Created by xor on 1/15/17.
 */
@SuppressWarnings("Duplicates")
public class Main {
    private static void initMel() throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        CurrentJar.initCurrentJarClass(Main.class);
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
    }

    public static void main(String[] args) throws Exception {
        initMel();
//        F.rmRf(MelBoot.defaultWorkingDir1);
//        F.rmRf(MelAuthSettings.DEFAULT_FILE);
        RWLock lock = new RWLock();
        lock.lockWrite();
        MelAuthSettings melAuthSettings = AuthKonsoleReader.readKonsole(MelStrings.update.VARIANT_FX, args);
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
            }).fail(exc -> {
                exc.printStackTrace();
            });
        } else {
            MelBoot melBoot = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), FileSyncFXBootloader.class, ContactsFXBootloader.class, DumpFxBootloader.class);
            MelAuthFxLoader fxLoader = new MelAuthFxLoader();
            melBoot.addMelAuthAdmin(fxLoader);
            melBoot.boot().done(melAuthService -> {
                RegisterHandlerFX registerHandlerFX = new RegisterHandlerFX();
                registerHandlerFX.setup(fxLoader.getMelAuthAdminFX());
                melAuthService.addRegisterHandler(registerHandlerFX);
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
