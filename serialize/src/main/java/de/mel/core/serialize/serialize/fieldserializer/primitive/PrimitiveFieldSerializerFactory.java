package de.mel.core.serialize.serialize.fieldserializer.primitive;

import java.lang.reflect.Field;
import java.util.Set;

import de.mel.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.NullSerializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.core.serialize.serialize.reflection.FieldAnalyzer;

/**
 * Created by xor on 12/20/15.
 */
public class PrimitiveFieldSerializerFactory implements FieldSerializerFactory {
    private static PrimitiveFieldSerializerFactory ins;
    private final Set<Class<?>> primitiveClasses = FieldAnalyzer.getPrimitiveClasses();

    public PrimitiveFieldSerializerFactory() {
    }

    public static PrimitiveFieldSerializerFactory getInstance() {
        if (ins == null)
            ins = new PrimitiveFieldSerializerFactory();
        return ins;
    }

    @Override
    public FieldSerializer createSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Object whatever = field.get(parentSerializer.getEntity());
        if (whatever == null)
            return new NullSerializer();
        return new PrimitiveFieldSerializer(whatever);
    }

    @Override
    public boolean canSerialize(Field field) {
        return primitiveClasses.contains(field.getType());
    }

    @Override
    public FieldSerializer createSerializerOnClass(SerializableEntitySerializer parentSerializer, Object value) {
        return new PrimitiveFieldSerializer(value);
    }

}
