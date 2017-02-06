package de.mein.core.serialize.deserialize.entity;

import de.mein.core.serialize.EntityAnalyzer;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.Serialize;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 12/23/15.
 */
public class SerializableEntityDeserializer implements FieldDeserializer {
    private Map<Integer, SerializableEntity> idRefEntityMap;
    private static SerializableEntityDeserializer ins;
    public static final String TYPE_REF = "__type";

    public SerializableEntityDeserializer() {
        this.idRefEntityMap = new HashMap<>();
    }

    public SerializableEntityDeserializer(Map<Integer, SerializableEntity> idRefEntityMap, JSONObject jsonMap) {
        this.idRefEntityMap = idRefEntityMap;
    }

    public SerializableEntityDeserializer(SerializableEntityDeserializer rootDeserializer, Field field) {
        this.idRefEntityMap = rootDeserializer.idRefEntityMap;
    }

    public SerializableEntity deserialize(JSONObject jsonObject) throws JsonDeserializationException {
        return buildEntity(jsonObject);
    }

    /**
     * @param json
     * @return
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     */
    public static SerializableEntity deserialize(String json) throws JsonDeserializationException {
        JSONObject jsonMap = new JSONObject(json);
        return new SerializableEntityDeserializer().deserialize(jsonMap);
    }

    private Object nullOrObject(Object obj) {
        if (obj == null || obj instanceof JSONObject.Null) {
            return null;
        }
        return obj;
    }

    private Object readFieldFromJson(JSONObject jsonMap, String fieldName) {
        Object fieldValue = jsonMap.opt(fieldName);
        return nullOrObject(fieldValue);
    }

    private void setField(Field field, SerializableEntity entity, Object value)
            throws IllegalArgumentException, IllegalAccessException {
        field.set(entity, value);
    }

    public SerializableEntity buildEntity(JSONObject jsonMap) throws JsonDeserializationException {
        try {
            if (jsonMap == null)
                return null;
            SerializableEntity entity = null;
            if (jsonMap.has(SerializableEntitySerializer.REF)) {
                int id = jsonMap.getInt(SerializableEntitySerializer.REF);
                return this.idRefEntityMap.get(id);
            }
            String typeString = (String) jsonMap.get(TYPE_REF);
            entity = EntityAnalyzer.instance(typeString);
            if (jsonMap.has(SerializableEntitySerializer.ID)) {
                int id = jsonMap.getInt(SerializableEntitySerializer.ID);
                this.idRefEntityMap.put(id, entity);
            }
            List<Field> fields = EntityAnalyzer.getFields(entity.getClass());
            for (Field field : fields) {
                if (FieldAnalyzer.isJsonIgnored(field) || FieldAnalyzer.isTransinient(field))
                    continue;
                String fieldName = field.getName();
                field.setAccessible(true);
                Object jsonFieldValue = readFieldFromJson(jsonMap, fieldName);
                Class fieldClass = field.getType();
                FieldDeserializer des = FieldSerializerFactoryRepository.buildFieldDeserializer(this, field);
                if (des == null) {
                    String message = "SerializableEntityDeserializer.buildEntity.did not find deserializer for type '" + field.getType().getSimpleName() + "'";
                    Type genericType = field.getGenericType();
                    try {
                        message += " , generic: " + genericType.getTypeName();
                    } catch (NoSuchMethodError e) {
                        // todo android der hurensohn
                        // no java.lang.reflect.getTypeName() available
                    }
                    Serialize.println(message);
                } else
                    des.deserialize(this, entity, field, jsonFieldValue);
            }
            return entity;
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonDeserializationException(e);
        }
    }


    @Override
    public void deserialize(SerializableEntityDeserializer serializableEntityDeserializer, SerializableEntity entity, Field field, Object jsonFieldValue) {
        if (jsonFieldValue instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) jsonFieldValue;
            try {
                SerializableEntity ent = buildEntity(jsonObject);
                field.setAccessible(true);
                field.set(entity, ent);
            } catch (JsonDeserializationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

        }
    }


}
