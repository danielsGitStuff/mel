package de.mein.auth.socket;

import de.mein.auth.MeinStrings;
import de.mein.auth.data.*;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
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
    private ReentrantLock requestLock = new ReentrantLock();
    protected Map<Long, MeinRequest> requestMap = new ConcurrentHashMap<>();
    private boolean stopped = false;


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
            MeinRequest deferred = requestMap.get(answerId);
            this.requestMap.remove(answerId);
            if (!msg.getState().equals(MeinStrings.msg.STATE_OK)) {
                if (msg.getPayload() != null)
                    deferred.getDobject().reject((Exception) msg.getPayload());
                else
                    deferred.getDobject().reject(new Exception("state was: " + msg.getState()));
                return true;
            }
            deferred.getDobject().check(deserialized);
            return true;
        }
        return false;
    }

    @Override
    public void queueForResponse(MeinRequest request) {
        requestLock.lock();
        requestMap.put(request.getRequestId(), request);
        requestLock.unlock();
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + meinAuthSocket.getMeinAuthService().getName();
    }

    public void stop() {
        requestLock.lock();
        stopped = true;
        for (MeinRequest request : requestMap.values()) {
            request.getDobject().reject(new MeinValidationProcess.SendException(getClass().getSimpleName() + ".stop() was called"));
        }
        requestLock.unlock();
        meinAuthSocket.stop();
    }

    private void closeRequest(MeinRequest request) {
        requestLock.lock();
        requestMap.remove(request.getRequestId());
        requestLock.unlock();
    }

    public abstract void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket webSocket);

}
