package de.mel.core.serialize.serialize.fieldserializer.binary;

import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.NullSerializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.lang.reflect.Field;

/**
 * Created by xor on 2/26/16.
 */
public class BinarySerializerFactory implements FieldSerializerFactory {
    private static BinarySerializerFactory ins;

    @Override
    public FieldSerializer createSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException, JsonSerializationException {
        field.setAccessible(true);
        byte[] whatever = (byte[]) field.get(parentSerializer.getEntity());
        if (whatever == null)
            return new NullSerializer();
        return new BinaryFieldSerializer(whatever);
    }

    @Override
    public boolean canSerialize(Field field) {
        return field.getType().equals(byte[].class);
    }

    @Override
    public FieldSerializer createSerializerOnClass(SerializableEntitySerializer parentSerializer, Object value) {
        return null;
    }

     public static BinarySerializerFactory getInstance() {
        if (ins == null)
            ins = new BinarySerializerFactory();
        return ins;
    }
}
