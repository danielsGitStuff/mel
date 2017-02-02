package de.mein.drive;

import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.service.MeinAuthService;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.factories.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;

/**
 * Created by xor on 7/11/16.
 */
public class Main {

    private static void init() throws IOException {
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        MeinBoot.addBootLoaderClass(DriveBootLoader.class);
    }

    public static void main(String[] args) throws Exception {
        init();
        RWLock lock = new RWLock();
        lock.lockWrite();
        MeinAuthSettings meinAuthSettings = (MeinAuthSettings) new MeinAuthSettings()
                .setPort(8888)
                .setDeliveryPort(8889)
                .setName("meinauth")
                .setBrotcastListenerPort(9966)
                .setBrotcastPort(6699)
                .setWorkingDirectory(MeinBoot.defaultWorkingDir1)
                .setJsonFile(new File("meinAuth.settings.json"));
        meinAuthSettings.save();
        MeinBoot meinBoot = new MeinBoot();
        Promise<MeinAuthService, Exception, Void> meinAuthBooted = meinBoot.boot(meinAuthSettings);
        meinAuthBooted.done(result -> {
            System.out.println("Main.main.booted");
            lock.unlockWrite();
        });
        lock.lockWrite();
        lock.lockWrite();
        System.out.println("Main.main.end");
    }
}
