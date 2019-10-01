package de.mel.core.serialize.serialize.tools;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to cast a JavaScript number to the desired Java Number type.
 *
 * @author xor
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
    },
    SHORT(Short.class) {
        @Override
        public Number cast(Number n) {
            return n == null ? null : n.shortValue();
        }
    },
    BYTE(Byte.class) {
        @Override
        public Number cast(Number n) {
            return n == null ? null : n.byteValue();
        }
    };

    private static Map<Class<? extends Number>, NumberTransformer> TRANSFORMER_MAP = null;

    private final Class<? extends Number> type;

    NumberTransformer(Class<? extends Number> type) {
        this.type = type;
    }

    public abstract Number cast(Number n);

    public static NumberTransformer forType(Class<? extends Number> type) {
        if (TRANSFORMER_MAP == null)
            init();
        final NumberTransformer t = TRANSFORMER_MAP.get(type);
        return t == null ? NULL : t;
    }

    private static void init() {
        TRANSFORMER_MAP = new HashMap<>();
        for (NumberTransformer transformer : values()) {
            TRANSFORMER_MAP.put(transformer.type, transformer);
        }
    }
}
