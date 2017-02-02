package de.mein.sql.deserialize;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.binary.BinaryDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.sql.Pair;

import java.lang.reflect.Field;

/**
 * Created by xor on 1/14/16.
 */
public class PairDeserializer implements FieldDeserializer {
    public PairDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {

    }

    @Override
    public void deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException {
        field.setAccessible(true);
        Pair<?> pair = (Pair<?>) field.get(entity);
        Object valueToSet = null;
        if (pair.getGenericClass().equals(byte[].class) && jsonFieldValue != null) {
            valueToSet = BinaryDeserializer.decode(jsonFieldValue.toString());
        } else {
            valueToSet = jsonFieldValue;
        }
        pair.setValueUnsecure(valueToSet);
    }
}
