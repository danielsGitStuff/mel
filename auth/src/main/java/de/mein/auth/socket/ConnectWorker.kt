package de.mein.auth.socket

import de.mein.Lok
import de.mein.auth.data.db.Certificate
import de.mein.auth.jobs.AConnectJob
import de.mein.auth.jobs.ConnectJob
import de.mein.auth.jobs.IsolatedConnectJob
import de.mein.auth.jobs.Job
import de.mein.auth.service.ConnectResult
import de.mein.auth.service.IncomingConnectionJob
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinWorker
import de.mein.auth.socket.process.imprt.MeinCertRetriever
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess
import de.mein.auth.tools.CountdownLock
import de.mein.auth.tools.N
import de.mein.auth.tools.N.NoTryExceptionConsumer
import de.mein.auth.tools.N.result
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.sql.SqlQueriesException
import org.jdeferred.Promise
import org.jdeferred.Promise.State
import org.jdeferred.impl.DeferredObject
import java.io.IOException
import java.net.ConnectException
import java.net.URISyntaxException
import java.security.*
import java.security.cert.CertificateException
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

/**
 * Connects to other instances on the network.
 */
open class ConnectWorker(private val meinAuthService: MeinAuthService) : MeinWorker() {
    private lateinit var meinAuthSocket: MeinAuthSocket
     lateinit var authenticateJob: AConnectJob<MeinValidationProcess, Void>

    constructor(meinAuthService: MeinAuthService, incomingConnectionJob: IncomingConnectionJob) : this(meinAuthService) {
        meinAuthSocket = incomingConnectionJob.meinAuthSocket
        this.authenticateJob = incomingConnectionJob
    }

    constructor(meinAuthService: MeinAuthService, connectionJob: ConnectJob) : this(meinAuthService) {
        this.authenticateJob = connectionJob
    }

    val connectResult: ConnectResult
    init {
        Objects.requireNonNull(authenticateJob)
        authenticateJob.always { state: State?, resolved: Any?, rejected: Any? -> super.shutDown() }
        addJob(authenticateJob)
        connectResult = ConnectResult(this)
    }


    override fun getRunnableName(): String {
        return "Connecting to: " + authenticateJob.address + ":" + authenticateJob.port.toString() + "/" + authenticateJob.portCert
    }

    private fun <T : MeinIsolatedProcess?> isolate(originalJob: IsolatedConnectJob<T>): DeferredObject<T, Exception?, Void?> {
        val runner = N(NoTryExceptionConsumer { e: Exception ->
            e.printStackTrace()
            originalJob.reject(e)
        })
        runner.runTry {
            meinAuthSocket = MeinAuthSocket(meinAuthService, this)
            meinAuthSocket!!.setRunnableName("-> " + originalJob.address + ":" + authenticateJob.port)
            val meinAuthProcess = MeinAuthProcess(meinAuthSocket)
            meinAuthProcess.authenticate(originalJob)
        }
        return originalJob
    }

    private fun auth(originalJob: AConnectJob<*, *>): DeferredObject<MeinValidationProcess, Exception, Void> {
        val dummyJob = ConnectJob(originalJob.certificateId, originalJob.address, originalJob.port, originalJob.portCert, false)
//        DeferredObject<MeinValidationProcess, Exception, Void> deferred = dummyJob;


        val runner = N(NoTryExceptionConsumer { e: Exception ->
            e.printStackTrace()
            dummyJob.reject(e)
        })
        runner.runTry {
            if (meinAuthSocket == null) meinAuthSocket = MeinAuthSocket(meinAuthService, this)
            meinAuthSocket!!.setRunnableName("-> " + originalJob.address + ":" + authenticateJob.port)
//            Socket socket = meinAuthService.getCertificateManager().createSocket();
//            socket.connect(new InetSocketAddress(job.getAddress(), job.getPort()));


            val meinAuthProcess = MeinAuthProcess(meinAuthSocket)
            meinAuthProcess.authenticate(dummyJob)
        }
        return dummyJob
    }

