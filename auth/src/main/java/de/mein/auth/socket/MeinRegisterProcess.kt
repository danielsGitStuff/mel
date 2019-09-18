package de.mein.auth.socket

import de.mein.Lok
import de.mein.auth.MeinStrings
import de.mein.auth.MeinStrings.msg
import de.mein.auth.data.*
import de.mein.auth.data.access.CertificateManager
import de.mein.auth.data.db.Certificate
import de.mein.auth.socket.process.reg.IRegisterHandlerListener
import de.mein.auth.tools.N
import de.mein.auth.tools.N.NoTryExceptionConsumer
import de.mein.core.serialize.SerializableEntity
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer
import de.mein.sql.SqlQueriesException
import org.jdeferred.Promise
import org.jdeferred.impl.DefaultDeferredManager
import org.jdeferred.impl.DeferredObject
import org.jdeferred.multiple.MultipleResults
import org.jdeferred.multiple.OneReject
import java.io.IOException
import java.net.URISyntaxException
import java.security.*
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

/**
 * handles registration of incoming and outgoing connections
 * Created by xor on 4/20/16.
 */
class MeinRegisterProcess(meinAuthSocket: MeinAuthSocket) : MeinProcess(meinAuthSocket) {
    private var certificateManager: CertificateManager? = null
    private val runner = N(NoTryExceptionConsumer { obj: Exception -> obj.printStackTrace() })
    private val confirmedPromise = DeferredObject<MeinRegisterConfirm, Exception, Void>()
    private val acceptedPromise = DeferredObject<Certificate, Exception, Void>()
    @Throws(InterruptedException::class, SqlQueriesException::class, CertificateException::class, NoSuchPaddingException::class, NoSuchAlgorithmException::class, IOException::class, BadPaddingException::class, IllegalBlockSizeException::class, InvalidKeyException::class, ClassNotFoundException::class, JsonSerializationException::class, IllegalAccessException::class, URISyntaxException::class, UnrecoverableKeyException::class, KeyStoreException::class, KeyManagementException::class)
    fun register(deferred: DeferredObject<Certificate, Exception, Void>, id: Long, address: String?, port: Int): Promise<Certificate, Exception, Void> {
        Lok.debug(meinAuthSocket.getMeinAuthService().name + ".MeinRegisterProcessImpl.register.id=" + id)
        meinAuthSocket.connectSSL(id, address, port)
        partnerCertificate = meinAuthSocket.getMeinAuthService().certificateManager.getCertificateById(id)
        val myCert = Certificate()
        myCert.setName(meinAuthSocket.getMeinAuthService().name)
        myCert.setAnswerUuid(partnerCertificate.uuid.v())
        myCert.setCertificate(meinAuthSocket.getMeinAuthService().certificateManager.myX509Certificate.encoded)
        val settings: MeinAuthSettings = meinAuthSocket.getMeinAuthService().settings
        myCert.setPort(settings.port)
                .setCertDeliveryPort(settings.deliveryPort)
        val request: MeinRequest = MeinRequest(MeinStrings.SERVICE_NAME, msg.INTENT_REGISTER)
                .setCertificate(myCert)
                .setRequestHandler(this).queue()
        for (regHandler in meinAuthSocket.getMeinAuthService().registerHandlers) {
            regHandler.acceptCertificate(object : IRegisterHandlerListener {
                override fun onCertificateAccepted(request: MeinRequest?, certificate: Certificate) {
                    runner.runTry {
                        // tell your partner that you trust him
                        sendConfirmation(true)
                        partnerCertificate = certificate
                        acceptedPromise.resolve(certificate)
                    }
                }

                override fun onCertificateRejected(request: MeinRequest?, certificate: Certificate?) {
                    runner.runTry {
                        sendConfirmation(false)
                        acceptedPromise.reject(UserDidNotTrustException())
                    }
                }
            }, request, meinAuthSocket.getMeinAuthService().myCertificate, partnerCertificate)
        }
        request.answerDeferred.done { result: SerializableEntity ->
            val r = result as MeinRequest
            val certificate: Certificate = r.certificate
            try {
                partnerCertificate = meinAuthSocket.getMeinAuthService().certificateManager.addAnswerUuid(partnerCertificate.id.v(), certificate.answerUuid.v())
                val response: MeinResponse = r.reponse()
                response.state = msg.STATE_OK
                send(response)
                removeThyself()
                for (registeredHandler in meinAuthSocket.getMeinAuthService().registeredHandlers) {
                    try {
                        registeredHandler.onCertificateRegistered(meinAuthSocket.getMeinAuthService(), partnerCertificate)
                    } catch (e: SqlQueriesException) {
                        e.printStackTrace()
                    }
                }
                deferred.resolve(partnerCertificate)
            } catch (e: Exception) {
                e.printStackTrace()
                deferred.reject(e)
            }
        }.fail { result: ResponseException ->
            Lok.debug("MeinRegisterProcess.onFail!!!!!!!!")
            deferred.reject(result)
        }
        DefaultDeferredManager().`when`(confirmedPromise, acceptedPromise).done { result: MultipleResults ->
            Lok.debug("MeinRegisterProcess.register")
            deferred.resolve(partnerCertificate)
        }
        val json: String? = SerializableEntitySerializer.serialize(request)
        meinAuthSocket.send(json)
        return deferred
    }

