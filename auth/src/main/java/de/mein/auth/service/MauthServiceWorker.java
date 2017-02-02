package de.mein.auth.service;

import de.mein.auth.socket.MeinAuthSocket;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;

/**
 * Created by xor on 04.09.2016.
 */
@Deprecated
public class MauthServiceWorker implements Runnable {
    private MeinAuthService meinAuthService;

    @Override
    public void run() {

    }


}
