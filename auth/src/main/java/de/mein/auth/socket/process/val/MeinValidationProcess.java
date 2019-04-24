package de.mein.auth.socket.process.val;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.*;
import de.mein.auth.data.cached.CachedData;
import de.mein.auth.data.cached.CachedPart;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.MeinService;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinProcess;
import de.mein.auth.socket.process.auth.MeinAuthProcess;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 4/27/16.
 */
@SuppressWarnings("Duplicates")
public class MeinValidationProcess extends MeinProcess {
    private final Long connectedId;
    private final Map<Long, CachedData> cachedForRetrieving = new HashMap<>();
    private final Map<Long, StateMsg> cachedStateMessages = new HashMap<>();
    private final Map<Long, CachedData> cachedForSending = new HashMap<>();


    public static class SendException extends Exception {
        public SendException(String msg) {
            super(msg);
        }
    }

    public MeinValidationProcess(MeinAuthSocket meinAuthSocket, Certificate partnercertificate) {
        super(meinAuthSocket);
        this.meinAuthSocket = meinAuthSocket;
        this.connectedId = partnercertificate.getId().v();
        try {
            this.partnerCertificate = meinAuthSocket.getMeinAuthService().getCertificateManager().getTrustedCertificateById(connectedId);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        super.stop();
    }

    public Long getConnectedId() {
        return connectedId;
    }


    @Override
    public synchronized void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket webSocket) {

        if (!handleCached(deserialized) && !handleAnswer(deserialized)) {
            try {
                if (!handleGetServices(deserialized)) {
                    if (!handleServiceInteraction(deserialized)) {
                        Lok.debug("MeinValidationProcess.onMessageReceived.something exploded here :/");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void requestCachedPart(CachedData cachedData) throws JsonSerializationException, IllegalAccessException {
        CachedRequest cachedRequest = new CachedRequest()
                .setCacheId(cachedData.getCacheId())
                .setPartNumber(cachedData.getNextPartNumber())
                .setServiceUuid(cachedData.getServiceUuid());
        send(cachedRequest);
    }

    private void handleCachedFinished(CachedData cachedData) throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        cachedData.toDisk();
        StateMsg stateMsg = cachedStateMessages.get(cachedData.getCacheId());
        this.onMessageReceived(stateMsg, meinAuthSocket);
//        handleAnswer(stateMsg);
//        cachedData.cleanUp();
    }

    /**
     * handle if deserialized is cached data. data might be requested or come in spontaneously.
     *
     * @param deserialized
     * @return true if the cached data is still incomplete. if complete let the appropriate method take care of it -> false
     */
    private boolean handleCached(SerializableEntity deserialized) {
        try {
            if (deserialized instanceof CachedData) {
                // first part of cached data arrives
                CachedData receivedData = (CachedData) deserialized;
                CachedData cached = cachedForRetrieving.get(receivedData.getCacheId());
                cached.initPartsMissed(cached.getPartCount());
                handleReceiveCachedPart(receivedData.getPart());
                return true;
            } else if (deserialized instanceof CachedPart) {
                // a further part of cached data arrives
                CachedPart part = (CachedPart) deserialized;
                CachedData cachedData = cachedForRetrieving.get(part.getCacheId());
                cachedData.onReceivedPart(part);
                if (cachedData.isComplete()) {
                    handleCachedFinished(cachedData);
                    return true;
                } else {
                    // still not complete
                    requestCachedPart(cachedData);
                }
                return true;
            } else if (deserialized instanceof StateMsg) {
                StateMsg stateMsg = (StateMsg) deserialized;
                if (stateMsg.getPayload() != null && stateMsg.getPayload() instanceof CachedData) {
                    // get a chacheId first
                    if (stateMsg instanceof MeinResponse) {
                        MeinResponse response = (MeinResponse) stateMsg;
                        // in case someone sent us data we did not ask for
                        if (!requestMap.containsKey(response.getResponseId()))
                            return true;
                    } else if (stateMsg instanceof MeinRequest) {
                        MeinRequest request = (MeinRequest) stateMsg;
                        // in case the request to this service was not allowed
                        if (!isServiceAllowed(request.getServiceUuid()))
                            return true;
                    }
                    // its new. is must get a cache id and folder
                    CachedData cachedData = (CachedData) stateMsg.getPayload();
                    cachedData.setCacheDirectory(meinAuthSocket.getMeinAuthService().getCacheDir());
                    cachedData.initPartsMissed(cachedData.getPartCount());
                    //todo debug
                    if (cachedData.getClass().getSimpleName().startsWith("SyncTask"))
                        Lok.debug("debug  ");
                    if (cachedData.isComplete() && cachedStateMessages.containsKey(cachedData.getCacheId())) {
                        // the message is complete -> nothing to do here
                        return false;
                    }

                    if (cachedData.isComplete()) {
                        // let the other methods work with the payload
                        return false;
                    } else {
                        cachedForRetrieving.put(cachedData.getCacheId(), cachedData);
                        cachedStateMessages.put(cachedData.getCacheId(), stateMsg);
                        cachedData.toDisk();
                        // the answer is not complete. asking for the next part
                        // retrieve the next part
                        requestCachedPart(cachedData);
                        // we took care of it for now.
                        return true;
                    }
                } else {
                    // we don't care
                    return false;
                }
            } else if (deserialized instanceof CachedRequest) {
                CachedRequest cachedRequest = (CachedRequest) deserialized;
                if (isServiceAllowed(cachedRequest.getServiceUuid())) {
                    CachedData cachedData = cachedForSending.get(cachedRequest.getCacheId());
                    CachedPart part = cachedData.getPart(cachedRequest.getPartNumber());
                    send(part);
                }
                return true;
            } else if (deserialized instanceof CachedDoneMessage) {
                CachedDoneMessage cachedDoneMessage = (CachedDoneMessage) deserialized;
                if (isServiceAllowed(cachedDoneMessage.getServiceUuid())) {
                    CachedData cachedData = cachedForSending.remove(cachedDoneMessage.getCacheId());
                    //todo debug
                    if (cachedData == null) {
                        Lok.debug("MeinValidationProcess.handleCached.debug234");
                    }
                    cachedData.cleanUp();
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * reads a {@link CachedPart} and and removes the related {@link CachedData} object from the waiting "list".
     * calls handleServiceInteraction() when done
     *
     * @param cachedPart
     * @throws IllegalAccessException
     * @throws JsonSerializationException
     * @throws IOException
     * @throws InstantiationException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     * @throws SqlQueriesException
     */
    private boolean handleReceiveCachedPart(CachedPart cachedPart) throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException, SqlQueriesException {
        CachedData cached = cachedForRetrieving.get(cachedPart.getCacheId());
        cached.onReceivedPart(cachedPart);
        if (cached.isComplete()) {
            cachedForRetrieving.remove(cached.getCacheId());
            // update the answer we got before and handle it
            StateMsg stateMsg = cachedStateMessages.get(cached.getCacheId());
            stateMsg.setPayLoad(cached);
            return handleAnswer(stateMsg);
        } else {
            // retrieve the next part
            CachedRequest cachedRequest = new CachedRequest()
                    .setCacheId(cached.getCacheId())
                    .setPartNumber(cached.getNextPartNumber())
                    .setServiceUuid(cached.getServiceUuid());
            send(cachedRequest);
            return true;
        }
    }

    private boolean handleServiceInteraction(SerializableEntity deserialized) throws SqlQueriesException {
        if (deserialized instanceof MeinMessage) {
            MeinMessage message = (MeinMessage) deserialized;
            String serviceUuid = message.getServiceUuid();
            if (serviceUuid == null) {
                return handleAnswer(deserialized);
            }
            if (!bootLevelSatisfied(serviceUuid, deserialized)) {
                Lok.error("NOT ALLOWED, LEVEL INSUFFICIENT");
                return true;
            }
            if (isServiceAllowed(serviceUuid)) {
                MeinService meinService = meinAuthSocket.getMeinAuthService().getMeinService(serviceUuid);
                if (deserialized instanceof MeinRequest) {
                    //delegate request to service
                    MeinRequest request = (MeinRequest) deserialized;
                    Request<ServicePayload> validatePromise = new Request<>().setPayload(request.getPayload()).setPartnerCertificate(this.partnerCertificate).setIntent(request.getIntent());

                    ServicePayload payload = request.getPayload();
                    if (payload instanceof CachedData) {
                        Lok.debug("MeinValidationProcess.handleServiceInteraction");
                    }
                    //wrap the answer and send it back
                    validatePromise.done(newPayload -> {
                        MeinResponse response = request.reponse().setPayLoad(newPayload);
                        if (newPayload instanceof CachedData) {
                            CachedData cachedData = (CachedData) newPayload;
                            cachedForSending.put(cachedData.getCacheId(), cachedData);
                        }
                        try {
                            send(response);
                        } catch (Exception e) {
                            e.printStackTrace();
                            handleError(request, e);
                        }
                    }).fail(result -> {
                        handleError(request, result);
                    });
                    try {
                        meinService.handleRequest(validatePromise);
                    } catch (Exception e) {
                        e.printStackTrace();
                        handleError(request, e);
                    }
                    return true;
                } else if (deserialized instanceof MeinMessage) {
                    //delegate message to service
                    MeinMessage meinMessage = (MeinMessage) deserialized;
                    meinService.handleMessage(meinMessage.getPayload(), this.partnerCertificate, meinMessage.getIntent());
                    return true;
                }
            }
        } else if (deserialized instanceof MeinResponse) {
            return handleAnswer(deserialized);
        }
        return false;
    }

    private boolean bootLevelSatisfied(String serviceUuid, SerializableEntity deserialized) {
        return true;
    }

    private void handleError(MeinRequest request, Exception e) {
        MeinResponse response = request.respondError(e);
        try {
            Lok.error("MeinValidationProcess for " + meinAuthSocket.getMeinAuthService().getName() + ".handleError");
            e.printStackTrace();
            send(response);
            Lok.error("MeinValidationProcess for " + meinAuthSocket.getMeinAuthService().getName() + ".handleError.done");
        } catch (JsonSerializationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        }
    }

    private boolean isServiceAllowed(String serviceUuid) throws SqlQueriesException {
        Service service = meinAuthSocket.getMeinAuthService().getDatabaseManager().getServiceByUuid(serviceUuid);
        if (service == null) {
            //todo debug
            Lok.debug("MeinValidationProcess.isServiceAllowed.debug");
        }
        return meinAuthSocket.getMeinAuthService().getDatabaseManager().isApproved(partnerCertificate.getId().v(), service.getId().v());
    }

    private boolean handleGetServices(SerializableEntity deserialized) throws JsonSerializationException, IllegalAccessException, SqlQueriesException {
        if (deserialized instanceof MeinRequest) {
            MeinRequest request = (MeinRequest) deserialized;
            if (request.getServiceUuid().equals(MeinStrings.SERVICE_NAME)
                    && request.getIntent().equals(MeinStrings.msg.INTENT_GET_SERVICES)) {
                MeinResponse response = request.reponse();
                MeinAuthProcess.addAllowedServicesJoinTypes(meinAuthSocket.getMeinAuthService(), partnerCertificate, response);
                send(response);
                return true;
            }
        }
        return false;
    }

//    private void registerCached(MeinRequest request) {
//        ServicePayload payload = request.getPayload();
//        if (payload instanceof CachedData) {
//            CachedData cachedData = (CachedData) payload;
//            cachedData.setCacheId(request.getRequestId());
//            //cachedData.setServiceUuid(request.getServiceUuid());
//            cachedForRetrieving.put(request.getRequestId(), cachedData);
//        }
//    }

    private void registerSendCached(MeinRequest request) {
        ServicePayload payload = request.getPayload();
        if (payload != null && payload instanceof CachedData) {
            CachedData cachedData = (CachedData) payload;
            if (cachedData.getCacheId() == null)
                Lok.error("you are about to send an instance of CachedData without assigning it a chacheId!");
            cachedForSending.put(cachedData.getCacheId(), cachedData);
        }
    }

    /**
     * locks until you received either a response or an error.<br>
     * useful if you do communications with a few more requests and want these to run on the same worker thread. <br>
     * lock on the {@link LockedRequest} to wait for a result.
     *
     * @param serviceUuid
     * @param intent
     * @param payload
     * @return
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     */
    public LockedRequest requestLocked(String serviceUuid, String intent, ServicePayload payload) throws JsonSerializationException, IllegalAccessException {
        LockedRequest promise = new LockedRequest();
        MeinRequest request = new MeinRequest(serviceUuid, intent);
        if (payload != null) {
            request.setPayLoad(payload);
        }
        registerSendCached(request);
        request.setRequestHandler(this).queue();
        request.getAnswerDeferred().done(result -> {
            StateMsg response = (StateMsg) result;
            promise.setResponse(response.getPayload());
            promise.unlock();
        }).fail(result -> {
            promise.setException(result);
            promise.unlock();
        });
        queueForResponse(request);
        send(request);
        promise.lock();
        return promise;
    }


    public Request request(String serviceUuid, String intent, ServicePayload payload) throws JsonSerializationException, IllegalAccessException {
        meinAuthSocket.getMeinAuthService().getPowerManager().wakeLock(this);
        Request promise = new Request();
        MeinRequest request = new MeinRequest(serviceUuid, intent);
        if (payload != null) {
            request.setPayLoad(payload);
        }
        registerSendCached(request);
        request.setRequestHandler(this).queue();
        request.getAnswerDeferred().done(result -> {
            StateMsg response = (StateMsg) result;
            promise.resolve(response.getPayload());
            meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(this);
        }).fail(result -> {
            if (validateFail(result)) {
                if (!promise.isRejected())
                    promise.reject(result);
            } else {
                if (!promise.isRejected())
                    promise.reject(result);
            }
            meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(this);
        });
        // todo this line should be redundant, see "request.setRequestHandler(this).queue();" above
        queueForResponse(request);
        send(request);
        return promise;
    }


//    public Request request(String serviceUuid, String intent, SerializableEntity payload) throws JsonSerializationException, IllegalAccessException {
//        List<SerializableEntity> payloads = new ArrayList<>();
//        if (payload != null)
//            payloads.add(payload);
//        return requestWithList(serviceUuid, intent, payloads);
//
//    }

    private boolean validateFail(Exception result) {
        return false;
    }

    private boolean validateThingy(SerializableEntity result) {
        return false;
    }

    public void message(String serviceUuid, String intent, ServicePayload payload) throws JsonSerializationException, IllegalAccessException {
        MeinMessage message = new MeinMessage(serviceUuid, intent).setPayLoad(payload);
        send(message);
    }

    public Certificate getPartnerCertificate() {
        return partnerCertificate;
    }

    public String getAddressString() {
        return meinAuthSocket.getAddressString();
    }


//    public Request requestWithList(String serviceName, String intent) throws JsonSerializationException, IllegalAccessException {
//        return requestWithList(serviceName, intent, new ArrayList<>());
//    }
}
