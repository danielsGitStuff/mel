package de.mel.core.serialize.serialize.reflection;

import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * collects Fields of any given Class. It also can examine classes for different
 * traits.
 *
 * @author xor
 */
@SuppressWarnings("rawtypes")
public class FieldAnalyzer {

    private FieldAnalyzer() {

    }

    private static Map<String, Field> getFields(Class clazz) {
        Map<String, Field> result = new HashMap<>();
        Class superClazz = clazz.getSuperclass();
        if (superClazz != null) {
            result.putAll(FieldAnalyzer.getFields(superClazz));
        }
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                result.put(field.getName(), field);
            }
        }
        return result;
    }

    public static List<Field> collectFields(Class clazz) {
        Map<String, Field> fieldMap = FieldAnalyzer.getFields(clazz);
        List<Field> result = new ArrayList<>(fieldMap.values());
        return result;
    }

    public static boolean isJsonIgnored(Field field) {
        Annotation[] annotations = field.getAnnotations();
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(JsonIgnore.class))
                    return true;
            }
            return false;
        }
        return false;
    }

    public static boolean isCollectionClass(Class clazz) {
        boolean isCollection = Collection.class.isAssignableFrom(clazz);
        return isCollection;
    }


    public static boolean isPrimitiveCollection(Field field) {
        boolean isCollection = Collection.class.isAssignableFrom(field.getType());
        if (isCollection) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Object whatEver = parameterizedType.getActualTypeArguments()[0];
            if (whatEver instanceof ParameterizedType || whatEver instanceof TypeVariable) {
                return false;
            }
            Class<?> genericType = (Class<?>) whatEver;
            return FieldAnalyzer.isPrimitiveClass(genericType);
        }
        return false;
    }

    public static boolean isEntitySerializableCollection(Field field) {
        boolean isCollection = Collection.class.isAssignableFrom(field.getType());
        if (isCollection) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Object whatEver = parameterizedType.getActualTypeArguments()[0];
            if (whatEver instanceof ParameterizedType || whatEver instanceof TypeVariable) {
                return false;
            }
            Class<?> genericType = (Class<?>) whatEver;
            return SerializableEntity.class.isAssignableFrom(genericType);
        }
        return false;
    }

    public static boolean isGenericCollectionOfClass(Field field, Class clazz) {
        if (Collection.class.isAssignableFrom(field.getType())) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Type[] types = parameterizedType.getActualTypeArguments();
            //collections only have one type
            Type type = types[0];
            if (type instanceof ParameterizedType) {
                Class genClass = (Class) ((ParameterizedType) type).getRawType();
                if (clazz.isAssignableFrom(genClass))
                    return true;
            } else if (type instanceof TypeVariable) {
                System.out.println("FieldAnalyzer.isGenericCollectionOfClass().TypeVariable.NOT:IMPLEMENTED:YET");
                System.out.println("FieldAnalyzer.isGenericCollectionOfClass().TypeVariable.NOT:IMPLEMENTED:YET");
                System.out.println("FieldAnalyzer.isGenericCollectionOfClass().TypeVariable.NOT:IMPLEMENTED:YET");
                System.out.println("FieldAnalyzer.isGenericCollectionOfClass().TypeVariable.NOT:IMPLEMENTED:YET");
                System.out.println("FieldAnalyzer.isGenericCollectionOfClass().TypeVariable.NOT:IMPLEMENTED:YET");
                System.out.println("FieldAnalyzer.isGenericCollectionOfClass().TypeVariable.NOT:IMPLEMENTED:YET");
                System.out.println("FieldAnalyzer.isGenericCollectionOfClass().TypeVariable.NOT:IMPLEMENTED:YET");
            }
        }
        return false;
    }

    /**
     * @param field
     * @return true if clazz is assignable from Entity
     */
    public static boolean isEntitySerializable(Field field) {
        Class clazz = field.getType();
        return SerializableEntity.class.isAssignableFrom(clazz);
    }

    public static boolean isEntitySerializableClass(Class clazz) {
        return SerializableEntity.class.isAssignableFrom(clazz);
    }

    public static boolean isOfClass(Field field, Class expected) {
        Class clazz = field.getType();
        return expected.isAssignableFrom(clazz);
    }

    private static final Set<Class<?>> primitiveClasses = new HashSet();

    static {
        Class<?>[] classes = new Class[]{Byte.class, byte.class, short.class, Short.class, int.class, Integer.class,
                long.class, Long.class, float.class, Float.class, double.class, Double.class, char.class,
                Character.class, String.class, boolean.class, Boolean.class
        };
        for (Class clazz : classes) {
            primitiveClasses.add(clazz);
        }
    }

    public static boolean isPrimitiveClass(Class<?> type) {
        return primitiveClasses.contains(type);
    }

    public static boolean isPrimitive(Field field) {
        return isPrimitiveClass(field.getType());
    }

    public static boolean isPrimitiveMap(Field field) {
        boolean isMap = isMap(field);
        if (isMap) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Object whatEver = parameterizedType.getActualTypeArguments()[0];
            if (whatEver instanceof ParameterizedType) {
                return false;
            }
            Class<?> genericType = (Class<?>) whatEver;
            return isPrimitiveClass(genericType);
        }
        return false;
    }

    public static boolean isMap(Field field) {
        boolean isMap = Map.class.isAssignableFrom(field.getType());
        return isMap;
    }


    public static boolean isTransinient(Field field) {
        return Modifier.isTransient(field.getModifiers());
    }

    public static Object readField(Object object, String fieldName) {
        try {
            Class clazz = object.getClass();
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
