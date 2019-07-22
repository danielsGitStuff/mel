package de.mein.auth.socket;

import de.mein.Lok;
import de.mein.auth.InsufficientBootLevelException;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.*;
import de.mein.auth.data.cached.CachedData;
import de.mein.auth.data.cached.CachedInitializer;
import de.mein.auth.data.cached.CachedPart;
import de.mein.auth.data.cached.CachedPartOlde;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.MeinService;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.exceptions.MeinJsonException;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.impl.DeferredObject;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 4/27/16.
 */
@SuppressWarnings("Duplicates")
public class MeinValidationProcess extends MeinProcess {
    private final Long connectedId;
    //    private final Map<Long, CachedData> cachedForRetrieving = new HashMap<>();
//    private final Map<Long, StateMsg> cachedStateMessages = new HashMap<>();
//    private final Map<Long, CachedData> cachedForSending = new HashMap<>();
    private final Map<Long, CachedInitializer> cachedForSending = new HashMap<>();
    private final Map<Long, CachedInitializer> cachedForRequesting = new HashMap<>();
    private final Map<Long, StateMsg> cachedStateMessages = new HashMap<>();
    private final boolean incoming;

    @Override
    public String toString() {
        if (meinAuthSocket != null) {
            return (incoming ? "incoming " : "outgoing ") + meinAuthSocket.getAddressString() + (meinAuthSocket.isStopped() ? " stopped" : " running");
        }
        return super.toString();
    }

    public boolean isClosed() {
        return meinAuthSocket.isStopped();
    }

    public MeinAuthSocket getMeinAuthSocket() {
        //todo debug
        return meinAuthSocket;
    }

    public static class SendException extends Exception {
        public SendException(String msg) {
            super(msg);
        }
    }