    @Throws(JsonSerializationException::class, IllegalAccessException::class)
    fun sendConfirmation(trusted: Boolean) {
        val message = MeinMessage(MeinStrings.SERVICE_NAME, null)
        if (trusted) message.setPayLoad(MeinRegisterConfirm().setConfirmed(trusted).setAnswerUuid(partnerCertificate.uuid.v())) else message.setPayLoad(MeinRegisterConfirm().setConfirmed(false))
        send(message)
    }

    override fun onMessageReceived(deserialized: SerializableEntity, webSocket: MeinAuthSocket) {
        if (meinAuthSocket == null) meinAuthSocket = webSocket
        if (!handleAnswer(deserialized)) if (deserialized is MeinRequest) {
            val request = deserialized
            try {
                val certificate: Certificate? = request.certificate
                if (meinAuthSocket.getMeinAuthService().registerHandlers.size == 0) {
                    Lok.debug("MeinRegisterProcess.onMessageReceived.NO.HANDLER.FOR.REGISTRATION.AVAILABLE")
                }
                for (registerHandler in meinAuthSocket.getMeinAuthService().registerHandlers) {
                    registerHandler.acceptCertificate(object : IRegisterHandlerListener {
                        override fun onCertificateAccepted(request: MeinRequest?, certificate: Certificate) {
                            runner.runTry {
                                // tell your partner that you appreciate her actions!
                                val x509Certificate: X509Certificate? = CertificateManager.loadX509CertificateFromBytes(certificate.certificate.v())
                                val address: String? = meinAuthSocket.address
                                val port: Int = certificate.port.v()
                                val portCert: Int = certificate.certDeliveryPort.v()
                                partnerCertificate = certificateManager!!.importCertificate(x509Certificate, certificate.name.v(), certificate.answerUuid.v(), address, port, portCert)
                                certificateManager.trustCertificate(partnerCertificate.id.v(), true)
                                sendConfirmation(true)
                                for (handler in meinAuthSocket.getMeinAuthService().registeredHandlers) {
                                    handler.onCertificateRegistered(meinAuthSocket.getMeinAuthService(), partnerCertificate)
                                }
                                acceptedPromise.resolve(partnerCertificate)
                            }
                        }

                        override fun onCertificateRejected(request: MeinRequest?, certificate: Certificate?) {
                            runner.runTry {
                                sendConfirmation(false)
                                acceptedPromise.reject(PartnerDidNotTrustException())
                            }
                        }
                    }, request, meinAuthSocket.getMeinAuthService().myCertificate, certificate)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (deserialized is MeinResponse) {
            //should not be called

            Lok.debug("MeinRegisterProcess.onMessageReceived.WRONG")
        } else if (deserialized is MeinMessage) {
            val message = deserialized
            if (message.payload != null && message.payload is MeinRegisterConfirm) {
                val confirm = message.payload as MeinRegisterConfirm
                if (confirm.isConfirmed) {
                    confirmedPromise.resolve(confirm)
                } else {
                    confirmedPromise.reject(PartnerDidNotTrustException())
                }
            }
        } else Lok.debug("MeinRegisterProcess.onMessageReceived.VERY.WRONG")
    }

    init {
        certificateManager = meinAuthSocket.getMeinAuthService().certificateManager
        confirmedPromise.done { result: MeinRegisterConfirm -> for (registerHandler in meinAuthSocket.getMeinAuthService().registerHandlers) registerHandler.onRemoteAccepted(partnerCertificate) }
        acceptedPromise.done { result: Certificate -> for (registerHandler in meinAuthSocket.getMeinAuthService().registerHandlers) registerHandler.onLocallyAccepted(partnerCertificate) }
        DefaultDeferredManager().`when`(confirmedPromise, acceptedPromise).done { results: MultipleResults ->
            runner.runTry {
                Lok.debug(meinAuthSocket.getMeinAuthService().name + ".MeinRegisterProcess.MeinRegisterProcess")
                val confirm = results.get(0).result as MeinRegisterConfirm
                val certificate = results.get(1).result as Certificate
                //check if is UUID


                val answerUuid: UUID = UUID.fromString(confirm.answerUuid)
                certificate.answerUuid.v(answerUuid.toString())
                certificateManager.updateCertificate(certificate)
                certificateManager.trustCertificate(certificate.id.v(), true)
                for (registerHandler in meinAuthSocket.getMeinAuthService().registerHandlers) registerHandler.onRegistrationCompleted(partnerCertificate)
                for (handler in meinAuthSocket.getMeinAuthService().registeredHandlers) {
                    handler.onCertificateRegistered(meinAuthSocket.getMeinAuthService(), certificate)
                }
                //todo send done, so the other side can connect!

            }
        }.fail { results: OneReject ->
            runner.runTry {
                if (results is OneReject) {
                    if (results.reject is PartnerDidNotTrustException) {
                        Lok.debug("MeinRegisterProcess.MeinRegisterProcess.rejected: " + results.reject.toString())
                        for (registerHandler in meinAuthSocket.getMeinAuthService().registerHandlers) registerHandler.onRemoteRejected(partnerCertificate)
                    } else if (results.reject is UserDidNotTrustException) {
                        Lok.debug("MeinRegisterProcess.MeinRegisterProcess.user.did.not.trust")
                        for (registerHandler in meinAuthSocket.getMeinAuthService().registerHandlers) registerHandler.onLocallyRejected(partnerCertificate)
                    }
                    certificateManager.deleteCertificate(partnerCertificate)
                } else {
                    Lok.error("MeinRegisterProcess.MeinRegisterProcess.reject.UNKNOWN.2")
                    certificateManager.deleteCertificate(partnerCertificate)
                }
                stop()
            }
        }
    }
}