package de.mein.core.serialize.serialize.fieldserializer.map;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.primitive.PrimitiveFieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.primitive.PrimitiveFieldSerializerFactory;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;
import de.mein.core.serialize.serialize.tools.StringBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Created by xor on 1/13/17.
 */
public class MapSerializer extends FieldSerializer {

    private final SerializableEntitySerializer parentSerializer;
    private final Field field;


    public MapSerializer(SerializableEntitySerializer parentSerializer, Field field) {
        this.parentSerializer = parentSerializer;
        this.field = field;
    }

    @Override
    public boolean isNull() {
        return field == null;
    }

    @Override
    public String JSON() throws JsonSerializationException {
        field.setAccessible(true);
        try {
            Map<?, ?> ins = (Map<?, ?>) field.get(parentSerializer.getEntity());
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
                    .comma().key("__m").eq().arrBegin();

            for (Object key : ins.keySet()) {
                if (!isFirst)
                    b.comma();
                isFirst = false;
                b.objBegin().key(kFactory.createSerializerOnClass(this, key).JSON());
                b.eq().append(vFactory.createSerializerOnClass(this, ins.get(key)).JSON())
                .objEnd();
            }
            b.arrEnd().objEnd();
            return b.toString();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        throw new JsonSerializationException(new Exception("some thing went wrong. obviously"));
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
