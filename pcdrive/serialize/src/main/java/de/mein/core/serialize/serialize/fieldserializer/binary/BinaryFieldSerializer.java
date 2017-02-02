package de.mein.core.serialize.serialize.fieldserializer.binary;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;

import java.util.Base64;

/**
 * Created by xor on 2/26/16.
 */
public class BinaryFieldSerializer extends FieldSerializer {
    private final byte[] bytes;

    public BinaryFieldSerializer(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public boolean isNull() {
        return (bytes == null || bytes.length == 0);
    }

    @Override
    public String JSON() throws JsonSerializationException {
        byte[] encoded = Base64.getEncoder().encode(bytes);
        String json = "\"" + new String(encoded) + "\"";
        return json;
    }
}
