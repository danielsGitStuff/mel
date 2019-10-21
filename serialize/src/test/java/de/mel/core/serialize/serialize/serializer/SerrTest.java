package de.mel.core.serialize.serialize.serializer;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.classes.*;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.InvalidPathException;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.core.serialize.serialize.reflection.classes.PrimitiveSet;
import de.mel.core.serialize.serialize.trace.TraceManager;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by xor on 12/20/15.
 */
public class SerrTest {

    public static class A implements SerializableEntity {
        private String primitive = "AAA";
        private SerializableEntity b = new B();
    }

    public static class B implements SerializableEntity {
        private String primitive = "BBB";
    }

    public static class S implements SerializableEntity {
        private Set<SimpleSerializableEntity> set = new HashSet<>();
    }

    public static class WithObjectMap implements SerializableEntity {
        private Map<String, URL> urls = new HashMap<>();
    }

//
//    @Test
//    public void primitiveList() throws JsonSerializationException, IllegalAccessException {
//        WithPrimitiveCollection s = new WithPrimitiveCollection();
//        s.strings.add("bla");
//        String json = SerializableEntitySerializer.serialize(s);
//        System.out.println(json);
//        assertEquals("{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.WithPrimitiveCollection\",\"strings\":[\"bla\"],\"primitive\":\"primitive\"}", json);
//    }

    @Test
    public void primitiveCollection() throws Exception {
        PrimitiveSet hashesAvailable = new PrimitiveSet().addInt(22).addString("aa").addString("bb").addInt(33).addInt(44);
        String json = SerializableEntitySerializer.serialize(hashesAvailable);
        System.out.println(json);
        PrimitiveSet copy = (PrimitiveSet) SerializableEntityDeserializer.deserialize(json);
        assertEquals("{\"$id\":1,\"__type\":\"de.mel.core.serialize.serialize.reflection.classes.PrimitiveSet\",\"strings\":[\"aa\",\"bb\"],\"ints\":[33,22,44]}", json);
    }


    @Test
    public void set() throws JsonSerializationException, IllegalAccessException {
        S s = new S();
        s.set.add(new SimpleSerializableEntity().setPrimitive("bla"));
        String json = SerializableEntitySerializer.serialize(s);
        System.out.println(json);
        assertEquals("{\"$id\":1,\"__type\":\"de.mel.core.serialize.serialize.serializer.SerrTest$S\",\"set\":[{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.SimpleSerializableEntity\",\"primitive\":\"bla\"}]}", json);
    }

    @Test
    public void testTraversalDepthEntity() throws JsonSerializationException {
        ChildSerializableEntity parent = new ChildSerializableEntity();
        ChildSerializableEntity child = new ChildSerializableEntity();
        parent.setPrimitive("bla");
        parent.addChild(child);
        child.setParent(parent);
        child.setPrimitive("bla");
        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
        serializer.setEntity(parent);
        serializer.setTraversalDepth(0);
        String json = serializer.JSON();
        assertEquals("{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"primitive\":\"bla\"}", json);
        serializer = new SerializableEntitySerializer();
        serializer.setEntity(parent);
        serializer.setTraversalDepth(1);
        json = serializer.JSON();
        System.out.println(json);
        assertEquals("{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"primitive\":\"bla\",\"children\":[{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"primitive\":\"bla\"}]}", json);
        System.out.println("SerializationTest.testTraversalDepth");
    }

    private void extendLeDirectory(ChildSerializableEntity dir, int depth) {
        if (depth >= 0) {
            ChildSerializableEntity sub = new ChildSerializableEntity();
            sub.setPrimitive("sub." + depth);
            sub.setParent(dir);
            dir.addChild(sub);
            extendLeDirectory(sub, --depth);
        }
    }

