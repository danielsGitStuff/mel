package de.mein;

import de.mein.auth.data.access.DatabaseManager;

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

}
