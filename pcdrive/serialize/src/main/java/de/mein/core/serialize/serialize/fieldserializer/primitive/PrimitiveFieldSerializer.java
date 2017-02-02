package de.mein.core.serialize.serialize.fieldserializer.primitive;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.tools.StringBuilder;


/**
 * Created by xor on 12/20/15.
 */
public class PrimitiveFieldSerializer extends FieldSerializer {
    private final Object value;

    public PrimitiveFieldSerializer(Object value) {
        this.value = value;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public String JSON() throws JsonSerializationException {
        return new StringBuilder().value(value).toString();
    }

}
