package de.mein.auth.socket

import de.mein.Lok
import de.mein.auth.MeinStrings
import de.mein.auth.MeinStrings.msg
import de.mein.auth.data.IsolationDetails
import de.mein.auth.data.MeinRequest
import de.mein.auth.data.MeinResponse
import de.mein.auth.data.access.CertificateManager
import de.mein.auth.data.db.Certificate
import de.mein.auth.data.db.Service
import de.mein.auth.data.db.ServiceJoinServiceType
import de.mein.auth.jobs.AConnectJob
import de.mein.auth.jobs.ConnectJob
import de.mein.auth.jobs.IsolatedConnectJob
import de.mein.auth.service.IMeinService
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinService
import de.mein.auth.socket.process.`val`.MeinServicesPayload
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess
import de.mein.auth.tools.Cryptor
import de.mein.auth.tools.N
import de.mein.auth.tools.N.NoTryExceptionConsumer
import de.mein.core.serialize.SerializableEntity
import de.mein.sql.SqlQueriesException
import org.jdeferred.Promise
import java.io.IOException

/**
 * handles authentication of incoming and outgoing connections
 * Created by xor on 4/21/16.
 */
class MeinAuthProcess(meinAuthSocket: MeinAuthSocket?) : MeinProcess(meinAuthSocket) {
    private var mySecret: String? = null
    // this is not mine
    private var decryptedSecret: String? = null
    private var partnerAuthenticated = false
    private val validationProcess: MeinValidationProcess? = null
    override fun onMessageReceived(deserialized: SerializableEntity, socket: MeinAuthSocket) {
        try {
            if (!handleAnswer(deserialized)) if (deserialized is MeinRequest) {
                val request = deserialized
                try {
                    partnerCertificate = meinAuthSocket.getMeinAuthService().certificateManager.getTrustedCertificateByUuid(request.userUuid)
                    assert(partnerCertificate != null)
                    decryptedSecret = meinAuthSocket.getMeinAuthService().certificateManager.decrypt(request.secret)
                    mySecret = CertificateManager.randomUUID().toString()
                    var isolationDetails: IsolationDetails? = null
                    if (request.payload != null && request.payload is IsolationDetails) {
                        isolationDetails = request.payload as IsolationDetails
                    }
                    val secret: ByteArray? = Cryptor.encrypt(partnerCertificate, mySecret)
                    val answer: MeinRequest = request.request().setRequestHandler(this).queue()
                            .setDecryptedSecret(decryptedSecret)
                            .setSecret(secret)
                    val finalIsolationDetails = isolationDetails
                    answer.answerDeferred.done { result: SerializableEntity ->
                        val r = result as MeinRequest
                        val response: MeinResponse = r.reponse()
                        try {
                            if (r.decryptedSecret == mySecret) {
                                if (finalIsolationDetails == null) {
                                    partnerAuthenticated = true
                                    val validationProcess = MeinValidationProcess(socket, partnerCertificate, true)
                                    if (meinAuthSocket.getMeinAuthService().registerValidationProcess(validationProcess)) {
                                        // get all allowed Services

                                        addAllowedServices(meinAuthSocket.getMeinAuthService(), partnerCertificate, response)
                                        send(response)
                                        // done here, set up validationprocess


                                        Lok.debug(meinAuthSocket.getMeinAuthService().name + " AuthProcess leaves socket")
                                        // propagate that we are connected!
                                        // note: if the connection is incoming, we cannot use the ports from the socket here.
                                        // whenever an outgoing connection is set up a random high port is used to do so (port 50k+)


                                        propagateAuthentication(partnerCertificate, socket.getSocket().inetAddress.hostAddress, partnerCertificate.port.v())
//                                            propagateAuthentication(this.partnerCertificate, socket.getSocket().getInetAddress().getHostAddress(), socket.getSocket().getLocalPort());

                                    } else {
//                                            Lok.debug("leaving, cause connection to cert " + partnerCertificate.getId().v() + " already exists. closing...");

                                        Lok.debug("connection to cert " + partnerCertificate.id.v().toString() + " already exists. waiting for the other side to close connection.")
//                                            this.stop();

                                    }
                                } else {
                                    Lok.debug("leaving for IsolationProcess")
                                    val service: IMeinService = meinAuthSocket.getMeinAuthService().getMeinService(finalIsolationDetails.targetService)
                                    val isolatedClass = javaClass.forName(finalIsolationDetails.processClass) as Class<out MeinIsolatedProcess>
                                    val isolatedProcess: MeinIsolatedProcess = MeinIsolatedProcess.instance(isolatedClass, meinAuthSocket, service, partnerCertificate.id.v(), finalIsolationDetails.sourceService, finalIsolationDetails.isolationUuid)
                                    isolatedProcess.service = service
                                    service.onIsolatedConnectionEstablished(isolatedProcess)
                                    send(response)
                                }
                            } else {
                                response.state = msg.STATE_ERR
                                send(response)
                            }
                        } catch (e: Exception) {
                            Lok.error("leaving socket, because of EXCEPTION: $e")
                            removeThyself()
                        }
                    }
                    send(answer)
                } catch (e: Exception) {
                    Lok.debug("leaving, because of exception: $e")
                    e.printStackTrace()
                }
            } else Lok.debug("MeinAuthProcess.onMessageReceived.ELSE1")
        } catch (e: Exception) {
            try {
                Lok.debug("leaving, because of exception: $e")
                socket.disconnect()
            } catch (e1: IOException) {
                e1.printStackTrace()
            }
        }
    }

