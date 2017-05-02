package de.mein.core.serialize.serialize.fieldserializer.map;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.primitive.PrimitiveFieldSerializerFactory;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;
import de.mein.core.serialize.serialize.tools.StringBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by xor on 1/13/17.
 */
public class MapSerializer extends FieldSerializer {

    private SerializableEntitySerializer parentSerializer;
    private Field field;
    private Map<?, ?> ins;


    public MapSerializer(SerializableEntitySerializer parentSerializer, Field field) throws IllegalAccessException {
        this.parentSerializer = parentSerializer;
        this.field = field;
        field.setAccessible(true);
        ins = (Map<?, ?>) field.get(parentSerializer.getEntity());
    }

    @Override
    public boolean isNull() {
        return ins == null;
    }

    @Override
    public String JSON() throws JsonSerializationException {
        Class insClass = ins.getClass();
        Type type = field.getGenericType();
        ParameterizedType pType = (ParameterizedType) type;
        Class clazzK = (Class) pType.getActualTypeArguments()[0];
        Class clazzV = (Class) pType.getActualTypeArguments()[1];
        KeySerializerFactory kFactory = new KeySerializerFactory(parentSerializer);
        FieldSerializerFactory vFactory = createSerializerFactory(clazzV);
        // the actual JSON
        boolean isFirst = true;
        StringBuilder b = new StringBuilder();
        b.objBegin().key("__type").eq().value(insClass.getName())
                .comma().key("__k").eq().value(clazzK.getName())
                .comma().key("__v").eq().value(clazzV.getName())
                .comma().key("__x").eq().objBegin();
        // we have to add all objects to an array first cause unlike in Java
        // you cannot use Entities as keys in JSON. So we reference every key with another Map.
        Set<?> keySet = new HashSet<>(ins.keySet());
        Set<SerializableEntity> keyEntities = new HashSet<>();
        Map<Integer, Object> keyIdKeyMap = new HashMap<>();
        Map<Object, Integer> keyKeyIdMap = new HashMap<>();
        boolean appendComma = false;
        Integer keyIdInc = 0;
        for (Object k : keySet.toArray()) {
            if (appendComma)
                b.comma();
            keyIdKeyMap.put(keyIdInc, k);
            keyKeyIdMap.put(k, keyIdInc);
            b.key(keyIdInc.toString()).eq();
            keyIdInc++;
            if (k instanceof SerializableEntity) {
                SerializableEntitySerializer serializer = parentSerializer.getPreparedSerializer((SerializableEntity) k);
                b.append(serializer.JSON());
                keySet.remove(k);
                keyEntities.add((SerializableEntity) k);
            } else {
                b.value(k);
            }
            appendComma = true;
        }
        b.objEnd().comma().key("__m").eq().objBegin();
        // here comes the reference part. primitives go here
        for (Object key : keySet) {
            Integer keyId = keyKeyIdMap.get(key);
            b.key(keyId.toString()).eq();
            b.append(vFactory.createSerializerOnClass(parentSerializer, ins.get(key)).JSON());
        }
        // add SerializableEntities
        for (SerializableEntity key : keyEntities) {
            Integer keyId = keyKeyIdMap.get(key);
            b.key(keyId.toString()).eq();
            FieldSerializer vSer = vFactory.createObjectSerializer(parentSerializer, ins.get(key));
            //FieldSerializer vSer = vFactory.createSerializerOnClass(parentSerializer, ins.get(key));
            b.append(vSer.JSON());
        }
        System.out.println("MapSerializer.JSON");
//            for (Object key : ins.keySet()) {
//                if (!isFirst)
//                    b.comma();
//                isFirst = false;
//                KeySerializer keySerializer = (KeySerializer) kFactory.createSerializerOnClass(parentSerializer, key);
//                if (!keySerializer.isPrimitive()) {
//                    b.append(keySerializer.JSON());
//                } else {
//                    b.objBegin().key(keySerializer.JSON());
//                }
//                b.eq().append(vFactory.createSerializerOnClass(parentSerializer, ins.get(key)).JSON())
//                        .objEnd();
//            }
        b.objEnd().objEnd();
        return b.toString();
    }


    private FieldSerializerFactory createSerializerFactory(Class clazz) {
        if (FieldAnalyzer.isPrimitiveClass(clazz)) {
            return new PrimitiveFieldSerializerFactory();
        } else if (FieldAnalyzer.isEntitySerializableClass(clazz)) {
            return new SerializableEntitySerializerFactory();
        } else {
            System.err.println("MapSerializer.createSerializerFactory: could not create Serializer for generic type: " + clazz.getName());
        }
        return null;
    }
}