    @Test
    public void testTrace() throws JsonSerializationException, InvalidPathException {
        ChildSerializableEntity root = new ChildSerializableEntity();
        extendLeDirectory(root, 5);
        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
        serializer.setEntity(root);
        serializer.setTraversalDepth(0);
        TraceManager traceManager = new TraceManager().addForcedPath("[de.mel.core.serialize.classes.ChildSerializableEntity].children");
        serializer.setTraceManager(traceManager);
        String json = serializer.JSON();
        String expected = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"children\":[{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"parent\":{\"$ref\":1},\"primitive\":\"sub.5\",\"children\":[{\"$id\":3,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"parent\":{\"$ref\":2},\"primitive\":\"sub.4\",\"children\":[{\"$id\":4,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"parent\":{\"$ref\":3},\"primitive\":\"sub.3\",\"children\":[{\"$id\":5,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"parent\":{\"$ref\":4},\"primitive\":\"sub.2\",\"children\":[{\"$id\":6,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"parent\":{\"$ref\":5},\"primitive\":\"sub.1\",\"children\":[{\"$id\":7,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"parent\":{\"$ref\":6},\"primitive\":\"sub.0\"}]}]}]}]}]}]}";
        assertEquals(expected, json);
        System.out.println("SerializationTest.testTraversalDepth");
    }

    @Test
    public void testWithEntitySerializableCollection() {
        WithSerializableEntityCollection withEntitySerializableCollection = new WithSerializableEntityCollection();
        ChildSerializableEntity pairSerializable = new ChildSerializableEntity();
        pairSerializable.setPrimitive("some test");
        withEntitySerializableCollection.entityserializables.add(pairSerializable);
        withEntitySerializableCollection.entityserializables.add(new WithPrimitiveCollection());
        String json = serialize(withEntitySerializableCollection);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.WithSerializableEntityCollection\",\"primitive\":\"primitive\",\"entityserializables\":[{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"primitive\":\"some test\"},{\"$id\":3,\"__type\":\"de.mel.core.serialize.classes.WithPrimitiveCollection\",\"strings\":[],\"primitive\":\"primitive\"}]}";
        System.out.println("should");
        System.out.println(result);
        System.out.println("is");
        System.out.println(json);
        assertEquals(result, json);
    }

    @Test
    public void testWithEmptyEntitySerializableCollection() {
        WithSerializableEntityCollection withEntitySerializableCollection = new WithSerializableEntityCollection();
        String json = serialize(withEntitySerializableCollection);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.WithSerializableEntityCollection\",\"primitive\":\"primitive\"}";
        assertEquals(result, json);
    }

    @Test
    public void testWithObjectMap() throws MalformedURLException {
        WithObjectMap entity = new WithObjectMap();
        entity.urls.put("1", new URL("http://bla.de"));
        String json = serialize(entity);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.serialize.serializer.SerrTest$WithObjectMap\"}";
        assertEquals(result, json);
    }
// currently not implemented
//    @Test
//    public void testWithPrimitveCollection() {
//        WithPrimitiveCollection withPrimitiveCollection = new WithPrimitiveCollection();
//        withPrimitiveCollection.strings.add("primitive.test");
//        withPrimitiveCollection.strings.add("primitive.test.2");
//        String json = serialize(withPrimitiveCollection);
//        System.out.println(json);
//        String result = "{\"$id\":1,\"__type\":\"WithPrimitiveCollection\",\"strings\":[\"primitive.test\",\"primitive.test.2\"],\"primitive\":\"primitive\"}";
//        assertTrue(result.equals(json));
//    }

//    @Test
//    public void testWithEmptyPrimitveCollection() {
//        WithPrimitiveCollection withPrimitiveCollection = new WithPrimitiveCollection();
//        String json = serialize(withPrimitiveCollection);
//        System.out.println(json);
//        String result = "{\"$id\":1,\"__type\":\"WithPrimitiveCollection\",\"primitive\":\"primitive\"}";
//        assertTrue(result.equals(json));
//    }

    @Test
    public void primitiveNull() {
        ChildSerializableEntity root = new ChildSerializableEntity();
        String json = serialize(root);
        System.out.println("is");
        System.out.println(json);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\"}";
        System.out.println("should");
        System.out.println(result);
        assertTrue(result.equals(json));
    }

    @Test
    public void listWithNull() {
        WithSerializableEntityCollection listi = new WithSerializableEntityCollection();
        listi.entityserializables.add(null);
        listi.entityserializables.add(null);
        listi.entityserializables.add(new SimpleSerializableEntity());
        String json = serialize(listi);
        assertEquals("{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.WithSerializableEntityCollection\",\"primitive\":\"primitive\",\"entityserializables\":[null,null,{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.SimpleSerializableEntity\"}]}", json);
    }


