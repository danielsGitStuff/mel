package de.mein.update;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;

import java.io.IOException;
import java.net.Socket;

public class UpdateMessageSocket extends SimpleSocket {
    private final Updater updater;
    private final String variant;

    public UpdateMessageSocket(Updater updater, Socket socket, String variant) throws IOException {
        super(socket);
        this.updater = updater;
        this.variant = variant;
    }

    @Override
    public void runImpl() {
        try {
            out.writeUTF(MeinStrings.update.QUERY_VERSION);
            String json = in.readUTF();
            Lok.debug(json);
            VersionAnswer answer = (VersionAnswer) SerializableEntityDeserializer.deserialize(json);
            VersionAnswer.VersionEntry entry= answer.getEntry(variant);
            if (entry== null){
                Lok.error("update server has not the variant('"+variant+"') I am looking for :(");
            }else {
                updater.onVersionAvailable(entry);
            }
        } catch (IOException | JsonDeserializationException e) {
            e.printStackTrace();
        }
    }
}
