package de.mel.core.serialize.serialize.fieldserializer.map;

import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

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
    public FieldSerializer createSerializerOnClass(SerializableEntitySerializer parentSerializer, Object value) {
        return null;
    }
}
