package de.mel.core.serialize.deserialize.entity;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.FieldDeserializer;
import de.mel.core.serialize.deserialize.FieldDeserializerFactory;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/23/15.
 */
public class SerializableEntityDeserializerFactory implements FieldDeserializerFactory {

    private static SerializableEntityDeserializerFactory ins;

    @Override
    public boolean canDeserialize(Field field) {
        return SerializableEntity.class.isAssignableFrom(field.getType());
    }

    @Override
    public FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        SerializableEntityDeserializer deserializer = new SerializableEntityDeserializer(rootDeserializer, field);
        return deserializer;
    }

    public static FieldDeserializerFactory getIntance() {
        if (ins == null)
            ins = new SerializableEntityDeserializerFactory();
        return ins;
    }
}
