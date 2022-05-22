package de.mel.sql.transform;

import com.sun.org.apache.xpath.internal.operations.Bool;

public abstract class SqlResultTransformer {
    public abstract <T> T convert(Class<T> resultClass, Object value);

    /**
     * Convenience references that can be used in Kotlin for casting reasons
     */
    public static Class<Long> CLASS_LONG = Long.class;
    public static Class<Integer> CLASS_INT = Integer.class;

    public static SqlResultTransformer sqliteResultSetTransformer() {
        return new SqlResultTransformer() {
            @Override
            public <T> T convert(Class<T> resultClass, Object value) {
                if (value == null)
                    return null;
                if (Number.class.isAssignableFrom(resultClass)) {
                    NumberTransformer nt = NumberTransformer.forType((Class<? extends Number>) resultClass);
                    // numbers may return als actual numbers
                    if (Number.class.isAssignableFrom(value.getClass())) {
                        return (T) nt.cast((Number) value);
                    }
                    // but sometimes 0 and 1 are represented as booleans
                    else if (Boolean.class.isAssignableFrom(value.getClass())) {
                        boolean b = (boolean) value;
                        if (b)
                            return (T) nt.cast(1);
                        return (T) nt.cast(0);
                    }
                }
                if (!resultClass.equals(value.getClass())) {
                    System.out.println("SqlResultTransformer.convert.error");
//                    NumberTransformer nt = NumberTransformer.forType((Class<? extends Number>) resultClass);
//                    T casted = (T) nt.cast((Number) value);
//                    return casted;
                }
                return (T) value;
            }
        };
    }
}
