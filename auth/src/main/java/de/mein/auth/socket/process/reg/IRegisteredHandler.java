package de.mein.auth.socket.process.reg;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 4/25/16.
 */
public  interface IRegisteredHandler {
    void onCertificateRegistered(MeinAuthService meinAuthService, Certificate registered) throws SqlQueriesException;
}
