package de.mein.core.serialize.deserialize.binary;

import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;

import java.lang.reflect.Field;

/**
 * Created by xor on 2/26/16.
 */
public class BinaryDeserializerFactory implements FieldDeserializerFactory {

    private static BinaryDeserializerFactory ins;

    public static BinaryDeserializerFactory getInstance() {
        if (ins == null)
            ins = new BinaryDeserializerFactory();
        return ins;
    }

    @Override
    public boolean canDeserialize(Field field) {
        return field.getType().equals(byte[].class);
    }

    @Override
    public FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        return new BinaryDeserializer();
    }
}
