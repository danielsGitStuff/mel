package de.mein.sql.transform;

public abstract class SqlResultTransformer {
    public abstract <T> T convert(Class<T> resultClass, Object value);

    public static SqlResultTransformer sqliteResultSetTransformer() {
        return new SqlResultTransformer() {
            @Override
            public <T> T convert(Class<T> resultClass, Object value) {
                if (value == null)
                    return null;
                if (Number.class.isAssignableFrom(value.getClass()) || Number.class.isAssignableFrom(resultClass)){
                    NumberTransformer nt = NumberTransformer.forType((Class<? extends Number>) resultClass);
                    return (T) nt.cast((Number) value);
                }
                if (!resultClass.equals(value.getClass())){
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
