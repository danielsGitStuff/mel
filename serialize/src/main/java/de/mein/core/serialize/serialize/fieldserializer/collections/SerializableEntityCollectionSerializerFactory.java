package de.mein.core.serialize.serialize.fieldserializer.collections;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.NullSerializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/20/15.
 */
public class SerializableEntityCollectionSerializerFactory implements FieldSerializerFactory {
    private static SerializableEntityCollectionSerializerFactory ins;

    private SerializableEntityCollectionSerializerFactory() {

    }

    public static FieldSerializerFactory getInstance() {
        if (ins == null)
            ins = new SerializableEntityCollectionSerializerFactory();
        return ins;
    }

    @Override
    public FieldSerializer createSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException, JsonSerializationException {
        field.setAccessible(true);
        Iterable<? extends SerializableEntity> iterable = (Iterable<? extends SerializableEntity>) field.get(parentSerializer.getEntity());
        if (iterable == null)
            return new NullSerializer();
        return new SerializableEntityCollectionSerializer(parentSerializer, iterable);
    }

    @Override
    public boolean canSerialize(Field field) {
        return FieldAnalyzer.isEntitySerializableCollection(field);
    }

    @Override
    public FieldSerializer createSerializerOnClass(SerializableEntitySerializer parentSerializer, Object value) {
        return null;
    }

}
