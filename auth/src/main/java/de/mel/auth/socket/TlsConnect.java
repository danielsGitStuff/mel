package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import de.mel.sql.SqlQueriesException;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

/**
 * The outgoing side of a connection: TCP connect and TLS handshake, both within the connect timeout of the instance.
 */
final class TlsConnect {
    private TlsConnect() {
    }

    static Socket connect(MelAuthService service, String address, int port) throws SqlQueriesException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        Socket socket = service.getCertificateManager().createSocket();
        Lok.debug("TlsConnect: " + address + ":" + port);
        int timeout = (int) service.getConnectTimeoutMs();
        try {
            socket.connect(new InetSocketAddress(address, port), timeout);
            // the handshake is part of connecting: a partner that accepts and then says nothing must not hold us up
            socket.setSoTimeout(timeout);
            ((SSLSocket) socket).startHandshake();
            socket.setSoTimeout(0);
        } catch (IOException e) {
            N.s(socket::close);
            throw e;
        }
        return socket;
    }
}
