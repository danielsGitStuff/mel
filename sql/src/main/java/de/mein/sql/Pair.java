package de.mein.sql;

import de.mein.sql.transform.NumberTransformer;

/**
 * this is a simple key value db structure which can cast its value
 * automatically. it also holds the database table name as key. basically it is
 * a row (with column names and types) in a database table.
 *
 * @param <V>
 * @author xor
 */
public class Pair<V> {
    private static Logger logger = Logger.getLogger(Pair.class);
    private V value;
    private String key;
    private static final String OPEN_BRACE = "{";
    private static final String COMMA = ",";
    private static final String CLOSE_BRACE = "}";
    private IPairGetListener getListener = null;
    private IPairGetListener hiddenGetListener = null;
    private IPairSetListener<V> setListener = null;
    private static PairTypeConverter typeConverter = new PairTypeConverter() {
        @Override
        public <V> V convert(Class<V> type, Object value) {
            if (value != null) {
                V v = null;
                if (type.equals(value.getClass())) {
                    v = (V) value;
                } else if (type.isEnum() && value instanceof String) {
                    @SuppressWarnings("rawtypes")
                    Class<Enum> eType = (Class<Enum>) type;
                    v = (V) Enum.valueOf(eType, (String) value);
                } else if (Number.class.isAssignableFrom(type)) {
                    NumberTransformer numberTransformer = NumberTransformer.forType((Class<? extends Number>) type);
                    v = (V) numberTransformer.cast((Number) value);
                } else if (type.equals(Boolean.class) && value.getClass().equals(String.class)) {
                    v = (V) new Boolean(Boolean.parseBoolean((String) value));
                } else if (type.equals(Boolean.class) && value.getClass().equals(Integer.class)) {
                    v = (V) new Boolean((((Integer) value) == 1) ? true : false);
                } else {
                    logger.warn(".setValueUnsecure().class.mismatch: class is " + type);
                    logger.warn("delivered class is " + value.getClass());
                    logger.warn("{key,value} is " + toString());
                }
                return v;
            }
            return null;
        }
    };

    public static void setTypeConverter(PairTypeConverter typeConverter) {
        Pair.typeConverter = typeConverter;
    }

    private Class<V> type;

    public Class<V> getGenericClass() {
        return type;
    }

    public Pair(Class<V> type, String key) {
        this.type = type;
        this.key = key;
    }

    public Pair(Class<V> type, String key, final V value) {
        this.type = type;
        this.key = key;
        this.v(value);
    }

    public String k() {
        return key;
    }

    public V v() {
        if (getListener != null)
            getListener.onGetCalled();
        if (hiddenGetListener != null) {
            getListener = hiddenGetListener;
            hiddenGetListener = null;
        }
        return value;
    }

    public Pair<V> setSetListener(IPairSetListener<V> setListener) {
        this.setListener = setListener;
        return this;
    }

    /**
     * ignores the getListener for the next time v() is called.
     *
     * @return
     */
    public Pair<V> ignoreListener() {
        if (hiddenGetListener == null) {
            hiddenGetListener = getListener;
            getListener = null;
        }
        return this;
    }

    public String valueAsString() {
        if (value != null) {
            return value.toString();
        }
        return null;
    }

    public Pair<V> v(V value) {
        if (setListener != null) {
            this.value = setListener.onSetCalled(value);
        } else
            this.value = value;

        return this;
    }

    public Pair<V> v(Pair<V> pair) {
        this.value = pair.v();
        return this;
    }

    public String toString() {
        return OPEN_BRACE + key + COMMA + value + CLOSE_BRACE;
    }

    @SuppressWarnings("unchecked")
    public void setValueUnsecure(Object value) {
        try {
            V v = typeConverter.convert(this.type, value);
            if (setListener != null) {
                this.value = setListener.onSetCalled(v);
            } else {
                this.value = v;
            }
        } catch (Exception e) {
            System.err.println("Pair{name:'" + k() + "'}.setValueUnsecure('" + value + "')");
            logger.error("stacktrace", e);
        }
    }

    @Override
    public int hashCode() {
        if (value != null) {
            return value.hashCode();
        }
        return key.hashCode();
    }

    public int calcHash() {
        if (value != null)
            return value.hashCode();
        return 0;
    }

    public Pair<V> setGetListener(IPairGetListener<V> getListener) {
        this.getListener = getListener;
        hiddenGetListener = null;
        return this;
    }

    public IPairGetListener getGetListener() {
        return getListener;
    }


    public Pair<V> nul() {
        value = null;
        return this;
    }

    public boolean isNull() {
        return value == null;
    }

    public boolean notNull() {
        return value != null;
    }
}