package de.mein.core.serialize.deserialize;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/23/15.
 */
public interface FieldDeserializer {
    Object deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Class typeClass, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException;
}
