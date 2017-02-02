package de.mein.core.serialize.deserialize.primitive;

import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.primitive.PrimitiveFieldSerializerFactory;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/25/15.
 */
public class PrimitiveDeserializerFactory implements FieldDeserializerFactory {
    private static PrimitiveDeserializerFactory ins;

    public PrimitiveDeserializerFactory() {

    }

    public static PrimitiveDeserializerFactory getInstance() {
        if (ins == null)
            ins = new PrimitiveDeserializerFactory();
        return ins;
    }

    @Override
    public boolean canDeserialize(Field field) {
        return PrimitiveFieldSerializerFactory.getInstance().canSerialize(field);
    }

    @Override
    public FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        return new PrimitiveDeserializer();
    }
}
