package de.mel.auth.socket;

import de.mel.auth.MelStrings;
import de.mel.auth.data.*;
import de.mel.auth.data.db.Certificate;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.exceptions.MelJsonException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import fun.with.promise.PromiseImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



/**
 * MelProcesses handle everything that is read on the MelAuthSocket.
 * There are several implementations of who care about registering, authentication and validating incoming and outgoing connections.
 */
public abstract class MelProcess implements IRequestHandler {
    protected MelAuthSocket melAuthSocket;
    protected Certificate partnerCertificate;
    protected Map<Long,MelRequest> requestMap = new ConcurrentHashMap<>();
    /**
     * why the socket was closed, null while it is open. Set before the pending requests are rejected.
     */
    protected volatile CloseReason closeReason;

    public MelProcess(MelAuthSocket melAuthSocket) {
        this.melAuthSocket = melAuthSocket;
    }


    protected void send(SerializableEntity melMessage) throws JsonSerializationException {
        if(melMessage instanceof MelRequest){
            MelRequest request = (MelRequest) melMessage;
            request.startTimeout(melAuthSocket.getMelAuthService());
        }
        String json = SerializableEntitySerializer.serialize(melMessage);
//       Lok.debug(melAuthSocket.getMelAuthService().getName() + ".send: " + json);
        melAuthSocket.send(json);
    }


    public void removeThyself() {
        //melAuthService.onProcessDone(this);
        melAuthSocket.close(CloseReason.SHUTDOWN);
    }

    protected boolean handleAnswer(SerializableEntity deserialized) {
        Long answerId = null;
        if (deserialized instanceof MelRequest) {
            answerId = ((MelRequest) deserialized).getAnswerId();
        } else if (deserialized instanceof MelResponse) {
            answerId = ((MelResponse) deserialized).getResponseId();
        }
        if (answerId != null && this.requestMap.containsKey(answerId)) {
            StateMsg msg = (StateMsg) deserialized;
            MelRequest request = requestMap.remove(answerId);
            request.stopTimeout();
            PromiseImpl<SerializableEntity, ResponseException> deferred = request.getAnswerDeferred();
            if (!msg.getState().equals(MelStrings.msg.STATE_OK)) {
                if (msg.getException() == null)
                    deferred.resolveError(new ResponseException("state was: " + msg.getState()));
                else
                    deferred.resolveError(msg.getException());
                return true;
            }
            deferred.resolveOk(deserialized);
            return true;
        }
        return false;
    }

    @Override
    public void queueForResponse(MelRequest request) {
        requestMap.put(request.getRequestId(), request);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + melAuthSocket.getMelAuthService().getName();
    }

    public void stop() {
        melAuthSocket.close(CloseReason.SHUTDOWN);
        melAuthSocket.getMelAuthService().getPowerManager().releaseWakeLock(this);
    }

    public abstract void onMessageReceived(SerializableEntity deserialized, MelAuthSocket webSocket) throws IOException, MelJsonException;

    /**
     * Called once, when the socket is closed, after everything that was received before the end has been handled.
     * Requests that wait for an answer will not get one any more.
     */
    public void onSocketClosed(CloseReason reason, boolean remote) {
        closeReason = reason;
        for (MelRequest request : new ArrayList<>(requestMap.values())) {
            if (requestMap.remove(request.getRequestId(), request)) {
                request.stopTimeout();
                if (request.getAnswerDeferred().isPending())
                    request.getAnswerDeferred().resolveError(new ResponseException("connection closed: " + reason));
            }
        }
    }
}
