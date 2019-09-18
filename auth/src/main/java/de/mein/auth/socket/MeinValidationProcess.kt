package de.mein.auth.socket

import de.mein.Lok
import de.mein.auth.InsufficientBootLevelException
import de.mein.auth.MeinStrings
import de.mein.auth.MeinStrings.msg
import de.mein.auth.data.*
import de.mein.auth.data.cached.CachedData
import de.mein.auth.data.cached.CachedInitializer
import de.mein.auth.data.cached.CachedPart
import de.mein.auth.data.db.Certificate
import de.mein.auth.data.db.Service
import de.mein.auth.service.MeinService
import de.mein.auth.socket.process.`val`.Request
import de.mein.auth.tools.N.oneLine
import de.mein.core.serialize.SerializableEntity
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.core.serialize.exceptions.MeinJsonException
import de.mein.sql.SqlQueriesException
import java.io.IOException
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.util.*

/**
 * Created by xor on 4/27/16.
 */
class MeinValidationProcess(meinAuthSocket: MeinAuthSocket, partnercertificate: Certificate, private val incoming: Boolean) : MeinProcess(meinAuthSocket) {
    val connectedId: Long
    //    private final Map<Long, CachedData> cachedForRetrieving = new HashMap<>();
//    private final Map<Long, StateMsg> cachedStateMessages = new HashMap<>();
//    private final Map<Long, CachedData> cachedForSending = new HashMap<>();
    private val cachedForSending: MutableMap<Long, CachedInitializer<*>> = HashMap()
    private val cachedForRequesting: MutableMap<Long, CachedInitializer<*>> = HashMap()
    private val cachedStateMessages: MutableMap<Long, StateMsg> = HashMap()
    override fun toString(): String {
        return if (meinAuthSocket != null) {
            (if (incoming) "incoming " else "outgoing ") + meinAuthSocket.getAddressString() + if (meinAuthSocket.isStopped()) " stopped" else " running"
        } else super.toString()
    }

    val isClosed: Boolean
        get() = meinAuthSocket.isStopped()


    class SendException(msg: String?) : Exception(msg)

    override fun stop() {
        super.stop()
    }

