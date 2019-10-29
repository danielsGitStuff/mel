package de.mel.core.serialize.serialize.fieldserializer.map;

import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.core.serialize.serialize.reflection.FieldAnalyzer;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

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
//        System.out.println("MapSerializerFactory.createSerializerOnClass.field: " + field);
        field.setAccessible(true);
        return new MapSerializer(parentSerializer, field);
    }

    @Override
    public boolean canSerialize(Field field) {
        if (!FieldAnalyzer.isMap(field))
            return false;
        Type type = field.getGenericType();
        ParameterizedType pType = (ParameterizedType) type;
        Class boundedK = FieldAnalyzer.getBoundedClass(pType.getActualTypeArguments()[0]);
        Class boundedV = FieldAnalyzer.getBoundedClass(pType.getActualTypeArguments()[1]);

        if (!(FieldAnalyzer.isEntitySerializableClass(boundedK) || FieldAnalyzer.isPrimitiveClass(boundedK)))
            return false;
        return FieldAnalyzer.isEntitySerializableClass(boundedV) || FieldAnalyzer.isPrimitiveClass(boundedV);
    }



    @Override
    public FieldSerializer createSerializerOnClass(SerializableEntitySerializer parentSerializer, Object value) {
        return null;
    }

}
