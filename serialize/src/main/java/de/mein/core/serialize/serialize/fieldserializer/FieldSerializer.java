package de.mein.core.serialize.serialize.fieldserializer;

import de.mein.core.serialize.exceptions.JsonSerializationException;

/**
 * Created by xor on 12/20/15.
 */
public abstract class FieldSerializer {

    public abstract boolean isNull();

    public abstract String JSON() throws JsonSerializationException;

}
