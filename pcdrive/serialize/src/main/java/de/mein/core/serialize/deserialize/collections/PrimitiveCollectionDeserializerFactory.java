package de.mein.core.serialize.deserialize.collections;

import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/12/16.
 */
public class PrimitiveCollectionDeserializerFactory implements FieldDeserializerFactory {

    private static PrimitiveCollectionDeserializerFactory ins;

    public static PrimitiveCollectionDeserializerFactory getInstance() {
        if (ins == null)
            ins = new PrimitiveCollectionDeserializerFactory();
        return ins;
    }

    @Override
    public boolean canDeserialize(Field field) {
        return FieldAnalyzer.isPrimitiveCollection(field);
    }

    @Override
    public FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        return new PrimitiveCollectionDeserializer();
    }
}