    /**
     * connects and blocks
     *
     * @param job
     * @return
     */
    internal fun connect(job: AConnectJob<*, *>) {
        val lock = CountdownLock(1)
        val remoteCertId: Long? = job.certificateId
        val address: String = job.address
        val port: Int = job.port
        val portCert: Int = job.portCert
        val regOnUnknown: Boolean = result {
            if (job is ConnectJob) return@result job.regOnUnknown
            false
        }

//        Lok.debug("MeinAuthSocket.connect(id=" + remoteCertId + " addr=" + address + " port=" + port + " portCert=" + portCert + " reg=" + regOnUnknown + ")");


        meinAuthService.powerManager.wakeLock(this)
        if (job is ConnectJob) {
            val result: DeferredObject<MeinValidationProcess, java.lang.Exception, Void> = job
            val runner = N(NoTryExceptionConsumer { e: Exception? ->
                if (result.isPending) result.reject(e)
                meinAuthService.powerManager.releaseWakeLock(this)
                stopConnecting()
                lock.unlock()
            })
            val firstAuth = auth(job)
            firstAuth.done { result1: MeinValidationProcess ->
                result.resolve(result1)
                meinAuthService.powerManager.releaseWakeLock(this)
            }.fail { except: Exception ->
                runner.runTry {
                    if (except is ShamefulSelfConnectException) {
                        result.reject(except)
                        meinAuthService.powerManager.releaseWakeLock(this)
                        shutDown()
                    } else if (except is ConnectException) {
                        Lok.error(javaClass.simpleName + " for " + meinAuthService.name + ".connect.HOST:NOT:REACHABLE")
                        result.reject(except)
                        meinAuthService.powerManager.releaseWakeLock(this)
                        stopConnecting()
                    } else if (regOnUnknown && remoteCertId == null) {
                        // try to register

                        val importPromise = DeferredObject<Certificate, Exception, Any>()
                        val registered = DeferredObject<Certificate, Exception, Void>()
                        importCertificate(importPromise, address, port, portCert)
                        importPromise.done { importedCert: Certificate ->
                            runner.runTry {
                                job.setCertificateId(importedCert.id.v())
                                register(registered, importedCert, address, port)
                                registered.done { registeredCert: Certificate ->
                                    runner.runTry {
                                        //connection is no more -> need new socket
                                        // create a new job that is not allowed to register.
                                        val secondJob = ConnectJob(authenticateJob.certificateId, authenticateJob.address, authenticateJob.port, authenticateJob.portCert, false)
                                        secondJob.done { result1: MeinValidationProcess ->
                                            result.resolve(result1)
                                            shutDown()
                                        }.fail { result1: Exception ->
                                            result.reject(result1)
                                            shutDown()
                                        }
                                        addJob(secondJob)
                                    }
                                }.fail { exception: Exception ->
                                    // it won't compile otherwise. don't know why.
                                    // compiler thinks exception is an Object instead of Exception
                                    exception.printStackTrace()
                                    result.reject(exception)
                                    meinAuthService.powerManager.releaseWakeLock(this)
                                    stopConnecting()
                                }
                            }
                        }.fail { ee: Exception ->
                            ee.printStackTrace()
                            result.reject(ee)
                            meinAuthService.powerManager.releaseWakeLock(this)
                            stopConnecting()
                        }
                    } else {
                        if (except !is ShamefulSelfConnectException) {
                            result.reject(CannotConnectException(except, address, port))
                        } else {
                            result.reject(except)
                        }
                        meinAuthService.powerManager.releaseWakeLock(this)
                        stopConnecting()
                    }
                }
            }
        } else if (job is IsolatedConnectJob<*>) {
            isolate(job as IsolatedConnectJob<out MeinIsolatedProcess>)
                    .fail({ result: Exception? -> stopConnecting() })
                    .always { state: State?, resolved: MeinIsolatedProcess?, rejected: Exception? -> meinAuthService.powerManager.releaseWakeLock(this@ConnectWorker) }
        }
    }

    private fun stopConnecting() {
        shutDown()
    }

    @Throws(URISyntaxException::class, InterruptedException::class)
    fun importCertificate(deferred: DeferredObject<Certificate, Exception, Any>, address: String, port: Int, portCert: Int) {
        val retriever = MeinCertRetriever(meinAuthService)
        retriever.retrieveCertificate(deferred, address, port, portCert)
    }

    @Throws(IllegalAccessException::class, SqlQueriesException::class, URISyntaxException::class, InvalidKeyException::class, NoSuchAlgorithmException::class, JsonSerializationException::class, CertificateException::class, KeyStoreException::class, ClassNotFoundException::class, KeyManagementException::class, BadPaddingException::class, UnrecoverableKeyException::class, NoSuchPaddingException::class, IOException::class, IllegalBlockSizeException::class, InterruptedException::class)
    private fun register(result: DeferredObject<Certificate, Exception, Void>, certificate: Certificate, address: String, port: Int): Promise<Certificate, Exception, Void> {
        meinAuthSocket = MeinAuthSocket(meinAuthService, this)
//        Socket socket = meinAuthService.getCertificateManager().createSocket();
//        socket.connect(new InetSocketAddress(address,port));
        val meinRegisterProcess = MeinRegisterProcess(meinAuthSocket)
        return meinRegisterProcess.register(result, certificate.id.v(), address, port)
    }

    @Throws(Exception::class)
    override fun workWork(job: Job<*, *, *>?) {
        if (job is ConnectJob) {
//            Lok.debug("Connecting to: " + connectJob.getAddress() + ":" + connectJob.getPort() + "/" + connectJob.getPortCert());
//        MeinAuthSocket meinAuthSocket = new MeinAuthSocket(meinAuthService);
//        Socket socket = createSocket();
//        N.oneLine(() -> meinAuthSocket.connect(connectJob));

            connect(authenticateJob)
        } else if (job is IsolatedConnectJob<*>) {
            connect(job)
        }
        if (authenticateJob.isResolved) shutDown()
    }

    override fun shutDown() {
        if (meinAuthSocket != null) meinAuthSocket!!.stop()
        super.shutDown()
    }


}