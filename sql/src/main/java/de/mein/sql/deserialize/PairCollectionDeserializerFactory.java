package de.mein.sql.deserialize;

import java.lang.reflect.Field;

import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;
import de.mein.sql.Pair;

/**
 * Created by xor on 10/11/17.
 */

public class PairCollectionDeserializerFactory implements FieldDeserializerFactory {

    private static PairCollectionDeserializerFactory ins;

    public static PairCollectionDeserializerFactory getInstance() {
        if (ins == null)
            ins = new PairCollectionDeserializerFactory();
        return ins;
    }

    @Override
    public boolean canDeserialize(Field field) {
        return FieldAnalyzer.isGenericCollectionOfClass(field,Pair.class);
    }

    @Override
    public FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        return new PairCollectionDeserializer(rootDeserializer,field);
    }
}
