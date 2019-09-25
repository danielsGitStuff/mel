package de.mel.sql;

import de.mel.sql.transform.NumberTransformer;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 3/21/17.
 */
public class PairTypeConverter {

    public interface TypeConvertFunction<V> {
        V convert(Class<V> type, Object value) throws TypeConvertException;
    }

    public class TypeConvertException extends Exception {
        public TypeConvertException() {
        }

        public TypeConvertException(String msg) {
            super(msg);
        }
    }

    private static Map<Class, TypeConvertFunction> convertFunctionMap = new HashMap<>();

    private final TypeConvertFunction<? extends Number> toNumber = (TypeConvertFunction<Number>) (type, value) -> {
        NumberTransformer numberTransformer = NumberTransformer.forType(type);
        return numberTransformer.cast((Number) value);
    };

    private final TypeConvertFunction<Enum> toEnum = (type, value) -> {
        @SuppressWarnings("rawtypes")
        Class<Enum> eType = type;
        return Enum.valueOf(eType, (String) value);
    };

    private final TypeConvertFunction<Boolean> toBoolean = new TypeConvertFunction<Boolean>() {
        @Override
        public Boolean convert(Class<Boolean> type, Object value) throws TypeConvertException {
            if (value.getClass().equals(String.class)) {
                return new Boolean(Boolean.parseBoolean((String) value));
            } else if (value.getClass().equals(Integer.class)) {
                return new Boolean((((Integer) value) == 1) ? true : false);
            }
            System.err.println("PairTypeConverter: COULD NOT CAST VALUE TO BOOLEAN: " + value);
            throw new TypeConvertException("COULD NOT CAST VALUE TO BOOLEAN:" + value);
        }
    };

    <V> V convert(Class<V> type, Object value) throws TypeConvertException {
        if (value != null) {
            // nothing to do
            if (type.equals(value.getClass()))
                return (V) value;

            // check if present
            if (convertFunctionMap.containsKey(type))
                return (V) convertFunctionMap.get(type).convert(type, value);

            V v = null;
            TypeConvertFunction<V> convertFunction = null;
            boolean b = type.equals(boolean.class);

            // check numbers first
            String canonicalName = type.getCanonicalName();
            boolean isNumber = Number.class.isAssignableFrom(type)
                    || canonicalName.equals("byte")
                    || canonicalName.equals("short")
                    || canonicalName.equals("int")
                    || canonicalName.equals("long")
                    || canonicalName.equals("float")
                    || canonicalName.equals("double");
            if (isNumber) {
                convertFunction = (TypeConvertFunction<V>) toNumber;
            } else if (type.isEnum() && value instanceof String) {
                convertFunction = (TypeConvertFunction<V>) toEnum;
            } else if (type.equals(boolean.class) || type.equals(Boolean.class) && value.getClass().equals(String.class)) {
                convertFunction = (TypeConvertFunction<V>) toBoolean;
            } else if (type.equals(boolean.class) || type.equals(Boolean.class) && value.getClass().equals(Integer.class)) {
                convertFunction = (TypeConvertFunction<V>) toBoolean;
            } else {
                System.err.println(".setValueUnsecure().class.mismatch: class is " + type);
                System.err.println("delivered class is " + value.getClass());
                System.err.println("{key,value} is " + toString());
            }
            if (convertFunction != null) {
                v = convertFunction.convert(type, value);
                convertFunctionMap.put(type, convertFunction);
            }
            return v;
        }
        return null;
    }
}
