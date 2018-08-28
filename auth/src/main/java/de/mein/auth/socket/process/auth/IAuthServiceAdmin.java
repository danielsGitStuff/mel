package de.mein.auth.socket.process.auth;

import de.mein.auth.data.db.Certificate;

/**
 * Created by xor on 4/18/16.
 */
public interface IAuthServiceAdmin {
    boolean acceptRegister(Certificate certificate);
}
