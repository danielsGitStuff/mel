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
 * Created by xor on 26.10.2015.
 */
public class DeSerializationTest {

    private WithPrimitiveCollection createWithPrimitiveCollection() {
        WithPrimitiveCollection withPrimitiveCollection = new WithPrimitiveCollection();
        withPrimitiveCollection.strings.add("primitive.test");
        withPrimitiveCollection.strings.add("primitive.test.2");
        return withPrimitiveCollection;
    }

    private WithSerializableEntityCollection createWithEntitySerializableCollection() {
        WithSerializableEntityCollection withEntitySerializableCollection = new WithSerializableEntityCollection();
        withEntitySerializableCollection.entityserializables.add(new WithPrimitiveCollection());
        withEntitySerializableCollection.entityserializables.add(new WithPrimitiveCollection());
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

    public static SerializableEntity deserialize(String s) throws IllegalAccessException, ClassNotFoundException, InstantiationException, JsonDeserializationException {
        SerializableEntityDeserializer deserializer = new SerializableEntityDeserializer();
        SerializableEntity serializable = deserializer.deserialize(s);
        return serializable;
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
        WithSerializableEntityCollection source = createWithEntitySerializableCollection();
        String json = serialize(source);
        WithSerializableEntityCollection deserialized = (WithSerializableEntityCollection) deserialize(json);
        assertEquals(source.entityserializables.get(0).getClass(), WithPrimitiveCollection.class);
        assertEquals(source.entityserializables.get(1).getClass(), WithPrimitiveCollection.class);
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
        ChildSerializableEntity deserialized = (ChildSerializableEntity) deserialize(json);
        assertEquals(root.getChildren().get(0).getPrimitive(), deserialized.getChildren().get(0).getPrimitive());
        assertEquals(root.getPrimitive(), deserialized.getPrimitive());
    }

    @Test
    public void listWithNull() throws ClassNotFoundException, InstantiationException, JsonDeserializationException, IllegalAccessException {
        WithSerializableEntityCollection original = new WithSerializableEntityCollection();
        original.entityserializables.add(null);
        original.entityserializables.add(null);
        original.entityserializables.add(new SimpleSerializableEntity());
        String json = serialize(original);
        WithSerializableEntityCollection deserial = (WithSerializableEntityCollection) deserialize(json);
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
        BinarySerializableEntity decoded = (BinarySerializableEntity) deserialize(json);
        for (int i = 0; i < binarySerializable.getBinary().length; i++) {
            assertEquals(binarySerializable.getBinary()[i], decoded.getBinary()[i]);
        }
    }
}
