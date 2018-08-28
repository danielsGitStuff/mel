package de.mein.core.serialize.deserialize.map;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.deserialize.primitive.PrimitiveDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;
import org.json.JSONObject;

import java.lang.reflect.Field;

/**
 * Created by xor on 1/14/17.
 */
public class KeyDeserializer {
    private final SerializableEntityDeserializer rootDeserializer;
    private boolean keyIsEntity = false;
    private Class<?> type;

    public KeyDeserializer(SerializableEntityDeserializer rootDeserializer, Class<?> type) {
        this.rootDeserializer = rootDeserializer;
        this.type = type;
        this.keyIsEntity = FieldAnalyzer.isEntitySerializableClass(type);
    }


    public Object deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException {
        if (jsonFieldValue != null) {
            if (keyIsEntity) {
                return rootDeserializer.deserialize((JSONObject) jsonFieldValue);
            } else {
                return new PrimitiveDeserializer().deserialize(null, null, null, type, jsonFieldValue);
            }
        }
        return null;
    }
}
