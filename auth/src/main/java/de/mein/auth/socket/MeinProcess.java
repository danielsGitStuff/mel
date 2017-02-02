package de.mein.auth.socket;

import de.mein.auth.data.*;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.socket.process.ISocketProcess;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 4/20/16.
 */
public abstract class MeinProcess implements ISocketProcess, IRequestHandler {
    private static Logger logger = Logger.getLogger(MeinProcess.class.getName());
    public static final String STATE_OK = "ok";
    public static final String STATE_ERR = "err";
    protected MeinAuthSocket meinAuthSocket;
    protected Certificate partnerCertificate;
    protected Map<Long, Dobject> requestMap = new ConcurrentHashMap<>();

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
            Dobject deferred = requestMap.get(answerId);
            this.requestMap.remove(answerId);
            if (!msg.getState().equals(STATE_OK)) {
                if (msg.getPayload() != null)
                    deferred.reject((Exception) msg.getPayload());
                else
                    deferred.reject(new Exception("state was: " + msg.getState()));
                return true;
            }
            deferred.check(deserialized);
            return true;
        }
        return false;
    }

    @Override
    public void queueForResponse(MeinRequest request) {
        requestMap.put(request.getRequestId(), request.getDobject());
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + meinAuthSocket.getMeinAuthService().getName();
    }

    public void stop() {
        meinAuthSocket.stop();
    }
}
