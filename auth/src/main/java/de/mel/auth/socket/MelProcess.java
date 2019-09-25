package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.*;
import de.mel.auth.data.db.Certificate;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.exceptions.MelJsonException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import org.jdeferred.impl.DeferredObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



/**
 * MelProcesses handle everything that is read on the MelAuthSocket.
 * There are several implementations of who care about registering, authentication and validating incoming and outgoing connections.
 */
public abstract class MelProcess implements IRequestHandler {
    protected MelAuthSocket melAuthSocket;
    protected Certificate partnerCertificate;
    protected Map<Long, DeferredObject<SerializableEntity, ResponseException, Void>> requestMap = new ConcurrentHashMap<>();

    public MelProcess(MelAuthSocket melAuthSocket) {
        this.melAuthSocket = melAuthSocket;
        melAuthSocket.setProcess(this);
        //TODO nach timeout bitte selbst löschen
    }


    protected void send(SerializableEntity melMessage) throws JsonSerializationException {
        String json = SerializableEntitySerializer.serialize(melMessage);
//       Lok.debug(melAuthSocket.getMelAuthService().getName() + ".send: " + json);
        melAuthSocket.send(json);
    }


    public void removeThyself() {
        //melAuthService.onProcessDone(this);
        melAuthSocket.stop();
    }

    protected synchronized boolean handleAnswer(SerializableEntity deserialized) {
        Long answerId = null;
        if (deserialized instanceof MelRequest) {
            answerId = ((MelRequest) deserialized).getAnswerId();
        } else if (deserialized instanceof MelResponse) {
            answerId = ((MelResponse) deserialized).getResponseId();
        }
        if (answerId != null && this.requestMap.containsKey(answerId)) {
            StateMsg msg = (StateMsg) deserialized;
            DeferredObject<SerializableEntity, ResponseException, Void> deferred = requestMap.remove(answerId);
            if (!msg.getState().equals(MelStrings.msg.STATE_OK)) {
                if (msg.getException() == null)
                    deferred.reject(new ResponseException("state was: " + msg.getState()));
                else
                    deferred.reject(msg.getException());
                return true;
            }
            deferred.resolve(deserialized);
            return true;
        }
        return false;
    }

    @Override
    public void queueForResponse(MelRequest request) {
        requestMap.put(request.getRequestId(), request.getAnswerDeferred());
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + melAuthSocket.getMelAuthService().getName();
    }

    public void stop() {
        melAuthSocket.stop();
        melAuthSocket.getMelAuthService().getPowerManager().releaseWakeLock(this);
    }

    public abstract void onMessageReceived(SerializableEntity deserialized, MelAuthSocket webSocket) throws IOException, MelJsonException;

    public void onSocketClosed(int code, String reason, boolean remote) {
        // nothing here yet
    }
}