    public MeinValidationProcess(MeinAuthSocket meinAuthSocket, Certificate partnercertificate, boolean incoming) {
        super(meinAuthSocket);
        this.incoming = incoming;
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
    public synchronized void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket webSocket) throws IOException, MeinJsonException {

        if (!handleCached(deserialized) && !handleAnswer(deserialized)) {
            try {
                if (!handleGetServices(deserialized)) {
                    if (!handleServiceInteraction((StateMsg) deserialized)) {
                        Lok.debug("MeinValidationProcess.onMessageReceived.something exploded here :/");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * If deserialized is some kind of cached data we care about it here.
     * Except: the {@link CachedInitializer} is already complete. In this case we return false and let the subsequent methods deal with it.
     * A cached data transfer always starts with a {@link CachedInitializer} which might be followed by several {@link CachedPart}s.
     * In order to get all of these parts {@link CachedRequest}s are sent to the partner and answered with {@link CachedPart}s.
     * When the {@link CachedInitializer} is complete a {@link CachedDoneMessage} is sent to the partner, so he can clean up.
     * Note: this method deals with caching on both sides.
     */
    private boolean handleCached(SerializableEntity deserialized) throws MeinJsonException, IOException {

        // if chached stuff starts then as a StateMsg
        if (deserialized instanceof StateMsg) {
            StateMsg stateMsg = (StateMsg) deserialized;
            if (stateMsg.getPayload() instanceof CachedInitializer) {
                // cached data incoming.
                CachedInitializer initializer = (CachedInitializer) stateMsg.getPayload();
                cachedForRequesting.put(initializer.getCacheId(), initializer);
                //store the StateMsg
                cachedStateMessages.put(initializer.getCacheId(), stateMsg);
                // set it up correctly
                initializer.setCacheDir(meinAuthSocket.getMeinAuthService().getCacheDir());
                initializer.initPartsMissed();
                if (initializer.getPart() != null) {
                    initializer.onReceivedPart(initializer.getPart());
                }
                if (initializer.isComplete()) {
                    // clean up and deal with the message
                    cachedForRequesting.remove(initializer.getCacheId());
                    cachedStateMessages.remove(initializer.getCacheId());
                    send(new CachedDoneMessage().setCacheId(initializer.getCacheId()));
//                    onMessageReceived(deserialized, meinAuthSocket);
//                    initializer.cleanUp();
                    return false;
                } else {
                    // ask for more
                    send(new CachedRequest().setPartNumber(initializer.getNextPartNumber()).setCacheId(initializer.getCacheId()));
                    return true;
                }
            }
        } else if (deserialized instanceof AbstractCachedMessage) {
            AbstractCachedMessage cachedMessage = (AbstractCachedMessage) deserialized;
            Long cacheId = cachedMessage.getCacheId();
            if (cachedMessage instanceof CachedRequest) {
                // partner asks for a cached part
                if (cachedForSending.containsKey(cacheId)) {
                    CachedRequest cachedRequest = (CachedRequest) cachedMessage;
                    send(cachedForSending.get(cacheId).getPart(cachedRequest.getPartNumber()));
                } else {
                    Lok.error("INVALID CACHE ID REQUESTED: " + cacheId);
                }
            } else if (cachedMessage instanceof CachedDoneMessage) {
                // partner has got everything he needs. we can free up our space here.
                CachedDoneMessage cachedDoneMessage = (CachedDoneMessage) cachedMessage;
                if (cachedForSending.containsKey(cacheId)) {
                    CachedInitializer initializer = cachedForSending.remove(cacheId);
                    initializer.cleanUp();
                } else {
                    Lok.error("INVALID CACHE ID REQUESTED: " + cacheId);
                }
            } else if (cachedMessage instanceof CachedPart) {
                CachedPart cachedPart = (CachedPart) cachedMessage;
                if (cachedForRequesting.containsKey(cacheId)) {
                    CachedInitializer initializer = cachedForRequesting.get(cacheId);
                    initializer.onReceivedPart(cachedPart);
                    if (initializer.isComplete()) {
                        cachedForRequesting.remove(cacheId);
                        StateMsg stateMsg = cachedStateMessages.get(cacheId);
                        send(new CachedDoneMessage().setCacheId(cacheId));
                        onMessageReceived(stateMsg, meinAuthSocket);
                        return false;
                    } else {
                        send(new CachedRequest().setCacheId(cacheId).setPartNumber(initializer.getNextPartNumber()));
                    }
                } else {
                    Lok.error("INVALID CACHE ID REQUESTED: " + cacheId);
                }
            }
            return true;
        }
        return false;
    }

    private void requestCachedPart(CachedData cachedData) throws JsonSerializationException, IllegalAccessException {
        CachedRequest cachedRequest = new CachedRequest()
                .setCacheId(cachedData.getCacheId())
                .setPartNumber(cachedData.getNextPartNumber());
        send(cachedRequest);
    }

//    private void handleCachedFinished(CachedData cachedData) throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, NoSuchMethodException, InvocationTargetException {
//        cachedData.toDisk();
//        StateMsg stateMsg = cachedStateMessages.get(cachedData.getCacheId());
//        this.onMessageReceived(stateMsg, meinAuthSocket);
////        handleAnswer(stateMsg);
////        cachedData.cleanUp();
//    }

    /**
     * handle if deserialized is cached data. data might be requested or come in spontaneously.
     *
     * @param deserialized
     * @return true if the cached data is still incomplete. if complete let the appropriate method take care of it -> false
     */
//    private boolean handleCached(SerializableEntity deserialized) {
//        try {
//            if (deserialized instanceof CachedData) {
//                // first part of cached data arrives
//                CachedData receivedData = (CachedData) deserialized;
//                CachedData cached = cachedForRetrieving.get(receivedData.getCacheId());
//                cached.initPartsMissed(cached.getPartCount());
//                handleReceiveCachedPart(receivedData.getPart());
//                return true;
//            } else if (deserialized instanceof CachedPartOlde) {
//                // a further part of cached data arrives
//                CachedPartOlde part = (CachedPartOlde) deserialized;
//                CachedData cachedData = cachedForRetrieving.get(part.getCacheId());
//                cachedData.onReceivedPart(part);
//                if (cachedData.isComplete()) {
//                    handleCachedFinished(cachedData);
//                    return true;
//                } else {
//                    // still not complete
//                    requestCachedPart(cachedData);
//                }
//                return true;
//            } else if (deserialized instanceof StateMsg) {
//                StateMsg stateMsg = (StateMsg) deserialized;
//                if (stateMsg.getPayload() != null && stateMsg.getPayload() instanceof CachedData) {
//                    // get a chacheId first
//                    if (stateMsg instanceof MeinResponse) {
//                        MeinResponse response = (MeinResponse) stateMsg;
//                        // in case someone sent us data we did not ask for
//                        if (!requestMap.containsKey(response.getResponseId()))
//                            return true;
//                    } else if (stateMsg instanceof MeinRequest) {
//                        MeinRequest request = (MeinRequest) stateMsg;
//                        // in case the request to this service was not allowed
//                        if (!isServiceAllowed(request.getServiceUuid()))
//                            return true;
//                    }
//                    // its new. is must get a cache id and folder
//                    CachedData cachedData = (CachedData) stateMsg.getPayload();
//                    if (cachedData.isComplete() && cachedStateMessages.containsKey(cachedData.getCacheId())) {
//                        // the message is complete -> nothing to do here
//                        return false;
//                    } else {
//                        MeinService service = meinAuthSocket.getMeinAuthService().getMeinService(cachedData.getServiceUuid());
//                        if (service!=null) {
//                            cachedData.setCacheDirectory(service.getCacheDirectory());
//                            cachedData.initPartsMissed(cachedData.getPartCount());
//                        }else {
//                            Lok.error("got some cached data but no service!");
//                        }
//                    }
//
//                    //todo debug
//                    if (cachedStateMessages.containsKey(cachedData.getCacheId()))
//                        Lok.debug("debug");
//
//                    if (cachedData.isComplete()) {
//                        // let the other methods work with the payload
//                        return false;
//                    } else {
//                        cachedForRetrieving.put(cachedData.getCacheId(), cachedData);
//                        cachedStateMessages.put(cachedData.getCacheId(), stateMsg);
//                        cachedData.toDisk();
//                        // the answer is not complete. asking for the next part
//                        // retrieve the next part
//                        requestCachedPart(cachedData);
//                        // we took care of it for now.
//                        return true;
//                    }
//                } else {
//                    // we don't care
//                    return false;
//                }
//            } else if (deserialized instanceof CachedRequest) {
//
//                CachedRequest cachedRequest = (CachedRequest) deserialized;
//                CachedData alreadyCached = cachedForSending.get(cachedRequest.getCacheId());
//                if (isServiceAllowed(alreadyCached.getServiceUuid())) {
//                    CachedData cachedData = cachedForSending.get(cachedRequest.getCacheId());
//                    CachedPartOlde part = cachedData.getPart(cachedRequest.getPartNumber());
//                    send(part);
//                }
//                return true;
//            } else if (deserialized instanceof CachedDoneMessage) {
//                CachedDoneMessage cachedDoneMessage = (CachedDoneMessage) deserialized;
//                if (isServiceAllowed(cachedDoneMessage.getServiceUuid())) {
//                    CachedData cachedData = cachedForSending.remove(cachedDoneMessage.getCacheId());
//                    cachedData.cleanUp();
//                }
//                return true;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return false;
//    }

    /**
     * reads a {@link CachedPartOlde} and and removes the related {@link CachedData} object from the waiting "list".
     * calls handleServiceInteraction() when done
     *
     * @throws JsonSerializationException
     * @throws IOException
     * @throws InvocationTargetException
     * @throws SqlQueriesException
     */
//    private boolean handleReceiveCachedPart(CachedPartOlde cachedPart) throws IllegalAccessException, JsonSerializationException, IOException, InstantiationException, InvocationTargetException, NoSuchMethodException, SqlQueriesException {
//        CachedData cached = cachedForRetrieving.get(cachedPart.getCacheId());
//        cached.onReceivedPart(cachedPart);
//        if (cached.isComplete()) {
//            cachedForRetrieving.remove(cached.getCacheId());
//            // update the answer we got before and handle it
//            StateMsg stateMsg = cachedStateMessages.get(cached.getCacheId());
//            stateMsg.setPayLoad(cached);
//            return handleAnswer(stateMsg);
//        } else {
//            // retrieve the next part
//            CachedRequest cachedRequest = new CachedRequest()
//                    .setCacheId(cached.getCacheId())
//                    .setPartNumber(cached.getNextPartNumber())
//                    .setServiceUuid(cached.getServiceUuid());
//            send(cachedRequest);
//            return true;
//        }
//    }
    private boolean handleServiceInteraction(StateMsg deserialized) throws SqlQueriesException {
        if (deserialized instanceof MeinMessage) {
            MeinMessage message = (MeinMessage) deserialized;
            ServicePayload payload = message.getPayload();
            String serviceUuid = message.getServiceUuid();
            // no serviceuuid could be an answer to a request
            if (serviceUuid == null) {
                return handleAnswer(deserialized);
            }
            MeinService meinService = meinAuthSocket.getMeinAuthService().getMeinService(serviceUuid);
            if (meinService == null) {
                if (message instanceof MeinRequest) {
                    MeinRequest request = (MeinRequest) message;
                    request.getAnswerDeferred().reject(new ResponseException("service not available"));
                } else {
                    Lok.debug("msg rejected");
                }
                return true;
            }
            if (!bootLevelSatisfied(serviceUuid, payload)) {
                Lok.error("NOT ALLOWED, LEVEL INSUFFICIENT");
                // if a request comes along that requires a higher boot level that the service has not reached yet,
                // this is the place to respond exactly that.
                if (message instanceof MeinRequest) {
                    MeinRequest meinRequest = (MeinRequest) message;
                    meinRequest.getAnswerDeferred().reject(new InsufficientBootLevelException());
                }
                return true;
            }
            if (isServiceAllowed(serviceUuid)) {
                if (deserialized instanceof MeinRequest) {
                    MeinRequest meinRequest = (MeinRequest) deserialized;
                    // wrap it, hand it over to the service and send results back
                    Request<ServicePayload> request4Service = new Request<>().setPayload(meinRequest.getPayload()).setPartnerCertificate(this.partnerCertificate).setServiceUuid(serviceUuid);
                    if (payload instanceof CachedData) {
                        Lok.debug("MeinValidationProcess.handleServiceInteraction");
                    }
                    //wrap the answer and send it back
                    request4Service.done(newPayload -> {
                        MeinResponse response = meinRequest.reponse().setPayLoad(newPayload);
                        if (newPayload instanceof CachedInitializer) {
                            CachedInitializer cachedData = (CachedInitializer) newPayload;
                            cachedForSending.put(cachedData.getCacheId(), cachedData);
                        }
                        try {
                            send(response);
                        } catch (Exception e) {
                            e.printStackTrace();
                            handleError(meinRequest, e);
                        }
                    }).fail(result -> {
                        handleError(meinRequest, result);
                    });
                    try {
                        meinService.handleRequest(request4Service);
                    } catch (Exception e) {
                        e.printStackTrace();
                        handleError(meinRequest, e);
                    }
                    return true;
                } else if (deserialized instanceof MeinMessage) {
                    //delegate message to service
                    MeinMessage meinMessage = (MeinMessage) deserialized;
                    meinService.handleMessage(meinMessage.getPayload(), this.partnerCertificate);
                    return true;
                }
                // clean up if it was cached
                if (deserialized.getPayload() instanceof CachedInitializer){
                    CachedInitializer initializer = (CachedInitializer) deserialized.getPayload();
                    initializer.cleanUp();
                }
            }
        } else if (deserialized instanceof MeinResponse) {
            return handleAnswer(deserialized);
        }
        return false;
    }

    /**
     * Check whether the service has already reached the boot level required by the payload.
     * The level is determined by creating a new instance of the payload that you got here and reading that.
     *
     * @param serviceUuid uuid of the service
     * @param payload     {@link ServicePayload} that has a required boot level
     * @return
     */
    private boolean bootLevelSatisfied(String serviceUuid, ServicePayload payload) {
        MeinService meinService = meinAuthSocket.getMeinAuthService().getMeinService(serviceUuid);
        if (payload != null) {
            try {
                Class<? extends ServicePayload> payloadClass = payload.getClass();
                Constructor<? extends ServicePayload> constructor = payloadClass.getDeclaredConstructor();
                ServicePayload newInstance = constructor.newInstance();
                return meinService.getBootLevel().greaterOrEqual(newInstance.getLevel());
            } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private void handleError(MeinRequest request, Exception e) {
        Lok.debug("handling error on " + meinAuthSocket.getMeinAuthService().getName());
        MeinResponse response = request.respondError(e);
        N.oneLine(() -> send(response));
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
            ServicePayload payload = request.getPayload();
            if (request.getServiceUuid().equals(MeinStrings.SERVICE_NAME) && payload != null && payload.hasIntent(MeinStrings.msg.INTENT_GET_SERVICES)) {
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


    protected void send(SerializableEntity serializableEntity) throws JsonSerializationException {
        if (serializableEntity instanceof MeinMessage) {
            ServicePayload payload = ((MeinMessage) serializableEntity).getPayload();
            if (payload instanceof CachedInitializer) {
                CachedInitializer initializer = (CachedInitializer) payload;
                cachedForSending.put(initializer.getCacheId(), initializer);
            }
        }
        super.send(serializableEntity);
    }

    public Request request(String serviceUuid, ServicePayload payload) throws JsonSerializationException {
        meinAuthSocket.getMeinAuthService().getPowerManager().wakeLock(MeinValidationProcess.this);
        Request promise = new Request().setServiceUuid(serviceUuid);
        MeinRequest request = new MeinRequest(serviceUuid, null);
        if (payload != null) {
            request.setPayLoad(payload);
        }
        request.setRequestHandler(this).queue();
        request.getAnswerDeferred().done(result -> {
            StateMsg response = (StateMsg) result;
            promise.resolve(response.getPayload());
            meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(MeinValidationProcess.this);
        }).fail(result -> {
            if (validateFail(result)) {
                try {
                    if (!promise.isRejected()) {
                        promise.reject(result);
                    }
                } finally {
                    meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(MeinValidationProcess.this);
                }
            } else {
                try {

                    if (!promise.isRejected()) {
                        promise.reject(result);
                    }
                } finally {
                    meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(MeinValidationProcess.this);
                }
            }
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

    public void message(String serviceUuid, ServicePayload payload) throws JsonSerializationException, IllegalAccessException {
        MeinMessage message = new MeinMessage(serviceUuid, null).setPayLoad(payload);
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
