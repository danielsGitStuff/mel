package de.mel.auth;

import de.mel.Lok;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.sql.deserialize.PairCollectionDeserializerFactory;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairCollectionSerializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;

/**
 * Created by xor on 09.08.2016.
 */
public class MelBootLoader implements Runnable {
    static {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance());

    }

    @Override
    public void run() {
        while (true)
            Lok.debug("MelBootLoader.runTry");
    }
}
