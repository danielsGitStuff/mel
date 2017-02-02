package de.mein.core.serialize.serialize.fieldserializer;

import de.mein.core.serialize.exceptions.JsonSerializationException;

/**
 * Created by xor on 12/20/15.
 */
public class NullSerializer  extends FieldSerializer{
    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public String JSON() throws JsonSerializationException {
        return null;
    }
}
