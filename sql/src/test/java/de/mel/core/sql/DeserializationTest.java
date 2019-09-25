package de.mel.core.sql;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.Pair;
import de.mel.sql.deserialize.PairDeserializerFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Vector;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 1/14/16.
 */
public class DeserializationTest {
    public static class A implements SerializableEntity {
        Pair<String> pairString = new Pair<String>(String.class, "k1", "value1");
    }

    @BeforeClass
    public static void init() {
        PairDeserializerFactory.addToRepo();
    }

    @Test
    public void deserialize() throws JsonSerializationException, JsonDeserializationException {
        A a = new A();
        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
        serializer.setEntity(a);
        String json = serializer.JSON();
        SerializableEntityDeserializer deserializer = new SerializableEntityDeserializer();
        A b = (A) deserializer.deserialize(json);
        assertEquals(a.pairString.k(),b.pairString.k());
        assertEquals(a.pairString.v(),b.pairString.v());
        Vector<String> v = new Vector<>();
    }
}
