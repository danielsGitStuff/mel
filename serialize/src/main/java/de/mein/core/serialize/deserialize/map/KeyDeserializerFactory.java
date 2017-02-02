package de.mein.core.serialize.deserialize.map;

import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;

import java.lang.reflect.Field;

/**
 * Created by xor on 1/14/17.
 */
public class KeyDeserializerFactory {

    public KeyDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        return new KeyDeserializer(rootDeserializer,field);
    }
}
