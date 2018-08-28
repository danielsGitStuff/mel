package de.mein.auth;

import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.sql.deserialize.PairCollectionDeserializerFactory;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairCollectionSerializer;
import de.mein.sql.serialize.PairCollectionSerializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;

/**
 * Created by xor on 09.08.2016.
 */
public class MeinBootLoader implements Runnable {
    static {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance());

    }

    @Override
    public void run() {
        while (true)
            System.out.println("MeinBootLoader.runTry");
    }
}
