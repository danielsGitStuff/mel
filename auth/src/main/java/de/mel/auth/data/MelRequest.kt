package de.mel.auth.data

import de.mel.Lok
import de.mel.auth.MelStrings.msg
import de.mel.auth.socket.MelSocket
import de.mel.auth.service.MelAuthService
import de.mel.core.serialize.JsonIgnore
import de.mel.core.serialize.SerializableEntity
import `fun`.with.promise.AlreadyResolvedErrorException
import `fun`.with.promise.AlreadyResolvedOkException
import `fun`.with.promise.PromiseImpl
import java.security.SecureRandom
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Created by xor on 4/28/16.
 */
class MelRequest() : MelMessage() {
    var requestId: Long = SecureRandom().nextLong()
        private set
    var answerId: Long? = null
        private set
    @JsonIgnore
    private var requestHandler: IRequestHandler? = null
    @JsonIgnore
    val answerDeferred: PromiseImpl<SerializableEntity, ResponseException> = PromiseImpl.withoutThrowingOnException()

    @JsonIgnore
    @Volatile
    private var timeoutTask: ScheduledFuture<*>? = null
    /**
     * see @[MelSocket]
     */
    private var mode: String? = null

    constructor(serviceUuid: String?, intent: String?) : this() {
        this.serviceUuid = serviceUuid
        this.intent = intent
    }

    fun setRequestId(requestId: Long): MelRequest {
        this.requestId = requestId
        return this
    }

    fun reponse(): MelResponse {
        return MelResponse().setResponseId(requestId)
    }

    fun setRequestHandler(requestHandler: IRequestHandler?): MelRequest {
        this.requestHandler = requestHandler
        return this
    }

    fun queue(): MelRequest {
        requestHandler!!.queueForResponse(this)
        return this
    }

    override fun setPayLoad(payLoad: ServicePayload): MelRequest {
        return super.setPayLoad(payLoad) as MelRequest
    }

    fun setAnswerId(answerId: Long): MelRequest {
        this.answerId = answerId
        return this
    }

    fun request(): MelRequest {
        return MelRequest().setAnswerId(requestId)
    }

    override fun setState(state: String): MelMessage {
        return super.setState(state)
    }

    fun respondError(e: Exception?): MelResponse {
        val response: MelResponse = reponse().setState(msg.STATE_ERR)
        if (e == null) return response
        if (e is ResponseException) response.setException(e) else {
            response.setException(ResponseException(e))
        }
        return response
    }

    fun setMode(mode: String?) {
        Lok.debug("")
        this.mode = mode
    }

    /**
     * Starts the countdown after which the request is rejected, or restarts it. Only requests that were sent have one.
     */
    @Synchronized
    fun startTimeout(melAuthService: MelAuthService) {
        stopTimeout()
        try {
            timeoutTask = melAuthService.timeoutScheduler.schedule({
                if (answerDeferred.isPending) {
                    try {
                        answerDeferred.resolveError(ResponseException("request timed out"))
                    } catch (e: AlreadyResolvedOkException) {
                        // answered at the very same moment
                    } catch (e: AlreadyResolvedErrorException) {
                        // answered at the very same moment
                    }
                }
            }, melAuthService.requestTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            // the instance is shut down
        }
    }

    @Synchronized
    fun stopTimeout() {
        timeoutTask?.cancel(false)
        timeoutTask = null
    }

}