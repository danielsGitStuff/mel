package de.mein.core.serialize.serialize.fieldserializer;

import de.mein.core.serialize.Serialize;
import de.mein.core.serialize.deserialize.FieldDeserializer;
import de.mein.core.serialize.deserialize.FieldDeserializerFactory;
import de.mein.core.serialize.deserialize.binary.BinaryDeserializerFactory;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.deserialize.collections.SerializableEntityCollectionDeserializerFactory;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializerFactory;
import de.mein.core.serialize.deserialize.map.MapDeserializerFactory;
import de.mein.core.serialize.deserialize.primitive.PrimitiveDeserializerFactory;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.binary.BinarySerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.collections.SerializableEntityCollectionSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.map.MapSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.primitive.PrimitiveFieldSerializerFactory;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 12/20/15.
 */
public class FieldSerializerFactoryRepository {
    private static Map<Field, FieldSerializerFactory> fieldSerializerFactoryMap = new HashMap<>();
    private static Map<Field, FieldDeserializerFactory> classIDeserializerFactoryMap = new HashMap<>();
    /**
     * this is not bullet proof. you can add as many instances of the same factory as you want
     */
    private static Map<String, FieldSerializerFactory> availableSerializationFactories = new HashMap<>();
    private static Map<String, FieldDeserializerFactory> availableDeserializationFactories = new HashMap<>();


    public static void addAvailableSerializerFactory(FieldSerializerFactory factory) {
        if (!availableSerializationFactories.containsKey(factory.getClass().getName()))
            availableSerializationFactories.put(factory.getClass().getName(), factory);
    }

    public static void bindClassAndSerializerFactory(Field field, FieldSerializerFactory factory) {
        fieldSerializerFactoryMap.put(field, factory);
    }

    public static void addAvailableDeserializerFactory(FieldDeserializerFactory factory) {
        if (!availableDeserializationFactories.containsKey(factory.getClass().getName()))
            availableDeserializationFactories.put(factory.getClass().getName(), factory);
    }

    public static void bindClassAndDeserializerFactory(Field field, FieldDeserializerFactory factory) {
        classIDeserializerFactoryMap.put(field, factory);
    }


    static {
        // init primitives, collections and SerializableEntity
        addAvailableSerializerFactory(PrimitiveFieldSerializerFactory.getInstance());
        addAvailableSerializerFactory(BinarySerializerFactory.getInstance());
        addAvailableSerializerFactory(SerializableEntitySerializerFactory.getInstance());
        addAvailableSerializerFactory(SerializableEntityCollectionSerializerFactory.getInstance());
        addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        addAvailableSerializerFactory(MapSerializerFactory.getInstance());
        addAvailableDeserializerFactory(SerializableEntityDeserializerFactory.getIntance());
        addAvailableDeserializerFactory(BinaryDeserializerFactory.getInstance());
        addAvailableDeserializerFactory(PrimitiveDeserializerFactory.getInstance());
        addAvailableDeserializerFactory(SerializableEntityCollectionDeserializerFactory.getInstance());
        addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        addAvailableDeserializerFactory(MapDeserializerFactory.getInstance());
    }

    public static void printSerializers() {
        Serialize.println("FieldSerializerFactoryRepository.printSerializers...");
        for (FieldSerializerFactory f : availableSerializationFactories.values()) {
            Serialize.println(f.getClass());
        }
        Serialize.println("Deserializers...");
        for (FieldDeserializerFactory f : availableDeserializationFactories.values()) {
            Serialize.println(f.getClass());
        }
        Serialize.println("FieldSerializerFactoryRepository.printSerializers.done");
    }


    /**
     * auf jeden fall fieldserializer zurückgeben (falls das Field serialisierbar ist). auch wenns objekt null ist.
     * ob, und wie, was im json als "null" erscheint entscheidet der parentSerializer.
     *
     * @param parentSerializer
     * @param field
     * @return
     * @throws IllegalAccessException
     * @throws JsonSerializationException
     */
    public static FieldSerializer buildFieldSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException, JsonSerializationException {
        if (FieldAnalyzer.isJsonIgnored(field))
            return null;
        Class<?> type = field.getType();
        //check if already available
        if (fieldSerializerFactoryMap.containsKey(field)) {
            return fieldSerializerFactoryMap.get(field).createSerializer(parentSerializer, field);
        } else {
            //check if any available factory can serialize it
            for (FieldSerializerFactory factory : availableSerializationFactories.values()) {
                if (factory.canSerialize(field)) {
                    bindClassAndSerializerFactory(field, factory);
                    return factory.createSerializer(parentSerializer, field);
                }
            }
        }
        return null;
    }

    public static FieldDeserializer buildFieldDeserializer(SerializableEntityDeserializer parentSerializer, Field field) {
        if (FieldAnalyzer.isJsonIgnored(field))
            return null;
        //check if already available
        if (classIDeserializerFactoryMap.containsKey(field)) {
            return classIDeserializerFactoryMap.get(field).createDeserializer(parentSerializer, field);
        } else {
            //check if any available factory can serialize it
            for (FieldDeserializerFactory factory : availableDeserializationFactories.values()) {
                if (factory.canDeserialize(field)) {
                    bindClassAndDeserializerFactory(field, factory);
                    return factory.createDeserializer(parentSerializer, field);
                }
            }
        }
        return null;
    }
}
