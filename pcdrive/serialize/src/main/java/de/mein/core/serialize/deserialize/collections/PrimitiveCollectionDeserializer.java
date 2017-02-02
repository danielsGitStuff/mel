package de.mein.core.serialize.deserialize.collections;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.deserialize.primitive.PrimitiveDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import org.json.JSONArray;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;

/**
 * Created by xor on 12/12/16.
 */
public class PrimitiveCollectionDeserializer implements FieldDeserializer {
    @Override
    public void deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException {
        System.out.println("PrimitiveCollectionDeserializer.deserialize");
        JSONArray jsonArray = (JSONArray) jsonFieldValue;
        if (jsonFieldValue != null) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Object whatever = parameterizedType.getActualTypeArguments()[0];

            Collection collection = SerializableEntityCollectionDeserializer.createCollection(field.getType());

            Class<?> genericType = (Class<?>) whatever;
            for (int i = 0; i < jsonArray.length(); i++) {
                Object value = PrimitiveDeserializer.JSON2Primtive(genericType, ((JSONArray) jsonFieldValue).get(i));
                collection.add(value);
            }
            field.set(entity, collection);
        }
    }
}