    @Throws(SqlQueriesException::class)
    private fun propagateAuthentication(partnerCertificate: Certificate, address: String?, port: Int) {
        // propagate to database first

        partnerCertificate.setAddress(address)
        partnerCertificate.setPort(port)
        meinAuthSocket.getMeinAuthService().certificateManager.updateCertificate(partnerCertificate)
        // propagate to allowed services


        val services: List<Service> = meinAuthSocket.getMeinAuthService().databaseManager.getAllowedServices(partnerCertificate.id.v())
        for (service in services) {
            val ins: IMeinService? = meinAuthSocket.getMeinAuthService().getMeinService(service.uuid.v())
            ins?.connectionAuthenticated(partnerCertificate)
        }
    }

    override fun toString(): String {
        return javaClass.simpleName + "." + meinAuthSocket.getMeinAuthService().name
    }

    internal fun authenticate(job: AConnectJob<*, *>) {
        val id: Long? = job.certificateId
        val address: String? = job.address
        val port: Int? = job.port
        try {
            meinAuthSocket.connectSSL(id, address, port!!)
            mySecret = CertificateManager.randomUUID().toString()
            if (partnerCertificate == null) partnerCertificate = meinAuthSocket.trustedPartnerCertificate
            val runner = N(NoTryExceptionConsumer { e: Exception ->
                e.printStackTrace()
                job.reject(e)
            })
            mySecret = CertificateManager.randomUUID().toString()
            val secret: ByteArray? = Cryptor.encrypt(partnerCertificate, mySecret)
            val request: MeinRequest = MeinRequest(MeinStrings.SERVICE_NAME, msg.INTENT_AUTH)
                    .setRequestHandler(this).queue().setSecret(secret)
                    .setUserUuid(partnerCertificate.answerUuid.v())
            if (job is IsolatedConnectJob<*>) {
                val isolatedConnectJob = job
                val isolationDetails: IsolationDetails? = IsolationDetails()
                        .setTargetService(isolatedConnectJob.remoteServiceUuid)
                        .setSourceService(isolatedConnectJob.ownServiceUuid)
                        .setIsolationUuid(job.isolatedUuid)
                        .setProcessClass(job.processClass.canonicalName)
                request.setPayLoad(isolationDetails)
            }
            request.answerDeferred.done { result: SerializableEntity ->
                val r = result as MeinRequest
                if (r.decryptedSecret == mySecret) {
                    runner.runTry {
                        decryptedSecret = meinAuthSocket.getMeinAuthService().certificateManager.decrypt(r.secret)
                        val answer: MeinRequest = r.request()
                                .setDecryptedSecret(decryptedSecret)
                                .setAuthenticated(true)
                                .setRequestHandler(this).queue()
                        answer.answerDeferred.done { result1: SerializableEntity ->
                            runner.runTry {
                                if (job is ConnectJob) {
                                    // check if already connected to that cert

                                    val validationProcess = MeinValidationProcess(meinAuthSocket, partnerCertificate, false)
                                    if (meinAuthSocket.getMeinAuthService().registerValidationProcess(validationProcess)) {
                                        // propagate that we are connected!

                                        propagateAuthentication(partnerCertificate, meinAuthSocket.getSocket().inetAddress.hostAddress, meinAuthSocket.getSocket().port)
                                        // done here, set up validationprocess


                                        Lok.debug(meinAuthSocket.getMeinAuthService().name + " AuthProcess leaves socket")
                                        // tell MAS we are connected & authenticated


                                        job.resolve(validationProcess)
                                        //


                                        val actualRemoteCertId = arrayOfNulls<Long?>(1)
                                        runner.runTry {
                                            actualRemoteCertId[0] = if (job.getCertificateId() == null) partnerCertificate.id.v() else job.getCertificateId()
                                            meinAuthSocket.getMeinAuthService().updateCertAddresses(actualRemoteCertId[0], address, port, job.getPortCert())
                                        }
                                    } else {
                                        Lok.debug("connection to cert " + partnerCertificate.id.v().toString() + " already existing. closing... v=" + meinAuthSocket.v)
                                        job.resolve(validationProcess)
                                        return@runTry
                                    }
                                } else if (job is IsolatedConnectJob<*>) {
                                    val isolatedConnectJob = job
                                    if (partnerCertificate.id.v() !== job.getCertificateId()) {
                                        job.reject(Exception("not the partner I expected"))
                                    } else {
                                        Lok.debug("MeinAuthProcess.authenticate465")
                                        val service: IMeinService = meinAuthSocket.getMeinAuthService().getMeinService(isolatedConnectJob.ownServiceUuid)
                                        val isolatedProcessClass: Class<*>? = isolatedConnectJob.processClass
                                        val meinIsolatedProcess: MeinIsolatedProcess = MeinIsolatedProcess.instance(isolatedProcessClass, meinAuthSocket, service, partnerCertificate.id.v(), isolatedConnectJob.remoteServiceUuid, isolatedConnectJob.isolatedUuid)
                                        val isolated: Promise<Void, Exception, Void> = meinIsolatedProcess.sendIsolate()
                                        isolated.done { nil: Void ->
                                            service.onIsolatedConnectionEstablished(meinIsolatedProcess)
                                            meinIsolatedProcess.service = service
                                            job.resolve(meinIsolatedProcess)
                                        }.fail { excc: Exception -> job.reject(excc) }
                                    }
                                }
                            }
                        }
                        send(answer)
                    }
                } else {
                    //error stuff

                    Lok.debug("MeinAuthProcess.authenticate.error.decrypted.secret: " + r.decryptedSecret)
                    Lok.debug("MeinAuthProcess.authenticate.error.should.be: $mySecret")
                    job.reject(Exception("find aok39ka"))
                }
            }
            send(request)
        } catch (e: Exception) {
            Lok.error("Exception occured: " + e.toString() + " v=" + meinAuthSocket.v)
            job.reject(e)
        }
    }

