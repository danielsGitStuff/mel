package de.mein.core.serialize.deserialize.primitive;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.tools.NumberTransformer;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/25/15.
 */
public class PrimitiveDeserializer implements FieldDeserializer {


    @Override
    public Object deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Class typeClass, Object value) throws IllegalAccessException {
        if (typeClass == null)
            typeClass = field.getType();
        value = JSON2Primtive(typeClass, value);
        if (field != null)
            field.set(entity, value);
        return value;
    }

    /**
     * @param clazz desired class
     * @param value
     * @return
     */
    public static Object JSON2Primtive(Class<?> clazz, Object value) {
        if (value != null) {
            Class<?> valueClass = value.getClass();
            if (clazz.equals(float.class))
                clazz = Float.class;
            else if (clazz.equals(boolean.class))
                clazz = Boolean.class;
            else if (clazz.equals(int.class))
                clazz = Integer.class;
            else if (clazz.equals(short.class))
                clazz = Short.class;
            if (!clazz.equals(valueClass) && Number.class.isAssignableFrom(clazz)) {
                Class<? extends Number> fieldNumberClass = (Class<? extends Number>) clazz;
                Number result = NumberTransformer.forType(fieldNumberClass).cast((Number) value);
                value = result;
            } else if (!clazz.equals(valueClass)
                    && (Boolean.class.isAssignableFrom(clazz)) || boolean.class.isAssignableFrom(clazz)) {
                Boolean result = Boolean.parseBoolean(value.toString());
                value = result;
            }
        }
        return value;
    }
}
