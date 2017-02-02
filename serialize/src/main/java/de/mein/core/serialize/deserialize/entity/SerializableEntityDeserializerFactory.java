package de.mein.core.serialize.deserialize.entity;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;

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
