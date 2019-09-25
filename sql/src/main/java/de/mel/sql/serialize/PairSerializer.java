package de.mel.sql.serialize;

import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mel.core.serialize.serialize.fieldserializer.binary.BinaryFieldSerializer;
import de.mel.core.serialize.serialize.tools.StringBuilder;
import de.mel.sql.Pair;

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
