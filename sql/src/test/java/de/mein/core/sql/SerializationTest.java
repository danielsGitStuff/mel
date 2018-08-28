package de.mein.core.sql;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.sql.classes.PairSerializableEntity;
import de.mein.core.sql.classes.SqlTableTester;
import de.mein.sql.serialize.PairSerializerFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SerializationTest {

    @BeforeClass
    public static void prepare() {
        // register pair serializerfactory
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
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
        String result = "{\"$id\":1,\"__type\":\"de.mein.core.sql.classes.PairSerializableEntity\",\"pair\":\"pair.value\"}";
        assertTrue(result.equals(json));
    }

    @Test
    public void testPairSerializableNullValue() {
        PairSerializableEntity pairSerializable = new PairSerializableEntity();
        String json = serialize(pairSerializable);
        System.out.println(json);
        String result = "{\"$id\":1,\"__type\":\"de.mein.core.sql.classes.PairSerializableEntity\"}";
        assertTrue(result.equals(json));
    }

    @Test
    public void testSQLTableObject() {
        SqlTableTester parent = new SqlTableTester();
        SqlTableTester child = new SqlTableTester();
        parent.addChild(child);
        child.setParent(parent);
        parent.getPair().v("parent");
        child.getPair().v("child");
        String json = serialize(child);
        String excpected = "{\"$id\":1,\"__type\":\"de.mein.core.sql.classes.SqlTableTester\",\"parent\":{\"$id\":2,\"__type\":\"de.mein.core.sql.classes.SqlTableTester\",\"children\":[{\"$ref\":1}],\"pair\":\"parent\",\"obj\":\"bla\"},\"pair\":\"child\",\"obj\":\"bla\"}";
        System.out.println("should");
        System.out.println(excpected);
        System.out.println("is");
        System.out.println(json);
        assertEquals(excpected, json);
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
        extendLeDirectory(root, 7);
        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
        serializer.setEntity(root);
        serializer.setTraversalDepth(1);
        String json = serializer.JSON();
        String expected = "{\"$id\":1,\"__type\":\"de.mein.core.sql.classes.SqlTableTester\",\"children\":[{\"$id\":2,\"__type\":\"de.mein.core.sql.classes.SqlTableTester\",\"pair\":\"sub.7\",\"obj\":\"bla\"}],\"pair\":\"root\",\"obj\":\"bla\"}";
        assertEquals(expected, json);
        System.out.println("SerializationTest.testTraversalDepth");
    }


}
