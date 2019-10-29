package de.mel.core.serialize.deserialize.collections;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.FieldDeserializer;
import de.mel.core.serialize.deserialize.FieldDeserializerFactory;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.reflection.FieldAnalyzer;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;


/**
 * Created by xor on 12/23/15.
 */
public class SerializableEntityCollectionDeserializerFactory implements FieldDeserializerFactory {

    private static SerializableEntityCollectionDeserializerFactory ins;

    private SerializableEntityCollectionDeserializerFactory() {

    }

    public static SerializableEntityCollectionDeserializerFactory getInstance() {
        if (ins == null)
            ins = new SerializableEntityCollectionDeserializerFactory();
        return ins;
    }

    @Override
    public boolean canDeserialize(Field field) {
        return FieldAnalyzer.isEntitySerializableCollection(field);
    }

    @Override
    public FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        return new SerializableEntityCollectionDeserializer(rootDeserializer, field);
    }
}