    companion object {
        /**
         * adds the currently available services to the Response
         *
         * @param meinAuthService
         * @param partnerCertificate
         * @param response
         * @throws SqlQueriesException
         */
        @Throws(SqlQueriesException::class)
        fun addAllowedServices(meinAuthService: MeinAuthService, partnerCertificate: Certificate, response: MeinResponse) {
            val payload: MeinServicesPayload? = meinAuthService.getAllowedServicesFor(partnerCertificate.id.v())
            response.setPayLoad(payload)
        }

        @Throws(SqlQueriesException::class)
        fun addAllowedServicesJoinTypes(meinAuthService: MeinAuthService, partnerCertificate: Certificate, response: MeinResponse) {
            val payload = MeinServicesPayload()
            response.setPayLoad(payload)
            val servicesJoinTypes: List<ServiceJoinServiceType> = meinAuthService.databaseManager.getAllowedServicesJoinTypes(partnerCertificate.id.v())
            //set flag for running Services, then add to result


            for (service in servicesJoinTypes) {
                val meinService: MeinService? = meinAuthService.getMeinService(service.uuid.v())
                service.isRunning = meinService != null
                if (meinService != null) {
                    service.additionalServicePayload = meinService.addAdditionalServiceInfo()
                }
                payload.addService(service)
            }
            response.setPayLoad(payload)
        }
    }
}