package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.InsufficientBootLevelException;
import de.mel.auth.MelStrings;
import de.mel.auth.data.*;
import de.mel.auth.data.cached.CachedData;
import de.mel.auth.data.cached.CachedInitializer;
import de.mel.auth.data.cached.CachedPart;
import de.mel.auth.data.cached.CachedPartOlde;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.Service;
import de.mel.auth.service.MelService;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.exceptions.MelJsonException;
import de.mel.sql.SqlQueriesException;

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
public class MelValidationProcess extends MelProcess {
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
        if (melAuthSocket != null) {
            return (incoming ? "incoming " : "outgoing ") + melAuthSocket.getAddressString() + (melAuthSocket.isStopped() ? " stopped" : " running");
        }
        return super.toString();
    }

    public boolean isClosed() {
        return melAuthSocket.isStopped();
    }

    public MelAuthSocket getMelAuthSocket() {
        return melAuthSocket;
    }

    public static class SendException extends Exception {
        public SendException(String msg) {
            super(msg);
        }
    }

    public MelValidationProcess(MelAuthSocket melAuthSocket, Certificate partnercertificate, boolean incoming) {
        super(melAuthSocket);
        this.incoming = incoming;
        this.melAuthSocket = melAuthSocket;
        this.connectedId = partnercertificate.getId().v();
        try {
            this.partnerCertificate = melAuthSocket.getMelAuthService().getCertificateManager().getTrustedCertificateById(connectedId);
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
    public synchronized void onMessageReceived(SerializableEntity deserialized, MelAuthSocket webSocket) throws IOException, MelJsonException {
        if (!handleCached(deserialized) && !handleAnswer(deserialized)) {
            try {
                if (!handleGetServices(deserialized)) {
                    if (!handleServiceInteraction(deserialized)) {
                        Lok.debug("MelValidationProcess.onMessageReceived.something exploded here :/");
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
    private boolean handleCached(SerializableEntity deserialized) throws MelJsonException, IOException {

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
                initializer.setCacheDir(melAuthSocket.getMelAuthService().getCacheDir());
                if (!initializer.isComplete()) {
                    initializer.initPartsMissed();
                    if (initializer.getPart() != null) {
                        initializer.onReceivedPart(initializer.getPart());
                    }
                }
                if (initializer.isComplete()) {
                    // clean up and deal with the message
                    cachedForRequesting.remove(initializer.getCacheId());
                    cachedStateMessages.remove(initializer.getCacheId());
                    send(new CachedDoneMessage().setCacheId(initializer.getCacheId()));
//                    onMessageReceived(deserialized, melAuthSocket);
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
                        onMessageReceived(stateMsg, melAuthSocket);
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

    private boolean handleServiceInteraction(SerializableEntity deserialized) throws SqlQueriesException {
        if (deserialized instanceof MelMessage) {
            MelMessage message = (MelMessage) deserialized;
            ServicePayload payload = message.getPayload();
            String serviceUuid = message.getServiceUuid();
            // no serviceuuid could be an answer to a request
            if (serviceUuid == null) {
                return handleAnswer(deserialized);
            }
            MelService melService = melAuthSocket.getMelAuthService().getMelService(serviceUuid);
            if (melService == null) {
                if (message instanceof MelRequest) {
                    MelRequest request = (MelRequest) message;
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
                if (message instanceof MelRequest) {
                    MelRequest melRequest = (MelRequest) message;
                    melRequest.getAnswerDeferred().reject(new InsufficientBootLevelException());
                }
                return true;
            }
            if (isServiceAllowed(serviceUuid)) {
                if (deserialized instanceof MelRequest) {
                    MelRequest melRequest = (MelRequest) deserialized;
                    // wrap it, hand it over to the service and send results back
                    Request<ServicePayload> request4Service = new Request<>().setPayload(melRequest.getPayload()).setPartnerCertificate(this.partnerCertificate).setServiceUuid(serviceUuid);
                    if (payload instanceof CachedData) {
                        Lok.debug("MelValidationProcess.handleServiceInteraction");
                    }
                    //wrap the answer and send it back
                    request4Service.done(newPayload -> {
                        MelResponse response = melRequest.reponse().setPayLoad(newPayload);
                        if (newPayload instanceof CachedInitializer) {
                            CachedInitializer cachedData = (CachedInitializer) newPayload;
                            cachedForSending.put(cachedData.getCacheId(), cachedData);
                        }
                        try {
                            send(response);
                        } catch (Exception e) {
                            e.printStackTrace();
                            handleError(melRequest, e);
                        }
                    }).fail(result -> {
                        handleError(melRequest, result);
                    });
                    try {
                        melService.handleRequest(request4Service);
                    } catch (Exception e) {
                        e.printStackTrace();
                        handleError(melRequest, e);
                    }
                    return true;
                } else if (deserialized instanceof MelMessage) {
                    //delegate message to service
                    MelMessage melMessage = (MelMessage) deserialized;
                    melService.handleMessage(melMessage.getPayload(), this.partnerCertificate);
                    return true;
                }
                // clean up if it was cached
                if (deserialized instanceof StateMsg) {
                    StateMsg stateMsg = (StateMsg) deserialized;
                    if (stateMsg.getPayload() instanceof CachedInitializer) {
                        CachedInitializer initializer = (CachedInitializer) stateMsg.getPayload();
                        initializer.cleanUp();
                    }
                }
            }
        } else if (deserialized instanceof MelResponse) {
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
        MelService melService = melAuthSocket.getMelAuthService().getMelService(serviceUuid);
        if (payload != null) {
            try {
                Class<? extends ServicePayload> payloadClass = payload.getClass();
                Constructor<? extends ServicePayload> constructor = payloadClass.getDeclaredConstructor();
                ServicePayload newInstance = constructor.newInstance();
                return melService.getBootLevel().greaterOrEqual(newInstance.getLevel());
            } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private void handleError(MelRequest request, Exception e) {
        Lok.debug("handling error on " + melAuthSocket.getMelAuthService().getName());
        MelResponse response = request.respondError(e);
        N.oneLine(() -> send(response));
    }

    private boolean isServiceAllowed(String serviceUuid) throws SqlQueriesException {
        Service service = melAuthSocket.getMelAuthService().getDatabaseManager().getServiceByUuid(serviceUuid);
        if (service == null) {
            //todo debug
            Lok.debug("MelValidationProcess.isServiceAllowed.debug");
        }
        return melAuthSocket.getMelAuthService().getDatabaseManager().isApproved(partnerCertificate.getId().v(), service.getId().v());
    }

    private boolean handleGetServices(SerializableEntity deserialized) throws
            JsonSerializationException, IllegalAccessException, SqlQueriesException {
        if (deserialized instanceof MelRequest) {
            MelRequest request = (MelRequest) deserialized;
            ServicePayload payload = request.getPayload();
            if (request.getServiceUuid().equals(MelStrings.SERVICE_NAME) && payload != null && payload.hasIntent(MelStrings.msg.INTENT_GET_SERVICES)) {
                MelResponse response = request.reponse();
                MelAuthProcess.addAllowedServicesJoinTypes(melAuthSocket.getMelAuthService(), partnerCertificate, response);
                send(response);
                return true;
            }
        }
        return false;
    }

//    private void registerCached(MelRequest request) {
//        ServicePayload payload = request.getPayload();
//        if (payload instanceof CachedData) {
//            CachedData cachedData = (CachedData) payload;
//            cachedData.setCacheId(request.getRequestId());
//            //cachedData.setServiceUuid(request.getServiceUuid());
//            cachedForRetrieving.put(request.getRequestId(), cachedData);
//        }
//    }


    protected void send(SerializableEntity serializableEntity) throws
            JsonSerializationException {
        if (serializableEntity instanceof MelMessage) {
            ServicePayload payload = ((MelMessage) serializableEntity).getPayload();
            if (payload instanceof CachedInitializer) {
                CachedInitializer initializer = (CachedInitializer) payload;
                cachedForSending.put(initializer.getCacheId(), initializer);
            }
        }
        super.send(serializableEntity);
    }

    public Request request(String serviceUuid, ServicePayload payload) throws
            JsonSerializationException {
        melAuthSocket.getMelAuthService().getPowerManager().wakeLock(MelValidationProcess.this);
        Request promise = new Request().setServiceUuid(serviceUuid);
        MelRequest request = new MelRequest(serviceUuid, null);
        if (payload != null) {
            request.setPayLoad(payload);
        }
        request.setRequestHandler(this).queue();
        request.getAnswerDeferred().done(result -> {
            StateMsg response = (StateMsg) result;
            promise.resolve(response.getPayload());
            melAuthSocket.getMelAuthService().getPowerManager().releaseWakeLock(MelValidationProcess.this);
        }).fail(result -> {
            if (validateFail(result)) {
                try {
                    if (!promise.isRejected()) {
                        promise.reject(result);
                    }
                } finally {
                    melAuthSocket.getMelAuthService().getPowerManager().releaseWakeLock(MelValidationProcess.this);
                }
            } else {
                try {

                    if (!promise.isRejected()) {
                        promise.reject(result);
                    }
                } finally {
                    melAuthSocket.getMelAuthService().getPowerManager().releaseWakeLock(MelValidationProcess.this);
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

    public void message(String serviceUuid, ServicePayload payload) throws
            JsonSerializationException, IllegalAccessException {
        MelMessage message = new MelMessage(serviceUuid, null).setPayLoad(payload);
        send(message);
    }

    public Certificate getPartnerCertificate() {
        return partnerCertificate;
    }

    public String getAddressString() {
        return melAuthSocket.getAddressString();
    }


//    public Request requestWithList(String serviceName, String intent) throws JsonSerializationException, IllegalAccessException {
//        return requestWithList(serviceName, intent, new ArrayList<>());
//    }
}
