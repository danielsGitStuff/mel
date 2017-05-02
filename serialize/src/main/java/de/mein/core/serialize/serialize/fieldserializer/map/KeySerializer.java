package de.mein.core.serialize.serialize.fieldserializer.map;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;

/**
 * used to serializes Keys of a Map
 * Created by xor on 1/13/17.
 */
class KeySerializer extends FieldSerializer {

    private final SerializableEntitySerializer parentSerializer;
    private final Object value;

    public KeySerializer(SerializableEntitySerializer parentSerializer, Object value) {
        this.parentSerializer = parentSerializer;
        this.value = value;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public String JSON() throws JsonSerializationException {
        if (Number.class.isAssignableFrom(value.getClass())) {
            return value.toString();
        } else if (String.class.isAssignableFrom(value.getClass())) {
            String res = value.toString();
            res = res.replaceAll("\"", "\\\"");
            return res;
        } else if (SerializableEntity.class.isAssignableFrom(value.getClass())) {
            SerializableEntitySerializer serializer = parentSerializer.getPreparedSerializer((SerializableEntity) value);
            String json = serializer.JSON();
            return json;
        } else {
            System.err.println("KeySerializer.JSON.not.implemented.2");
        }
        return null;
    }

    public boolean isPrimitive() {
        return value == null || FieldAnalyzer.isPrimitiveClass(value.getClass());
    }
}
