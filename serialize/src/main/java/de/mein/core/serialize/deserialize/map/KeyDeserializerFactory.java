package de.mein.core.serialize.deserialize.map;

import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;

/**
 * Created by xor on 1/14/17.
 */
public class KeyDeserializerFactory {

    private final Class type;

    public KeyDeserializerFactory(Class type) {
        this.type = type;
    }

    public KeyDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer) {
        return new KeyDeserializer(rootDeserializer, type);
    }
}
