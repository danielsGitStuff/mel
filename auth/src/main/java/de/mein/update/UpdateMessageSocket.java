package de.mein.update;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.tools.N;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.sql.Hash;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

public class UpdateMessageSocket extends SimpleSocket {
    private final Updater updater;
    private final String variant;
    private final String serverCertHash;

    public UpdateMessageSocket(Updater updater, Socket socket, String variant, String serverCertHash) throws IOException {
        super(socket);
        this.updater = updater;
        this.variant = variant;
        this.serverCertHash = serverCertHash;
    }

    @Override
    public void runImpl() {
        try {
            SSLSocket sslSocket = (SSLSocket) socket;
            Certificate cert = sslSocket.getSession().getPeerCertificates()[0];
            String hash = Hash.sha256(cert.getEncoded());
            if (!hash.equals(serverCertHash))
                throw new IOException("expected hash: " + serverCertHash + " but server was: " + hash);
            out.writeUTF(MeinStrings.update.QUERY_VERSION);
            String json = in.readUTF();
            Lok.debug(json);
            VersionAnswer answer = (VersionAnswer) SerializableEntityDeserializer.deserialize(json);
            VersionAnswer.VersionEntry entry = answer.getEntry(variant);
            if (entry == null) {
                Lok.error("update server has not the variant('" + variant + "') I am looking for :(");
            } else {
                updater.onVersionAvailable(entry);
            }
        } catch (IOException | JsonDeserializationException | CertificateEncodingException e) {
            e.printStackTrace();
        } finally {
            N.s(out::close);
            N.s(in::close);
        }
    }
}
