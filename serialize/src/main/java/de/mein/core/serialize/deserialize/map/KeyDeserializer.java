package de.mein.core.serialize.deserialize.map;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import org.json.JSONObject;

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
        if (jsonFieldValue != null) {
            if (jsonFieldValue instanceof JSONObject) {
                return rootDeserializer.buildEntity((JSONObject) jsonFieldValue);
            } else {
                System.out.println("KeyDeserializer.deserialize.m8959t4e0g");
            }
        }
        return null;
    }
}
