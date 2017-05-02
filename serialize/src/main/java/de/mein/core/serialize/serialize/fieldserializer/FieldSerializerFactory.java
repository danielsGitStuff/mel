package de.mein.core.serialize.serialize.fieldserializer;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.lang.reflect.Field;

/**
 * Created by xor on 12/20/15.
 */
public interface FieldSerializerFactory {
      FieldSerializer createSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException, JsonSerializationException;
      boolean canSerialize(Field field);
      FieldSerializer createSerializerOnClass(SerializableEntitySerializer parentSerializer, Object value);
}
