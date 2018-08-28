package de.mein.sql.deserialize;

import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.sql.Pair;
import de.mein.sql.serialize.PairSerializerFactory;

import java.lang.reflect.Field;

/**
 * Created by xor on 1/14/16.
 */
public class PairDeserializerFactory implements FieldDeserializerFactory {

    static {
        addToRepo();
    }

    public static void addToRepo() {
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
    }

    private static PairDeserializerFactory ins;

    public static PairDeserializerFactory getInstance() {
        if (ins == null)
            ins = new PairDeserializerFactory();
        return ins;
    }

    @Override
    public boolean canDeserialize(Field field) {
        return Pair.class.isAssignableFrom(field.getType());
    }

    @Override
    public FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        return new PairDeserializer(rootDeserializer,field);
    }
}
