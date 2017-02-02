package de.mein.core.serialize.deserialize.map;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializerFactory;
import de.mein.core.serialize.deserialize.primitive.PrimitiveDeserializer;
import de.mein.core.serialize.deserialize.primitive.PrimitiveDeserializerFactory;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 1/13/17.
 */
public class MapDeserializer implements FieldDeserializer {


    private final SerializableEntityDeserializer rootDeserializer;
    private final Field field;

    public MapDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        this.rootDeserializer = rootDeserializer;
        this.field = field;
    }

    /**
     * note: deserializing non-primitive things is not implemented yet
     *
     * @param serializableEntityDeserializer
     * @param entity
     * @param field
     * @param jsonFieldValue
     * @throws IllegalAccessException
     * @throws JsonDeserializationException
     */
    @Override
    public void deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException {
        Type type = field.getGenericType();
        ParameterizedType pType = (ParameterizedType) type;
        Class clazzK = (Class) pType.getActualTypeArguments()[0];
        Class clazzV = (Class) pType.getActualTypeArguments()[1];
        KeyDeserializerFactory kFactory = new KeyDeserializerFactory();
        FieldDeserializerFactory vFactory = createDeserializerFactory(clazzV);
        Map map = createMap(clazzK, clazzV);
        JSONObject jsonObject = (JSONObject) jsonFieldValue;
        JSONArray list = jsonObject.getJSONArray("__m");
        for (int i = 0; i < list.length(); i++) {
            JSONObject rowObj = (JSONObject) list.get(i);
            Object key = rowObj.keySet().iterator().next();
            Object value = rowObj.get(key.toString());

            key = JSON2val(clazzK, key);
            // kFactory.createDeserializer(rootDeserializer, field).deserialize(serializableEntityDeserializer, entity, field, key);
            value = JSON2val(clazzV, value);
            map.put(key, value);
        }
        field.set(entity, map);
    }

    private Object JSON2val(Class clazz, Object obj) {
        if (Number.class.isAssignableFrom(clazz) && !Number.class.isAssignableFrom(obj.getClass())) {
            obj = Double.parseDouble(obj.toString());
        }
        obj = PrimitiveDeserializer.JSON2Primtive(clazz, obj);
        return obj;
    }

    public static <K, V> Map<K, V> createMap(Class<K> classK, Class<V> classV) {
        Map<K, V> map = new HashMap<>();
        return map;
    }

    private FieldDeserializerFactory createDeserializerFactory(Class clazz) {
        if (FieldAnalyzer.isPrimitiveClass(clazz)) {
            return new PrimitiveDeserializerFactory();
        } else if (FieldAnalyzer.isEntitySerializableClass(clazz)) {
            return new SerializableEntityDeserializerFactory();
        } else {
            System.err.println("MapSerializer.createSerializerFactory: could not create Serializer for generic type: " + clazz.getName());
        }
        return null;
    }
}
