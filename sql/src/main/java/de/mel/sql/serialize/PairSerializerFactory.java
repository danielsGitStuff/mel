package de.mel.sql.serialize;

import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.NullSerializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.Pair;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/20/15.
 */
public class PairSerializerFactory implements FieldSerializerFactory {
    private static PairSerializerFactory ins;

    private PairSerializerFactory() {
    }

    public static PairSerializerFactory getInstance() {
        if (ins == null)
            ins = new PairSerializerFactory();
        return ins;
    }

    @Override
    public FieldSerializer createSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException, JsonSerializationException {
        field.setAccessible(true);
        Pair<?> pair = (Pair<?>) field.get(parentSerializer.getEntity());
        if (pair.ignoreListener().v() == null)
            return new NullSerializer();
        return new PairSerializer(pair);
    }

    @Override
    public boolean canSerialize(Field field) {
        return Pair.class.isAssignableFrom(field.getType());
    }

    @Override
    public FieldSerializer createSerializerOnClass(SerializableEntitySerializer parentSerializer, Object value) {
        return null;
    }

}
