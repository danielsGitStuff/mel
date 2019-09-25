package de.mel.core.serialize.serialize.fieldserializer.binary;

import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializer;

import java.util.Base64;

/**
 * Created by xor on 2/26/16.
 */
public class BinaryFieldSerializer extends FieldSerializer {
    private final byte[] bytes;

    public interface Base64Encoder {
        byte[] encode(byte[] bytes);
    }

    private static Base64Encoder base64Encoder = bytes -> Base64.getEncoder().encode(bytes);

    public static void setBase64Encoder(Base64Encoder base64Encoder) {
        BinaryFieldSerializer.base64Encoder = base64Encoder;
    }

    public BinaryFieldSerializer(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public boolean isNull() {
        return (bytes == null || bytes.length == 0);
    }

    @Override
    public String JSON() throws JsonSerializationException {
        String json = "\"" + new String(BinaryFieldSerializer.base64Encoder.encode(bytes)) + "\"";
        return json;
    }
}
