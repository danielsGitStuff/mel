package de.mein.core.serialize.deserialize.map;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;

import java.lang.reflect.Field;

/**
 * Created by xor on 1/14/17.
 */
public class KeyDeserializer {
    private final SerializableEntityDeserializer rootDeserializer;
    private final Field field;

    public KeyDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        this.rootDeserializer = rootDeserializer;
        this.field = field;
    }


    public Object deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException {
        System.out.println("KeyDeserializer.deserialize");
        return null;
    }
}
