package de.mel.core.serialize.deserialize;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.classes.*;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.*;

/**
 * This tests more complex Classes. That means {@link SerializableEntity}s containing more than one field.
 * Their order is not deterministic. To check whether (de)serialization was successful you must check both, the original and the deserialized, objects.
 * Created by xor on 26.10.2015.
 */
public class DeSerializationTest {

    private WithCollectionPrimitive createWithPrimitiveCollection() {
        WithCollectionPrimitive withCollectionPrimitive = new WithCollectionPrimitive();
        withCollectionPrimitive.strings.add("primitive.test");
        withCollectionPrimitive.strings.add("primitive.test.2");
        return withCollectionPrimitive;
    }

    private WithCollectionGeneric createWithEntitySerializableCollection() {
        WithCollectionGeneric withEntitySerializableCollection = new WithCollectionGeneric();
        withEntitySerializableCollection.entityserializables.add(new WithCollectionPrimitive());
        withEntitySerializableCollection.entityserializables.add(new WithCollectionPrimitive());
        return withEntitySerializableCollection;
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
    public void withCollectionGenericType() throws Exception {
        WithCollectionGenericType<SimplestEntity> withCollectionGenericType = new WithCollectionGenericType<>();
        SimplestEntity simplestEntity = new SimplestEntity();
        simplestEntity.name = "changed";
        withCollectionGenericType.list.add(simplestEntity);
        withCollectionGenericType.list.add(simplestEntity);
        String json = SerializableEntitySerializer.serialize(withCollectionGenericType);
        WithCollectionGenericType<SimplestEntity> des = (WithCollectionGenericType<SimplestEntity>) SerializableEntityDeserializer.deserialize(json);
        assertEquals(withCollectionGenericType.list.size(), des.list.size());
        assertNotNull(des.list.get(0));
        assertNotNull(des.list.get(1));
        SimplestEntity desSimplest = des.list.get(0);
        assertSame(desSimplest, des.list.get(1));
        assertEquals(simplestEntity.name, desSimplest.name);
    }

    @Test
    public void withMapGenericType() throws Exception {
        WithMapGenericType<SimplestEntity> withMapGenericType = new WithMapGenericType();
        SimplestEntity simplestEntity = new SimplestEntity();
        simplestEntity.name = "changed name";
        withMapGenericType.entities.put("test1", simplestEntity);
        withMapGenericType.entities.put("test2", simplestEntity);
        String json = SerializableEntitySerializer.serialize(withMapGenericType);
        WithMapGenericType<SimplestEntity> des = (WithMapGenericType<SimplestEntity>) SerializableEntityDeserializer.deserialize(json);
        SimplestEntity desSimplest = des.entities.get("test1");
        assertNotNull(des.entities.get("test1"));
        assertNotNull(des.entities.get("test2"));
        assertSame(desSimplest, des.entities.get("test2"));
        assertEquals(simplestEntity.name, des.entities.get("test1").name);
    }

// currently not implemented
//    @Test
//    public void withPrimitiveCollectionTest() throws IllegalAccessException, InstantiationException, ClassNotFoundException, JsonDeserializationException {
//        WithPrimitiveCollection source = createWithPrimitiveCollection();
//        String json = serialize(source);
//        WithPrimitiveCollection deserialized = (WithPrimitiveCollection) deserialize(json);
//        assertEquals(source.strings.get(0), deserialized.strings.get(0));
//        assertEquals(source.strings.get(1), deserialized.strings.get(1));
//        assertEquals(source.primitive, deserialized.primitive);
//    }
//
//    @Test
//    public void primitveListStuff() throws JsonSerializationException, IllegalAccessException, JsonDeserializationException {
//        //serialization tested in SerrTest
//        WithPrimitiveCollection primitiveList = new WithPrimitiveCollection();
//        primitiveList.strings.add("gu");
//        String json = SerializableEntitySerializer.serialize(primitiveList);
//        WithPrimitiveCollection des = (WithPrimitiveCollection) SerializableEntityDeserializer.deserialize(json);
//        System.out.println("DeSerializationTest.primitveListStuff." + des);
//        assertEquals(primitiveList.strings.get(0),des.strings.get(0));
//    }

    @Test
    public void withEntitySerializableCollectionTest() throws IllegalAccessException, InstantiationException, ClassNotFoundException, JsonDeserializationException {
        WithCollectionGeneric source = createWithEntitySerializableCollection();
        String json = serialize(source);
        WithCollectionGeneric deserialized = (WithCollectionGeneric) SerializableEntityDeserializer.deserialize(json);
        assertEquals(source.entityserializables.get(0).getClass(), WithCollectionPrimitive.class);
        assertEquals(source.entityserializables.get(1).getClass(), WithCollectionPrimitive.class);
    }

    @Test
    public void testDefaultSerializable() throws IllegalAccessException, InstantiationException, ClassNotFoundException, JsonDeserializationException {
        ChildSerializableEntity root = new ChildSerializableEntity();
        ChildSerializableEntity child = new ChildSerializableEntity();
        root.setPrimitive("root");
        root.addChild(child);
        child.setParent(root);
        child.setPrimitive("child");
        String json = serialize(root);
        ChildSerializableEntity deserialized = (ChildSerializableEntity) SerializableEntityDeserializer.deserialize(json);
        assertEquals(root.getChildren().get(0).getPrimitive(), deserialized.getChildren().get(0).getPrimitive());
        assertEquals(root.getPrimitive(), deserialized.getPrimitive());
    }

    @Test
    public void listWithNull() throws ClassNotFoundException, InstantiationException, JsonDeserializationException, IllegalAccessException {
        WithCollectionGeneric original = new WithCollectionGeneric();
        original.entityserializables.add(null);
        original.entityserializables.add(null);
        original.entityserializables.add(new SimpleSerializableEntity());
        String json = serialize(original);
        WithCollectionGeneric deserial = (WithCollectionGeneric) SerializableEntityDeserializer.deserialize(json);
        assertNull(original.entityserializables.get(0));
        assertNull(deserial.entityserializables.get(0));
        assertNull(original.entityserializables.get(1));
        assertNull(deserial.entityserializables.get(1));
        assertNotNull(original.entityserializables.get(2));
        assertNotNull(deserial.entityserializables.get(2));
    }

    @Test
    public void binary() throws ClassNotFoundException, InstantiationException, JsonDeserializationException, IllegalAccessException {
        BinarySerializableEntity binarySerializable = new BinarySerializableEntity();
        binarySerializable.setBinary(Base64.getEncoder().encode("binarybla".getBytes()));
        String json = serialize(binarySerializable);
        BinarySerializableEntity decoded = (BinarySerializableEntity) SerializableEntityDeserializer.deserialize(json);
        for (int i = 0; i < binarySerializable.getBinary().length; i++) {
            assertEquals(binarySerializable.getBinary()[i], decoded.getBinary()[i]);
        }
    }
}
