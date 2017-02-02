package de.mein.core.serialize.serialize.fieldserializer.map;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.lang.reflect.Field;

/**
 * Created by xor on 1/13/17.
 */
class KeySerializerFactory implements FieldSerializerFactory {

    private final SerializableEntitySerializer parentSerializer;

    KeySerializerFactory(SerializableEntitySerializer parentSerializer) {
        this.parentSerializer = parentSerializer;
    }

    @Override
    public FieldSerializer createSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException, JsonSerializationException {
        return null;
    }

    @Override
    public boolean canSerialize(Field field) {
        return true;
    }

    @Override
    public FieldSerializer createSerializerOnClass(FieldSerializer parentSerializer, Object value) {
        return new KeySerializer(this.parentSerializer, value);
    }
}