    @Synchronized
    @Throws(IOException::class, MeinJsonException::class)
    override fun onMessageReceived(deserialized: SerializableEntity, webSocket: MeinAuthSocket) {
        if (!handleCached(deserialized) && !handleAnswer(deserialized)) {
            try {
                if (!handleGetServices(deserialized)) {
                    if (!handleServiceInteraction(deserialized)) {
                        Lok.debug("MeinValidationProcess.onMessageReceived.something exploded here :/")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * If deserialized is some kind of cached data we care about it here.
     * Except: the [CachedInitializer] is already complete. In this case we return false and let the subsequent methods deal with it.
     * A cached data transfer always starts with a [CachedInitializer] which might be followed by several [CachedPart]s.
     * In order to get all of these parts [CachedRequest]s are sent to the partner and answered with [CachedPart]s.
     * When the [CachedInitializer] is complete a [CachedDoneMessage] is sent to the partner, so he can clean up.
     * Note: this method deals with caching on both sides.
     */
    @Throws(MeinJsonException::class, IOException::class)
    private fun handleCached(deserialized: SerializableEntity): Boolean {

        // if chached stuff starts then as a StateMsg

        if (deserialized is StateMsg) {
            val stateMsg = deserialized
            if (stateMsg.payload is CachedInitializer<*>) {
                // cached data incoming.

                val initializer = stateMsg.payload as CachedInitializer<*>
                cachedForRequesting[initializer.cacheId] = initializer
                //store the StateMsg


                cachedStateMessages[initializer.cacheId] = stateMsg
                // set it up correctly


                initializer.cacheDir = meinAuthSocket.getMeinAuthService().getCacheDir()
                if (!initializer.isComplete) {
                    initializer.initPartsMissed()
                    if (initializer.part != null) {
                        initializer.onReceivedPart(initializer.part)
                    }
                }
                return if (initializer.isComplete) {
                    // clean up and deal with the message

                    cachedForRequesting.remove(initializer.cacheId)
                    cachedStateMessages.remove(initializer.cacheId)
                    send(CachedDoneMessage().setCacheId(initializer.cacheId))
//                    onMessageReceived(deserialized, meinAuthSocket);
//                    initializer.cleanUp();


                    false
                } else {
                    // ask for more

                    send(CachedRequest().setPartNumber(initializer.nextPartNumber).setCacheId(initializer.cacheId))
                    true
                }
            }
        } else if (deserialized is AbstractCachedMessage<*>) {
            val cachedMessage = deserialized
            val cacheId: Long = cachedMessage.cacheId
            if (cachedMessage is CachedRequest) {
                // partner asks for a cached part

                if (cachedForSending.containsKey(cacheId)) {
                    send(cachedForSending[cacheId]!!.getPart(cachedMessage.partNumber))
                } else {
                    Lok.error("INVALID CACHE ID REQUESTED: $cacheId")
                }
            } else if (cachedMessage is CachedDoneMessage) {
                // partner has got everything he needs. we can free up our space here.

                val cachedDoneMessage = cachedMessage
                if (cachedForSending.containsKey(cacheId)) {
                    val initializer = cachedForSending.remove(cacheId)!!
                    initializer.cleanUp()
                } else {
                    Lok.error("INVALID CACHE ID REQUESTED: $cacheId")
                }
            } else if (cachedMessage is CachedPart) {
                if (cachedForRequesting.containsKey(cacheId)) {
                    val initializer = cachedForRequesting[cacheId]!!
                    initializer.onReceivedPart(cachedMessage)
                    if (initializer.isComplete) {
                        cachedForRequesting.remove(cacheId)
                        val stateMsg = cachedStateMessages[cacheId]!!
                        send(CachedDoneMessage().setCacheId(cacheId))
                        onMessageReceived(stateMsg, meinAuthSocket)
                        return false
                    } else {
                        send(CachedRequest().setCacheId(cacheId).setPartNumber(initializer.nextPartNumber))
                    }
                } else {
                    Lok.error("INVALID CACHE ID REQUESTED: $cacheId")
                }
            }
            return true
        }
        return false
    }

    @Throws(SqlQueriesException::class)
    private fun handleServiceInteraction(deserialized: SerializableEntity): Boolean {
        if (deserialized is MeinMessage) {
            val message = deserialized
            val payload: ServicePayload? = message.payload
            val serviceUuid = message.serviceUuid ?: return handleAnswer(deserialized)
            // no serviceuuid could be an answer to a request


            val meinService: MeinService = meinAuthSocket.getMeinAuthService().getMeinService(serviceUuid)
            if (meinService == null) {
                if (message is MeinRequest) {
                    message.answerDeferred.reject(ResponseException("service not available"))
                } else {
                    Lok.debug("msg rejected")
                }
                return true
            }
            if (!bootLevelSatisfied(serviceUuid, payload)) {
                Lok.error("NOT ALLOWED, LEVEL INSUFFICIENT")
                // if a request comes along that requires a higher boot level that the service has not reached yet,
                // this is the place to respond exactly that.


                if (message is MeinRequest) {
                    message.answerDeferred.reject(InsufficientBootLevelException())
                }
                return true
            }
            if (isServiceAllowed(serviceUuid)) {
                if (deserialized is MeinRequest) {
                    val meinRequest = deserialized
                    // wrap it, hand it over to the service and send results back


                    val request4Service: Request<ServicePayload> = Request<ServicePayload>().setPayload(meinRequest.payload).setPartnerCertificate(this.partnerCertificate).setServiceUuid(serviceUuid)
                    if (payload is CachedData) {
                        Lok.debug("MeinValidationProcess.handleServiceInteraction")
                    }
                    //wrap the answer and send it back


                    request4Service.done { newPayload: ServicePayload ->
                        val response: MeinResponse = meinRequest.reponse().setPayLoad(newPayload)
                        if (newPayload is CachedInitializer<*>) {
                            val cachedData = newPayload
                            cachedForSending[cachedData.cacheId] = cachedData
                        }
                        try {
                            send(response)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            handleError(meinRequest, e)
                        }
                    }.fail { result: Exception -> handleError(meinRequest, result) }
                    try {
                        meinService.handleRequest(request4Service)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handleError(meinRequest, e)
                    }
                    return true
                }
                // clean up if it was cached
                else if (deserialized is MeinMessage) {
                    //delegate message to service

                    meinService.handleMessage(deserialized.payload, this.partnerCertificate)
                    return true
                }

                if (deserialized is StateMsg) {
                    val stateMsg = deserialized as StateMsg
                    if (stateMsg.payload is CachedInitializer<*>) {
                        val initializer = stateMsg.payload as CachedInitializer<*>
                        initializer.cleanUp()
                    }
                }
            }
        } else if (deserialized is MeinResponse) {
            return handleAnswer(deserialized)
        }
        return false
    }

    /**
     * Check whether the service has already reached the boot level required by the payload.
     * The level is determined by creating a new instance of the payload that you got here and reading that.
     *
     * @param serviceUuid uuid of the service
     * @param payload     [ServicePayload] that has a required boot level
     * @return
     */
    private fun bootLevelSatisfied(serviceUuid: String, payload: ServicePayload?): Boolean {
        val meinService: MeinService = meinAuthSocket.getMeinAuthService().getMeinService(serviceUuid)
        return if (payload != null) {
            try {
                val payloadClass: Class<out ServicePayload> = payload.javaClass
                val constructor: Constructor<out ServicePayload> = payloadClass.getDeclaredConstructor()
                val newInstance: ServicePayload = constructor.newInstance()
                meinService.bootLevel.greaterOrEqual(newInstance.level)
            } catch (e: InstantiationException) {
                e.printStackTrace()
                false
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
                false
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
                false
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
                false
            }
        } else true
    }

    private fun handleError(request: MeinRequest, e: Exception) {
        Lok.debug("handling error on " + meinAuthSocket.getMeinAuthService().getName())
        val response: MeinResponse = request.respondError(e)
        oneLine { send(response) }
    }

    @Throws(SqlQueriesException::class)
    private fun isServiceAllowed(serviceUuid: String): Boolean {
        val service: Service = meinAuthSocket.getMeinAuthService().getDatabaseManager().getServiceByUuid(serviceUuid)
        if (service == null) {
            //todo debug

            Lok.debug("MeinValidationProcess.isServiceAllowed.debug")
        }
        return meinAuthSocket.getMeinAuthService().getDatabaseManager().isApproved(partnerCertificate.getId().v(), service.id.v())
    }

    @Throws(JsonSerializationException::class, IllegalAccessException::class, SqlQueriesException::class)
    private fun handleGetServices(deserialized: SerializableEntity): Boolean {
        if (deserialized is MeinRequest) {
            val request = deserialized
            val payload: ServicePayload? = request.payload
            if (request.serviceUuid == MeinStrings.SERVICE_NAME && payload != null && payload.hasIntent(msg.INTENT_GET_SERVICES)) {
                val response: MeinResponse = request.reponse()
                MeinAuthProcess.addAllowedServicesJoinTypes(meinAuthSocket.getMeinAuthService(), partnerCertificate, response)
                send(response)
                return true
            }
        }
        return false
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


    @Throws(JsonSerializationException::class)
    override fun send(serializableEntity: SerializableEntity) {
        if (serializableEntity is MeinMessage) {
            val payload: ServicePayload? = serializableEntity.payload
            if (payload is CachedInitializer<*>) {
                val initializer = payload
                cachedForSending[initializer.cacheId] = initializer
            }
        }
        super.send(serializableEntity)
    }

    @Throws(JsonSerializationException::class)
    fun request(serviceUuid: String, payload: ServicePayload): Request<ServicePayload> {
        meinAuthSocket.meinAuthService.powerManager.wakeLock(this@MeinValidationProcess)
        val promise: Request<ServicePayload> = Request<ServicePayload>().setServiceUuid(serviceUuid)
        val request = MeinRequest(serviceUuid, null)
        if (payload != null) {
            request.setPayLoad(payload)
        }
        request.setRequestHandler(this).queue()
        request.answerDeferred.done { result: SerializableEntity ->
            val response = result as StateMsg
            promise.resolve(response.payload)
            meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(this@MeinValidationProcess)
        }.fail { result: ResponseException ->
            if (validateFail(result)) {
                try {
                    if (!promise.isRejected) {
                        promise.reject(result)
                    }
                } finally {
                    meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(this@MeinValidationProcess)
                }
            } else {
                try {
                    if (!promise.isRejected) {
                        promise.reject(result)
                    }
                } finally {
                    meinAuthSocket.getMeinAuthService().getPowerManager().releaseWakeLock(this@MeinValidationProcess)
                }
            }
        }
        // todo this line should be redundant, see "request.setRequestHandler(this).queue();" above


        queueForResponse(request)
        send(request)
        return promise
    }


//    public Request request(String serviceUuid, String intent, SerializableEntity payload) throws JsonSerializationException, IllegalAccessException {
//        List<SerializableEntity> payloads = new ArrayList<>();
//        if (payload != null)
//            payloads.add(payload);
//        return requestWithList(serviceUuid, intent, payloads);
//
//    }


    private fun validateFail(result: Exception): Boolean {
        return false
    }

    private fun validateThingy(result: SerializableEntity?): Boolean {
        return false
    }

    @Throws(JsonSerializationException::class, IllegalAccessException::class)
    fun message(serviceUuid: String?, payload: ServicePayload?) {
        val message: MeinMessage = MeinMessage(serviceUuid, null).setPayLoad(payload)
        send(message)
    }


    val addressString: String?
        get() = meinAuthSocket.getAddressString()


//    public Request requestWithList(String serviceName, String intent) throws JsonSerializationException, IllegalAccessException {
//        return requestWithList(serviceName, intent, new ArrayList<>());
//    }


    init {
        this.meinAuthSocket = meinAuthSocket
        connectedId = partnercertificate.id.v()
        try {
            this.partnerCertificate = meinAuthSocket.getMeinAuthService().certificateManager.getTrustedCertificateById(connectedId)
        } catch (e: SqlQueriesException) {
            e.printStackTrace()
        }
    }
}