package de.mel.core.serialize.deserialize;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/23/15.
 */
public interface FieldDeserializer {
    Object deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Class typeClass, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException;
}
