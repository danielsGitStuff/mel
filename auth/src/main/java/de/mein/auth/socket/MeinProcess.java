package de.mein.auth.socket;

import de.mein.auth.MeinStrings;
import de.mein.auth.data.*;
import de.mein.auth.data.db.Certificate;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import org.jdeferred.impl.DeferredObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MeinProcesses handle everything that is read on the MeinAuthSocket.
 * There are several implementations of who care about registering, authentication and validating incoming and outgoing connections.
 */
public abstract class MeinProcess implements IRequestHandler {
    private static Logger logger = Logger.getLogger(MeinProcess.class.getName());
    protected MeinAuthSocket meinAuthSocket;
    protected Certificate partnerCertificate;
    protected Map<Long, DeferredObject<SerializableEntity, ResponseException, Void>> requestMap = new ConcurrentHashMap<>();

    public MeinProcess(MeinAuthSocket meinAuthSocket) {
        this.meinAuthSocket = meinAuthSocket;
        meinAuthSocket.setProcess(this);
        //TODO nach timeout bitte selbst löschen
    }


    protected void send(SerializableEntity meinMessage) throws JsonSerializationException, IllegalAccessException {
        String json = SerializableEntitySerializer.serialize(meinMessage);
        logger.log(Level.FINEST, meinAuthSocket.getMeinAuthService().getName() + ".send: " + json);
        meinAuthSocket.send(json);
    }


    public void removeThyself() {
        //meinAuthService.onProcessDone(this);
        meinAuthSocket.stop();
    }

    protected synchronized boolean handleAnswer(SerializableEntity deserialized) {
        Long answerId = null;
        if (deserialized instanceof MeinRequest) {
            answerId = ((MeinRequest) deserialized).getAnswerId();
        } else if (deserialized instanceof MeinResponse) {
            answerId = ((MeinResponse) deserialized).getResponseId();
        }
        if (answerId != null && this.requestMap.containsKey(answerId)) {
            StateMsg msg = (StateMsg) deserialized;
            DeferredObject<SerializableEntity, ResponseException, Void> deferred = requestMap.remove(answerId);
            if (!msg.getState().equals(MeinStrings.msg.STATE_OK)) {
                if (msg.getException()== null)
                    deferred.reject(new ResponseException("state was: "+msg.getState()));
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
    public void queueForResponse(MeinRequest request) {
        requestMap.put(request.getRequestId(), request.getAnswerDeferred());
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + meinAuthSocket.getMeinAuthService().getName();
    }

    public void stop() {
        meinAuthSocket.stop();
        meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(this);
    }

    public abstract void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket webSocket);

}
