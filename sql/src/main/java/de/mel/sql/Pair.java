package de.mel.sql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mel.sql.transform.NumberTransformer;

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




    private static PairTypeConverter typeConverter = new PairTypeConverter();
    private IPairSetListener<V> hiddenSetListener;

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

    public Pair<V> ignoreSetListener() {
        if (hiddenSetListener == null) {
            hiddenSetListener = setListener;
            setListener = null;
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
            if (hiddenSetListener != null) {
                setListener = hiddenSetListener;
                hiddenSetListener = null;
            }
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


    /**
     * sets value to null
     *
     * @return
     */
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

    public static String hash(List<Pair<?>> pairs) {
        return hash(pairs.toArray(new Pair[0]));
    }

    public static String hash(Pair<?>... pairs) {
        MD5er md5er = new MD5er();
        hash(md5er, pairs);
        return md5er.digest();
    }

    /**
     * feeds the MD5er with pairs but does not digest at the end.
     *
     * @param md5er
     * @param pairs
     */
    public static MD5er hash(MD5er md5er, Pair<?>... pairs) {
        for (Pair<?> pair : pairs) {
            md5er.hash(pair.v());
        }
        return md5er;
    }

    public static MD5er hash(MD5er md5er, List<Pair<?>> pairs) {
        return hash(md5er, pairs.toArray(new Pair[0]));
    }

    public boolean equalsValue(Object o) {
        if (o != null && o instanceof Pair)
            o = ((Pair) o).v();
        if (value == null && o == null)
            return true;
        else if (value != null && o != null) {
            return value.equals(o);
        }
        return false;
    }

    public boolean notEqualsValue(Object o) {
        return !equalsValue(o);
    }
}