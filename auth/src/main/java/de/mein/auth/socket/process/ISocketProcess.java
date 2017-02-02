package de.mein.auth.socket.process;

import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinSocket;
import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 4/27/16.
 */
public interface ISocketProcess {
    void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket webSocket);
}
