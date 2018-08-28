package de.mein.sql.serialize;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.binary.BinaryFieldSerializer;
import de.mein.core.serialize.serialize.tools.StringBuilder;
import de.mein.sql.Pair;

/**
 * Created by xor on 12/20/15.
 */
public class PairSerializer extends FieldSerializer {
    private final Pair<?> pair;

    public PairSerializer(Pair<?> pair) {
        this.pair = pair;
    }

    @Override
    public boolean isNull() {
        return pair.ignoreListener().v() == null;
    }

    @Override
    public String JSON() throws JsonSerializationException {
        if (pair.getGenericClass().equals(byte[].class)) {
            return new BinaryFieldSerializer((byte[]) pair.ignoreListener().v()).JSON();
        }
        return new StringBuilder().value(pair.ignoreListener().v()).toString();
    }
}
