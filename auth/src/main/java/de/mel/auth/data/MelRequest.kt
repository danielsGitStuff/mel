package de.mel.auth.data

import de.mel.Lok
import de.mel.auth.MelStrings.msg
import de.mel.auth.data.db.Certificate
import de.mel.auth.socket.MelSocket
import de.mel.auth.tools.N
import de.mel.auth.tools.WatchDogTimer
import de.mel.core.serialize.JsonIgnore
import de.mel.core.serialize.SerializableEntity
import org.jdeferred.impl.DeferredObject
import java.security.SecureRandom

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
    val answerDeferred: DeferredObject<SerializableEntity, ResponseException, Void> = DeferredObject()
    var secret: ByteArray? = null
        private set
    var decryptedSecret: String? = null
        private set
    var userUuid: String? = null
        private set
    var authenticated: Boolean? = null
        private set
    var certificate: Certificate? = null
        private set

    val timer = WatchDogTimer("request killer", {
        N.r { answerDeferred.reject(ResponseException("request timed out")) }
    }, 30, 100, 1000)
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

    fun setSecret(secret: ByteArray): MelRequest {
        this.secret = secret
        return this
    }

    fun setDecryptedSecret(decryptedSecret: String?): MelRequest {
        this.decryptedSecret = decryptedSecret
        return this
    }

    fun setUserUuid(userUuid: String?): MelRequest {
        this.userUuid = userUuid
        return this
    }

    fun setAuthenticated(authenticated: Boolean): MelRequest {
        this.authenticated = authenticated
        return this
    }

    fun setCertificate(certificate: Certificate?): MelRequest {
        this.certificate = certificate
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

    fun startTimeout() {
        timer.start();
    }

    fun stopTimeout() {
        timer.stop();
    }

}