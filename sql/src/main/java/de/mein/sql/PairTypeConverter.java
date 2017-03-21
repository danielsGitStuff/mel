package de.mein.sql;

/**
 * Created by xor on 3/21/17.
 */
public interface PairTypeConverter {
    <V> V convert(Class<V> type, Object value);
}
