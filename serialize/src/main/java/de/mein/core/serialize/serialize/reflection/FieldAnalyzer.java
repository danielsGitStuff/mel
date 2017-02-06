package de.mein.core.serialize.serialize.reflection;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;
import sun.reflect.generics.reflectiveObjects.TypeVariableImpl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * collects Fields of any given Class. It also can examine classes for different
 * traits.
 *
 * @author DECK006
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
            if (whatEver instanceof ParameterizedTypeImpl) {
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
            if (whatEver instanceof ParameterizedTypeImpl) {
                return false;
            }
            Class<?> genericType = (Class<?>) whatEver;
            return SerializableEntity.class.isAssignableFrom(genericType);
        }
        return false;
    }

    public static boolean isCollectionOfClass(Class clazz, Field field) {
        if (Collection.class.isAssignableFrom(field.getType())) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Object whatEver = parameterizedType.getActualTypeArguments()[0];
            if (whatEver instanceof ParameterizedTypeImpl) {
                return false;
            }
            if (whatEver instanceof TypeVariableImpl) {
                return false;
            }
            Class<?> genericType = (Class<?>) whatEver;
            return clazz.isAssignableFrom(genericType);
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
            if (whatEver instanceof ParameterizedTypeImpl) {
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
}
