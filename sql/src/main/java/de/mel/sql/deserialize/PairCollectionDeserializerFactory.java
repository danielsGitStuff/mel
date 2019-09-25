package de.mel.sql.deserialize;

import java.lang.reflect.Field;

import de.mel.core.serialize.deserialize.FieldDeserializer;
import de.mel.core.serialize.deserialize.FieldDeserializerFactory;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.reflection.FieldAnalyzer;
import de.mel.sql.Pair;

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
