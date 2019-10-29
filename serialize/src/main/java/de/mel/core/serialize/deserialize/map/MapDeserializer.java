package de.mel.core.serialize.deserialize.map;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.FieldDeserializer;
import de.mel.core.serialize.deserialize.FieldDeserializerFactory;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializerFactory;
import de.mel.core.serialize.deserialize.primitive.PrimitiveDeserializer;
import de.mel.core.serialize.deserialize.primitive.PrimitiveDeserializerFactory;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.serialize.reflection.FieldAnalyzer;
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
     * @param typeClass
     * @param jsonFieldValue                 @throws IllegalAccessException
     * @throws JsonDeserializationException
     */
    @Override
    public Object deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Class typeClass, Object jsonFieldValue) throws IllegalAccessException, JsonDeserializationException {
        Type type = field.getGenericType();
        ParameterizedType pType = (ParameterizedType) type;
        Class clazzK = FieldAnalyzer.getBoundedClass(pType.getActualTypeArguments()[0]);
        Class clazzV = FieldAnalyzer.getBoundedClass(pType.getActualTypeArguments()[1]);
        KeyDeserializerFactory kFactory = new KeyDeserializerFactory(clazzK);
        FieldDeserializerFactory vFactory = createDeserializerFactory(clazzV);
        Map map = null;

        // deserialize keyIdMap first!
        if (jsonFieldValue != null) {
            map = createMap(clazzK, clazzV);
            JSONObject jsonObject = (JSONObject) jsonFieldValue;
            JSONObject keyIdKeyJSONMap = jsonObject.getJSONObject("__x");
            Map<Integer, Object> keyIdKeyMap = new HashMap<>();
            for (Integer i = 0; i < keyIdKeyJSONMap.length(); i++) {
                Object key = keyIdKeyJSONMap.get(i.toString());
                KeyDeserializer des = kFactory.createDeserializer(rootDeserializer);
                Object desKey = des.deserialize(rootDeserializer, null, null, key);
                keyIdKeyMap.put(i, desKey);
            }
            // now lets do the "real" deserialization
            JSONObject jsonMap = jsonObject.getJSONObject("__m");
            for (Integer keyId = 0; keyId < jsonMap.length(); keyId++) {
                Object valObj = jsonMap.get(keyId.toString());
                Object key = keyIdKeyMap.get(keyId);
                Object val = null;
                FieldDeserializer valueDeserializer = vFactory.createDeserializer(rootDeserializer, null);
                val = valueDeserializer.deserialize(rootDeserializer, null, null, clazzV, valObj);
                map.put(key, val);
            }
        }
        field.set(entity, map);
        return map;
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
