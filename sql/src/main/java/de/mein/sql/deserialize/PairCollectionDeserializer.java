package de.mein.sql.deserialize;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.plaf.PanelUI;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.binary.BinaryDeserializer;
import de.mein.core.serialize.deserialize.collections.SerializableEntityCollectionDeserializer;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.deserialize.primitive.PrimitiveDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;
import de.mein.sql.Pair;

/**
 * Created by xor on 10/11/17.
 */

public class PairCollectionDeserializer implements FieldDeserializer {
    public PairCollectionDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {

    }

    @Override
    public Object deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Class typeClass, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException {
        if (jsonFieldValue != null) {
            Collection<Pair> pairs = (Collection<Pair>) field.get(entity);
            ParameterizedType genericType = (ParameterizedType) field.getGenericType();
            ParameterizedType pairType = (ParameterizedType) genericType.getActualTypeArguments()[0];
            Type pairGenericType = pairType.getActualTypeArguments()[0];
            Iterator<Pair> iterator = pairs.iterator();
            JSONArray jsonArray = (JSONArray) jsonFieldValue;
            int length = jsonArray.length();
            for (int i = 0; i < length; i++) {
                Pair pair = iterator.next();
                Object something = jsonArray.opt(i);
                Object value = PrimitiveDeserializer.JSON2Primtive((Class<?>) pairGenericType, something);
                if (pair == null) {
                    System.err.println(getClass().getSimpleName() + ".deserialize() on entity: " + entity.getClass().getSimpleName() + ", field: " + field.getName() + " ... seems like the Collection you wanted de deserialize was not big enough to fit everything in the JSON");
                    System.err.println(getClass().getSimpleName() + ".deserialize() on entity: " + entity.getClass().getSimpleName() + ", field: " + field.getName() + " ... currently there is no way of constructing Pairs on the fly cause Pair.key gets lost in the serialization.");
                } else {
                    if (jsonArray.isNull(i) || value == null || value instanceof JSONObject.Null)
                        pair.nul();
                    else
                        pair.v(value);
                }
            }
            field.set(entity, pairs);
            return pairs;
        }
        return null;
    }
}
