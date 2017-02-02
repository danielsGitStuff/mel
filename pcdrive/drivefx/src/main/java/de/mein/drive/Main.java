package de.mein.drive;

import de.mein.KonsoleHandler;
import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.gui.RegisterHandlerFX;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinStandAloneAuthFX;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.drive.boot.DriveFXBootLoader;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.factories.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import org.jdeferred.Promise;

import java.io.IOException;

/**
 * Created by xor on 1/15/17.
 */
@SuppressWarnings("Duplicates")
public class Main {
    private static void init() throws IOException {
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        MeinBoot.addBootLoaderClass(DriveFXBootLoader.class);
    }

    public static void main(String[] args) throws Exception {
        init();
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinAuthSettings meinAuthSettings = new KonsoleHandler().start(args);
        meinAuthSettings.save();
        MeinStandAloneAuthFX meinAuthService = new MeinStandAloneAuthFX(meinAuthSettings);
        meinAuthService.addRegisterHandler(new RegisterHandlerFX());
        MeinBoot meinBoot = new MeinBoot();
        Promise<MeinAuthService, Exception, Void> meinAuthBooted = meinBoot.boot(meinAuthService);
        meinAuthBooted.done(result -> {
            System.out.println("Main.main.booted");
            lock.unlockWrite();
        });
        lock.lockWrite();
        lock.lockWrite();
        System.out.println("Main.main.end");
    }
}
