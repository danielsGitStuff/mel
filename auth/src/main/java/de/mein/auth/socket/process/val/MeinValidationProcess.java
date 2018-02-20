package de.mein.auth.socket.process.val;

import de.mein.auth.MeinStrings;
import de.mein.auth.data.*;
import de.mein.auth.data.cached.data.CachedData;
import de.mein.auth.data.cached.data.CachedPart;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.IMeinService;
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
    private final Map<Long, StateMsg> cachedAnswers = new HashMap<>();
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
            // there is only GET_SERVICES yet
            try {
                if (!handleGetServices(deserialized)) {
                    if (!handleDriveOps(deserialized)) {
                        System.out.println("MeinValidationProcess.onMessageReceived.something exploded here :/");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

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
                return handleReceiveCachedPart((CachedPart) deserialized);
            } else if (deserialized instanceof MeinResponse) {
                // a cached request might fail. clean up everything and continue as usual
                MeinResponse response = (MeinResponse) deserialized;
                if (cachedForRetrieving.containsKey(response.getResponseId())) {
                    if (MeinStrings.msg.STATE_ERR.equals(response.getState())) {
                        CachedData cachedData = cachedForRetrieving.remove(response.getResponseId());
                        cachedData.cleanUp();
                    } else if (MeinStrings.msg.STATE_OK.equals(response.getState())) {
                        // first part of cached data arrives. save the answer for when all parts have arrived.
                        // we will update its cached payload and put it in handleAnswer()
                        CachedData receivedData = (CachedData) response.getPayload();
                        CachedData cached = cachedForRetrieving.get(receivedData.getCacheId());
                        cached.initPartsMissed(receivedData.getPartCount());
                        cachedAnswers.put(cached.getCacheId(), response);
                        handleReceiveCachedPart(receivedData.getPart());
                        return true;
                    }
                }

                // do not return true here cause other things might want to handle this.
                // eg: errors go here
                return false;
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
                        System.out.println("MeinValidationProcess.handleCached.debug234");
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
     * calls handleDriveOps() when done
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
            StateMsg stateMsg = cachedAnswers.get(cached.getCacheId());
            stateMsg.setPayLoad(cached);
            return handleAnswer(stateMsg);
        } else {
            // retrieve the next part
            CachedRequest cachedRequest = new CachedRequest()
                    .setCacheId(cached.getCacheId())
                    .setPartNumber(cached.getNextPartNumber())
                    .setServiceUuid(cached.getServiceUuid());
            send(cachedRequest);
        }
        return false;
    }

    private boolean handleDriveOps(SerializableEntity deserialized) throws SqlQueriesException {
        if (deserialized instanceof MeinMessage) {
            MeinMessage message = (MeinMessage) deserialized;
            String serviceUuid = message.getServiceUuid();
            if (serviceUuid == null) {
                return handleAnswer(deserialized);
            }
            if (isServiceAllowed(serviceUuid)) {
                IMeinService meinService = meinAuthSocket.getMeinAuthService().getMeinService(serviceUuid);
                if (deserialized instanceof MeinRequest) {
                    //delegate request to service
                    MeinRequest request = (MeinRequest) deserialized;
                    Request<IPayload> validatePromise = new Request<>().setPayload(request.getPayload()).setPartnerCertificate(this.partnerCertificate).setIntent(request.getIntent());
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

    private void handleError(MeinRequest request, Exception e) {
        MeinResponse response = request.respondError(e);
        try {
            System.err.println("MeinValidationProcess for " + meinAuthSocket.getMeinAuthService().getName() + ".handleError");
            e.printStackTrace();
            send(response);
            System.err.println("MeinValidationProcess for " + meinAuthSocket.getMeinAuthService().getName() + ".handleError.done");
        } catch (JsonSerializationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        }
    }

    private boolean isServiceAllowed(String serviceUuid) throws SqlQueriesException {
        Service service = meinAuthSocket.getMeinAuthService().getDatabaseManager().getServiceByUuid(serviceUuid);
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

    private void registerCached(MeinRequest request) {
        IPayload payload = request.getPayload();
        if (payload instanceof CachedData) {
            System.out.println("MeinValidationProcess.requestLocked.cached");
            CachedData cachedData = (CachedData) payload;
            cachedData.setCacheId(request.getRequestId());
            cachedData.setServiceUuid(request.getServiceUuid());
            cachedForRetrieving.put(request.getRequestId(), cachedData);
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
    public LockedRequest requestLocked(String serviceUuid, String intent, IPayload payload) throws JsonSerializationException, IllegalAccessException {
        LockedRequest promise = new LockedRequest();
        MeinRequest request = new MeinRequest(serviceUuid, intent);
        if (payload != null) {
            request.setPayLoad(payload);
        }
        registerCached(request);
        request.setRequestHandler(this).queue();
        request.getPromise().done(result -> {
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

    public Request request(String serviceUuid, String intent, IPayload payload) throws JsonSerializationException, IllegalAccessException {
        Request promise = new Request();
        MeinRequest request = new MeinRequest(serviceUuid, intent);
        if (payload != null) {
            request.setPayLoad(payload);
        }
        registerCached(request);
        request.setRequestHandler(this).queue();
        request.getPromise().done(result -> {
            StateMsg response = (StateMsg) result;
            promise.resolve(response.getPayload());
        }).fail(result -> {
            if (validateFail(result)) {
                if (!promise.isRejected())
                    promise.reject(result);
            } else {
                if (!promise.isRejected())
                    promise.reject(result);
            }
        });
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

    public void message(String serviceUuid, String intent, IPayload payload) throws JsonSerializationException, IllegalAccessException {
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
