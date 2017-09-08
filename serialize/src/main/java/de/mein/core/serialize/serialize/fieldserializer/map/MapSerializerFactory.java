package de.mein.core.serialize.serialize.fieldserializer.map;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;

import java.lang.reflect.Field;

/**
 * Created by xor on 1/13/17.
 */
public class MapSerializerFactory implements FieldSerializerFactory {
    private static MapSerializerFactory ins;

    public static FieldSerializerFactory getInstance() {
        if (ins == null)
            ins = new MapSerializerFactory();
        return ins;
    }

    @Override
    public FieldSerializer createSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException, JsonSerializationException {
        System.out.println("MapSerializerFactory.createSerializerOnClass.field: " + field);
        field.setAccessible(true);
        return new MapSerializer(parentSerializer, field);
    }

    @Override
    public boolean canSerialize(Field field) {
        return FieldAnalyzer.isMap(field);
    }

    @Override
    public FieldSerializer createSerializerOnClass(SerializableEntitySerializer parentSerializer, Object value) {
        return null;
    }

}
