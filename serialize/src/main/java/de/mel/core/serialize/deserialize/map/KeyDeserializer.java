package de.mel.core.serialize.deserialize.map;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.deserialize.primitive.PrimitiveDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.serialize.reflection.FieldAnalyzer;
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
