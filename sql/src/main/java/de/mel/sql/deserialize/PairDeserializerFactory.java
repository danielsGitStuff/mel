package de.mel.sql.deserialize;

import de.mel.core.serialize.deserialize.FieldDeserializer;
import de.mel.core.serialize.deserialize.FieldDeserializerFactory;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.sql.Pair;
import de.mel.sql.serialize.PairSerializerFactory;

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
