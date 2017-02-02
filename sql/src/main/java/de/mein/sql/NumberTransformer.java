package de.mein.sql;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 30.10.2015.
 */
public enum NumberTransformer {
    INT(Integer.class) {
        @Override
        public Number cast(Number n) {
            return n == null ? null : n.intValue();
        }
    },
    LONG(Long.class) {
        @Override
        public Number cast(Number n) {
            return n == null ? null : n.longValue();
        }
    },
    NULL(null) {
        @Override
        public Number cast(Number n) {
            return null;
        }
    },
    DOUBLE(Double.class) {
        @Override
        public Number cast(Number n) {
            return n == null ? null : n.doubleValue();
        }
    },
    FLOAT(Float.class) {
        @Override
        public Number cast(Number n) {
            return n == null ? null : n.floatValue();
        }
    };

    private final static Map<Class<? extends Number>, NumberTransformer> TRANSFORMER_MAP = new HashMap<>();

    static {
        for (NumberTransformer tranformer : values()) {
            TRANSFORMER_MAP.put(tranformer.type, tranformer);
        }
    }

    private final Class<? extends Number> type;

    NumberTransformer(Class<? extends Number> type) {
        this.type = type;
    }

    public abstract Number cast(Number n);

    public static NumberTransformer forType(Class<? extends Number> type) {
        final NumberTransformer t = TRANSFORMER_MAP.get(type);
        return t == null ? NULL : t;
    }
}