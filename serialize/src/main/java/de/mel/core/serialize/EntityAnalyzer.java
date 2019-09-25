package de.mel.core.serialize;

import de.mel.core.serialize.serialize.reflection.FieldAnalyzer;
import de.mel.core.serialize.serialize.tools.StringBuilder;

import java.lang.reflect.*;
import java.util.*;

public class EntityAnalyzer {
    static final Map<String, Class<? extends SerializableEntity>> SIMPLECLASSNAME_CLASS_MAP = new HashMap<String, Class<? extends SerializableEntity>>() {
        /**
         *
         */
        private static final long serialVersionUID = 7276907138700440209L;

    };

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Class<? extends SerializableEntity> clazz(String className) throws ClassNotFoundException {
        Class<? extends SerializableEntity> clazz = null;
        if (EntityAnalyzer.SIMPLECLASSNAME_CLASS_MAP.containsKey(className)) {
            clazz = EntityAnalyzer.SIMPLECLASSNAME_CLASS_MAP.get(className);
        } else {
            clazz = (Class<? extends SerializableEntity>) Class.forName(className);
            if (SerializableEntity.class.isAssignableFrom(clazz)) {
                EntityAnalyzer.SIMPLECLASSNAME_CLASS_MAP.put(className, clazz);
            }
        }
        if (clazz == null) {
            System.err.println(className + " is not derived from Entity.class");
            throw new ClassNotFoundException(className + " is not derived from Entity.class");
        }
        return clazz;
    }

    public static String getType(SerializableEntity serializable) {
        return serializable.getClass().getName();
    }

    public static String toJSONlike(SerializableEntity serializable) {
        StringBuilder b = new StringBuilder();
        b.objBegin();
        b.key("__type");
        b.eq();
        b.value(getType(serializable));
        b.comma();
        EntityAnalyzer.getFields(serializable.getClass()).forEach(field -> {
            b.key(field.getName());
            b.eq();
            field.setAccessible(true);
            try {
                b.value(field.get(serializable));
            } catch (Exception e) {
                e.printStackTrace();
            }
            b.comma();
        });
        b.objEnd();
        return b.toString();
    }

    @SuppressWarnings("rawtypes")
    public static String toString(SerializableEntity serializable) {
        StringBuilder b = new StringBuilder();
        b.append("(").append(serializable.getClass()).append(")");
        b.objBegin().lineBreak();
        boolean valueWritten = false;
        try {
            for (Field field : EntityAnalyzer.getFields(serializable.getClass())) {
                field.setAccessible(true);
                Class fieldClazz = field.getType();
                Object fieldValue = field.get(serializable);
                if (fieldValue != null && !Modifier.isTransient(field.getModifiers())) {
                    if (valueWritten) {
                        b.comma().lineBreak();
                    }
                    b.key(field.getName()).eq();
                    if (FieldAnalyzer.isCollectionClass(fieldClazz)) {
                        Set set = (Set) fieldValue;
                        Type type = field.getGenericType();
                        String typeString = "";
                        if (type instanceof ParameterizedType) {
                            ParameterizedType pType = (ParameterizedType) type;
                            Class clarz = (Class) pType.getActualTypeArguments()[0];
                            typeString = clarz.getSimpleName();
                        }
                        b.arrBegin().append(typeString).append(" x " + set.size()).arrEnd();
                    } else if (FieldAnalyzer.isEntitySerializable(field)) {
                        b.objBegin().append(fieldClazz.getSimpleName()).objEnd();
                    } else {
                        b.value(fieldValue);
                    }
                    valueWritten = true;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        b.lineBreak().objEnd();
        return b.toString();
    }

    public static List<Field> getSerializableFields(SerializableEntity serializable) {
        List<Field> result = new ArrayList<>();
        for (Field field : EntityAnalyzer.getFields(serializable.getClass())) {
            if (!(Modifier.isTransient(field.getModifiers()) || FieldAnalyzer.isJsonIgnored(field)))
                result.add(field);
        }
        return result;
    }

    @JsonIgnore
    private static final Map<Class<? extends SerializableEntity>, List<Field>> CLASS_FIELDS_MAP = Collections
            .synchronizedMap(new HashMap<>());

    public static List<Field> getFields(Class<? extends SerializableEntity> clazz) {
        if (!EntityAnalyzer.CLASS_FIELDS_MAP.containsKey(clazz)) {
            List<Field> fields = FieldAnalyzer.collectFields(clazz);
            EntityAnalyzer.CLASS_FIELDS_MAP.put((Class<? extends SerializableEntity>) clazz, fields);
        }
        List<Field> fields = EntityAnalyzer.CLASS_FIELDS_MAP.get(clazz);
        return fields;
    }


    public static SerializableEntity instance(String simpleClassName)
            throws ReflectiveOperationException {
        try {
            Constructor<? extends SerializableEntity> constructor = EntityAnalyzer.clazz(simpleClassName).getDeclaredConstructor();
            SerializableEntity entity = constructor.newInstance();
            return entity;
        } catch (ReflectiveOperationException e) {
            System.err.println("could not create instance with default constructor for class: " + simpleClassName);
            throw e;
        }
    }

}
