package de.mein;

import de.mein.auth.data.access.DatabaseManager;
import de.mein.core.serialize.deserialize.binary.BinaryDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.binary.BinaryFieldSerializer;

/**
 * Created by xor on 2/4/17.
 */

public class MeinInjector {
    private MeinInjector() {
    }

    public static void setMeinAuthSqlInputStreamInjector(DatabaseManager.SqlInputStreamInjector sqlInputStreamInjector) {
        DatabaseManager.setSqlInputStreamInjector(sqlInputStreamInjector);
    }

    public static void setSQLConnectionCreator(DatabaseManager.SQLConnectionCreator connectionCreator) {
        DatabaseManager.setSqlConnectionCreator(connectionCreator);
    }

    public static void setBase64Encoder(BinaryFieldSerializer.Base64Encoder encoder) {
        BinaryFieldSerializer.setBase64Encoder(encoder);
    }

    public static void setBase64Decoder(BinaryDeserializer.Base64Decoder decoder) {
        BinaryDeserializer.setBase64Decoder(decoder);
    }
}
