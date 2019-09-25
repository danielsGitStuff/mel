package de.mel;

import de.mel.auth.data.access.DatabaseManager;
import de.mel.core.serialize.deserialize.binary.BinaryDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.binary.BinaryFieldSerializer;
import de.mel.execute.SqliteExecutor;
import de.mel.execute.SqliteExecutorInjection;
import de.mel.sql.Pair;
import de.mel.sql.PairTypeConverter;

/**
 * Collection of all places where some customized implementations might be useful.
 * e.g. where the standard way of doing things in Java do not work on Android
 */

public class MelInjector {
    private MelInjector() {
    }

    public static void setMelAuthSqlInputStreamInjector(DatabaseManager.SqlInputStreamInjector sqlInputStreamInjector) {
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

    public static void setExecutorImpl(SqliteExecutorInjection injectedImpl) {
        SqliteExecutor.setExecutorImpl(injectedImpl);
    }

}
