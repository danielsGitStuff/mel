package de.mein.core.serialize.deserialize.collections;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;


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
        if (!Iterable.class.isAssignableFrom(field.getType()))
            return false;
        ParameterizedType genericListType = (ParameterizedType) field.getGenericType();
        Class<?> genericListClass = (Class<?>) genericListType.getActualTypeArguments()[0];
        return SerializableEntity.class.isAssignableFrom(genericListClass);
    }

    @Override
    public FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        return new SerializableEntityCollectionDeserializer(rootDeserializer, field);
    }
}
