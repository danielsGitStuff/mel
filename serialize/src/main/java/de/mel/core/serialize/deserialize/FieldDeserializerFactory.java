package de.mel.core.serialize.deserialize;


import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/23/15.
 */
public interface FieldDeserializerFactory {
    boolean canDeserialize(Field field);

    FieldDeserializer createDeserializer(SerializableEntityDeserializer rootDeserializer, Field field);
}
