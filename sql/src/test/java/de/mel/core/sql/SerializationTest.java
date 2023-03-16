package de.mel.core.sql;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.core.sql.classes.PairSerializableEntity;
import de.mel.core.sql.classes.SqlTableTester;
import de.mel.sql.Pair;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SerializationTest {

    @BeforeClass
    public static void prepare() {
        // register pair serializerfactory
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
    }


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


    @Test
    public void testPairSerializable() {
        PairSerializableEntity pairSerializable = new PairSerializableEntity();
        pairSerializable.pair.v("pair.value");
        String json = serialize(pairSerializable);
        System.out.println(json);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.sql.classes.PairSerializableEntity\",\"pair\":\"pair.value\"}";
        assertTrue(result.equals(json));
    }

    @Test
    public void testPairSerializableNullValue() {
        PairSerializableEntity pairSerializable = new PairSerializableEntity();
        String json = serialize(pairSerializable);
        System.out.println(json);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.sql.classes.PairSerializableEntity\"}";
        assertTrue(result.equals(json));
    }

    @Test
    public void testSQLTableObject() throws JsonDeserializationException {
        SqlTableTester parent = new SqlTableTester();
        SqlTableTester child = new SqlTableTester();
        parent.addChild(child);
        child.setParent(parent);
        parent.getPair().v("parent");
        child.getPair().v("child");
        String json = serialize(child);
        SqlTableTester desChild = (SqlTableTester) SerializableEntityDeserializer.deserialize(json);
        assertPair(child.getPair(), desChild.getPair());
        assertPair(parent.getPair(), desChild.getParent().getPair());
    }

    private void extendLeDirectory(SqlTableTester dir, int depth) {
        if (depth >= 0) {
            SqlTableTester sub = new SqlTableTester();
            sub.getPair().v("sub." + depth);
            sub.setParent(dir);
            dir.addChild(sub);
            extendLeDirectory(sub, --depth);
        }
    }

    @Test
    public void testTraversalDepthCollection() throws JsonSerializationException, JsonDeserializationException {
        SqlTableTester root = new SqlTableTester();
        root.getPair().v("root");
        extendLeDirectory(root, 3);
        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
        serializer.setEntity(root);
        serializer.setTraversalDepth(1);
        String json = serializer.JSON();
        SqlTableTester des = (SqlTableTester) SerializableEntityDeserializer.deserialize(json);
        assertEquals(root.getPair().k(), des.getPair().k());
        assertEquals(root.getPair().v(), des.getPair().v());
        assertPair(root.getChildren().iterator().next().getPair(), des.getChildren().iterator().next().getPair());
        System.out.println("SerializationTest.testTraversalDepth");
    }

    private void assertPair(Pair p1, Pair p2) {
        assertEquals(p1.k(), p2.k());
        assertEquals(p1.v(), p2.v());
    }


}