    @Test
    public void primitive() {
        ChildSerializableEntity root = new ChildSerializableEntity();
        root.setPrimitive("testi");
        String json = serialize(root);
        System.out.println("is");
        System.out.println(json);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"primitive\":\"testi\"}";
        System.out.println("should");
        System.out.println(result);
        assertEquals(result, json);
    }

    @Test
    public void number() throws JsonSerializationException, JsonDeserializationException {
        SimpleSerializableEntity simpleSerializableEntity = new SimpleSerializableEntity().setNumber(567);
        String json = SerializableEntitySerializer.serialize(simpleSerializableEntity);
        SimpleSerializableEntity des = (SimpleSerializableEntity) SerializableEntityDeserializer.deserialize(json);
        assertEquals(simpleSerializableEntity.getNumber(), des.getNumber());

    }

    @Test(expected = JsonDeserializationException.class)
    public void numberFail() throws JsonSerializationException, JsonDeserializationException {
        SimpleSerializableEntity simpleSerializableEntity = new SimpleSerializableEntity().setNumber(567);
        String json = SerializableEntitySerializer.serialize(simpleSerializableEntity);
        json = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.SimpleSerializableEntity\",\"number\":\"567\"}";
        SimpleSerializableEntity des = (SimpleSerializableEntity) SerializableEntityDeserializer.deserialize(json);
        assertEquals(simpleSerializableEntity.getNumber(), des.getNumber());
    }

    @Test
    public void ring() {
        SimpleSerializableEntity root = new SimpleSerializableEntity();
        SimpleSerializableEntity child = new SimpleSerializableEntity();
        child.setPrimitive("child");
        root.setPrimitive("root");
        root.out = child;
        child.in = root;
        child.out = root;
        root.in = child;
        String json = serialize(child);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.SimpleSerializableEntity\",\"primitive\":\"child\",\"in\":{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.SimpleSerializableEntity\",\"primitive\":\"root\",\"in\":{\"$ref\":1},\"out\":{\"$ref\":1}},\"out\":{\"$ref\":2}}";
        assertEquals(result, json);
    }

    @Test
    public void rootContainsChildContainsRoot() {
        SimpleSerializableEntity root = new SimpleSerializableEntity();
        SimpleSerializableEntity child = new SimpleSerializableEntity();
        child.setPrimitive("child");
        root.setPrimitive("root");
        root.out = child;
        child.in = root;
        child.out = root;
        String json = serialize(root);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.SimpleSerializableEntity\",\"primitive\":\"root\",\"out\":{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.SimpleSerializableEntity\",\"primitive\":\"child\",\"in\":{\"$ref\":1},\"out\":{\"$ref\":1}}}";
        assertEquals(result, json);
    }

