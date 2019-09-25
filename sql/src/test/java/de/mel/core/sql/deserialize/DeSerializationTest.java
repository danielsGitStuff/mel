package de.mel.core.sql.deserialize;


import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.core.sql.classes.PairSerializableEntity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 26.10.2015.
 */
public class DeSerializationTest {



    public static String serialize(SerializableEntity serializable) {
        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
        serializer.setEntity(serializable);
        try {
            return serializer.JSON();
        } catch (JsonSerializationException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static SerializableEntity deserialize(String s) throws IllegalAccessException, ClassNotFoundException, InstantiationException, JsonDeserializationException {
        SerializableEntityDeserializer deserializer = new SerializableEntityDeserializer();
        SerializableEntity serializable = deserializer.deserialize(s);
        return serializable;
    }



    @Test
    public void testPairSerializable() throws IllegalAccessException, InstantiationException, ClassNotFoundException, JsonDeserializationException {
        PairSerializableEntity pairSerializable = new PairSerializableEntity();
        pairSerializable.pair.v("pair.value");
        String json = serialize(pairSerializable);
        PairSerializableEntity deserialized = (PairSerializableEntity) deserialize(json);
        assertEquals(pairSerializable.pair.v(), deserialized.pair.v());
        assertEquals(pairSerializable.pair.k(), deserialized.pair.k());
    }
}
