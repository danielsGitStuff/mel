package de.miniserver.socket;

import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.miniserver.data.VersionAnswer;

import java.io.IOException;
import java.net.Socket;

public class EncSocket extends SimpleSocket {
    public static final String QUERY_VERSION = "v?";
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
    public void run() {
        try {
            String jsonAnswer = SerializableEntitySerializer.serialize(versionAnswer);
            while (!Thread.currentThread().isInterrupted()) {
                String s = in.readUTF();
                if (s.equals(QUERY_VERSION)) {
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
