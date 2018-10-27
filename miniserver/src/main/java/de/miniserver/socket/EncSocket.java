package de.miniserver.socket;

import de.mein.auth.MeinStrings;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.update.SimpleSocket;
import de.mein.update.VersionAnswer;

import java.io.IOException;
import java.net.Socket;

public class EncSocket extends SimpleSocket {
    private final VersionAnswer versionAnswer;

    public EncSocket(Socket socket, VersionAnswer versionAnswer) throws IOException {
        super(socket);
        this.versionAnswer = versionAnswer;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    @Override
    public void runImpl() {
        try {
            String jsonAnswer = SerializableEntitySerializer.serialize(versionAnswer);
            while (!Thread.currentThread().isInterrupted()) {
                String s = in.readUTF();
                if (s.equals(MeinStrings.update.QUERY_VERSION)) {
                    out.writeUTF(jsonAnswer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }
}
