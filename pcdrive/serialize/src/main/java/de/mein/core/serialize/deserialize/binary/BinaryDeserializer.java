package de.mein.core.serialize.deserialize.binary;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;

import java.lang.reflect.Field;
import java.util.Base64;

/**
 * Created by xor on 2/26/16.
 */
public class BinaryDeserializer implements FieldDeserializer {

    public static byte[] decode(String base64){
        return Base64.getDecoder().decode(base64.toString());
    }
    @Override
    public void deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Object value) throws IllegalAccessException, JsonDeserializationException {
        //value is expected to be Base64 encoded
        Class<?> fieldClass = field.getType();
        if (value != null) {
            byte[] decoded = decode(value.toString());
            field.set(entity, decoded);
        }
    }
}