    @Test
    public void childContainsRootInList() {
        // this is kinda fucked up
        ChildSerializableEntity root = new ChildSerializableEntity();
        ChildSerializableEntity child = new ChildSerializableEntity();
        child.setPrimitive("child");
        root.setPrimitive("root");
        root.addChild(child);
        child.setParent(root);
        String json = serialize(child);
        String result = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"parent\":{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.ChildSerializableEntity\",\"primitive\":\"root\",\"children\":[{\"$ref\":1}]},\"primitive\":\"child\"}";
        assertEquals(result, json);
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

    public static class SimpleMapTest implements SerializableEntity {
        Map<Integer, String> map = new HashMap<>();
        Map<String, Integer> inverse = new HashMap<>();
    }

    public static class EntityMapTest implements SerializableEntity {
        Map<Integer, SerializableEntity> map = new HashMap<>();
        Map<SerializableEntity, Integer> inverse = new HashMap<>();
    }

    @Test
    public void mapEntityAsKey() throws JsonSerializationException, IllegalAccessException {
        class MapTest implements SerializableEntity {
            private Map<SerializableEntity, Long> map = new HashMap<>();
        }
        MapTest mapTest = new MapTest();
        SimpleSerializableEntity key = new SimpleSerializableEntity();
        mapTest.map.put(key, 666L);
        String json = SerializableEntitySerializer.serialize(mapTest);
        System.out.println(json);
        String expected = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.serialize.serializer.SerrTest$1MapTest\",\"map\":{\"__type\":\"java.util.HashMap\",\"__k\":\"de.mel.core.serialize.SerializableEntity\",\"__v\":\"java.lang.Long\",\"__x\":{\"0\":{\"$id\":2,\"__type\":\"de.mel.core.serialize.classes.SimpleSerializableEntity\"}},\"__m\":{\"0\":666}}}";
        assertEquals(expected, json);
    }

    public static class MapTestE implements SerializableEntity {
        Map<SerializableEntity, SerializableEntity> map = new HashMap<>();

        public MapTestE() {
        }
    }

    @Test
    public void mapEntityOnItself() throws JsonSerializationException, IllegalAccessException, JsonDeserializationException {
        MapTestE mapTest = new MapTestE();
        B key = new B();
        mapTest.map.put(key, key);
        String json = SerializableEntitySerializer.serialize(mapTest);
        System.out.println(json);
        String expected = "{\"$id\":1,\"__type\":\"de.mel.core.serialize.serialize.serializer.SerrTest$MapTestE\",\"map\":{\"__type\":\"java.util.HashMap\",\"__k\":\"de.mel.core.serialize.SerializableEntity\",\"__v\":\"de.mel.core.serialize.SerializableEntity\",\"__x\":{\"0\":{\"$id\":2,\"__type\":\"de.mel.core.serialize.serialize.serializer.SerrTest$B\",\"primitive\":\"BBB\"}},\"__m\":{\"0\":{\"$ref\":2}}}}";
        assertEquals(expected, json);
        Object o = SerializableEntityDeserializer.deserialize(json);
        System.out.println(o);
    }

    @Test
    public void mapInverse() throws JsonSerializationException, IllegalAccessException, JsonDeserializationException {
        EntityMapTest mapTest = new EntityMapTest();
        B key = new B();
        mapTest.inverse.put(key, 3);
        mapTest.map = null;
        String json = SerializableEntitySerializer.serialize(mapTest);
        System.out.println(json);
        Object o = SerializableEntityDeserializer.deserialize(json);
        System.out.println(o);
    }

    @Test
    public void mapTestEntity() throws JsonSerializationException, IllegalAccessException, JsonDeserializationException {
        EntityMapTest mapTest = new EntityMapTest();
        B key = new B();
        B value = new B();
        mapTest.map.put(1, value);
        mapTest.map.put(2, value);
        mapTest.inverse.put(key, 3);
        mapTest.inverse.put(key, 4);
        String json = SerializableEntitySerializer.serialize(mapTest);
        System.out.println(json);
        Object o = SerializableEntityDeserializer.deserialize(json);
        System.out.println(o);
    }

    @Test
    public void mapTestPrimitive() throws JsonSerializationException, IllegalAccessException, JsonDeserializationException {
        SimpleMapTest mapTest = new SimpleMapTest();
        mapTest.map.put(1, "one");
        mapTest.map.put(2, "two");
        mapTest.inverse.put("three", 3);
        mapTest.inverse.put("four", 4);
        String json = SerializableEntitySerializer.serialize(mapTest);
        System.out.println(json);
        SimpleMapTest des = (SimpleMapTest) SerializableEntityDeserializer.deserialize(json);
        System.out.println("SerrTest.mapTestPrimitive");
        assertEquals("one", des.map.get(1));
        assertEquals("two", des.map.get(2));
        assertEquals((Integer) 3, (Integer) des.inverse.get("three"));
        assertEquals((Integer) 4, (Integer) des.inverse.get("four"));
    }

    @Test
    public void binary() throws JsonDeserializationException {
        final String source = "bla";
        BinarySerializableEntity binarySerializable = new BinarySerializableEntity();
        binarySerializable.setBinary(source.getBytes());
        String json = serialize(binarySerializable);
        assertEquals("{\"$id\":1,\"__type\":\"de.mel.core.serialize.classes.BinarySerializableEntity\",\"binary\":\"Ymxh\"}", json);
        BinarySerializableEntity deserialized = (BinarySerializableEntity) SerializableEntityDeserializer.deserialize(json);
        assertEquals(Arrays.toString(binarySerializable.getBinary()), Arrays.toString(deserialized.getBinary()));
        assertEquals(source, new String(deserialized.getBinary()));
    }

}
