package de.mein.auth.socket

import de.mein.Lok
import de.mein.auth.MeinStrings
import de.mein.auth.data.MeinRequest
import de.mein.auth.data.db.Certificate
import de.mein.auth.jobs.AConnectJob
import de.mein.auth.jobs.BlockReceivedJob
import de.mein.auth.service.IncomingConnectionJob
import de.mein.auth.service.MeinAuthService
import de.mein.auth.socket.MeinSocket.MeinSocketListener
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess
import de.mein.auth.tools.N.result
import de.mein.core.serialize.SerializableEntity
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mein.sql.Hash
import de.mein.sql.SqlQueriesException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyManagementException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateEncodingException
import java.util.*
import javax.net.ssl.SSLSocket

/**
 * Created by xor on 10.08.2016.
 */
class MeinAuthSocket : MeinSocket, MeinSocketListener {
    private lateinit var connectWorker: ConnectWorker
    var process: MeinProcess? = null
        protected set
    var partnerCertificate: Certificate? = null
        protected set


    constructor(meinAuthService: MeinAuthService, connectWorker: ConnectWorker) : super(meinAuthService, null) {
        setListener(this)
        this.connectWorker = connectWorker
    }

    constructor(meinAuthService: MeinAuthService, socket: Socket) : super(meinAuthService, socket) {
        setListener(this)
        connectWorker = ConnectWorker(meinAuthService, IncomingConnectionJob(this))
    }

    val addressString: String
        get() {
            val port: Int = result {
                if (connectJob == null) return@result socket.getLocalPort()
                connectJob!!.port
            }
            return getAddressString(socket.getInetAddress(), port)
        }

    override fun allowIsolation(): MeinAuthSocket {
        this.allowIsolation = true
        return this
    }

    override fun onIsolated() {
        (process as MeinIsolatedProcess?)!!.onIsolated()
    }

    override fun onMessage(meinSocket: MeinSocket, msg: String) {
        try {
            meinAuthService.powerManager.wakeLock(this)
            val deserialized: SerializableEntity? = SerializableEntityDeserializer.deserialize(msg)
            if (process != null) {
                process!!.onMessageReceived(deserialized, this)
            } else if (deserialized is MeinRequest) {
                val request = deserialized
                if (request.serviceUuid == MeinStrings.SERVICE_NAME && request.intent == MeinStrings.msg.INTENT_REGISTER) {
                    val meinRegisterProcess = MeinRegisterProcess(this)
                    process = meinRegisterProcess
                    meinRegisterProcess.onMessageReceived(deserialized, this)
                } else if (request.serviceUuid == MeinStrings.SERVICE_NAME && request.intent == MeinStrings.msg.INTENT_AUTH) {
                    val meinAuthProcess = MeinAuthProcess(this)
                    process = meinAuthProcess
                    meinAuthProcess.onMessageReceived(deserialized, this)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            meinAuthService.powerManager.releaseWakeLock(this)
        }
    }

    override fun onOpen() {}
    override fun onError(ex: Exception) {}
    override fun onClose(code: Int, reason: String, remote: Boolean) {
//        Lok.debug(meinAuthService.getName() + "." + getClass().getSimpleName() + ".onClose");

        if (process != null) process!!.onSocketClosed(code, reason, remote)
        meinAuthService.onSocketClosed(this)
    }

    override fun onBlockReceived(block: BlockReceivedJob) {
        // this shall only work with isolated processes

        (process as MeinIsolatedProcess?)!!.onBlockReceived(block)
    }

    @Throws(SqlQueriesException::class, UnrecoverableKeyException::class, NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class, IOException::class)
    internal fun connectSSL(certId: Long?, address: String, port: Int) {
        if (certId != null) partnerCertificate = meinAuthService.certificateManager.getTrustedCertificateById(certId)
        val socket: Socket = meinAuthService.certificateManager.createSocket()
        Lok.debug("MeinAuthSocket.connectSSL: $address:$port")
        socket.connect(InetSocketAddress(address, port))
        //stop();


        setSocket(socket)
        start()
    }

    @get:Throws(IOException::class, CertificateEncodingException::class, SqlQueriesException::class, ShamefulSelfConnectException::class)
    internal val trustedPartnerCertificate: Certificate?
        internal get() {
            if (partnerCertificate == null) {
                val sslSocket = socket as SSLSocket
                val cert: java.security.cert.Certificate = sslSocket.session.peerCertificates[0]
                val certBytes: ByteArray? = cert.encoded
                val hash: String? = Hash.sha256(certBytes)
                partnerCertificate = meinAuthService.certificateManager.getTrustedCertificateByHash(hash)
                if (partnerCertificate == null) {
                    if (Arrays.equals(meinAuthService.certificateManager.publicKey.encoded, cert.publicKey.encoded)) {
                        throw ShamefulSelfConnectException()
                    }
                }
            }
            return partnerCertificate
        }

    @Throws(IOException::class)
    fun sendBlock(block: ByteArray) {
        assert(block.size == BLOCK_SIZE)
        out.write(block)
        out.flush()
    }

    @Throws(IOException::class)
    fun disconnect() {
        socket.close()
    }

    val isValidated: Boolean
        get() = process != null && process is MeinValidationProcess

    override fun onSocketClosed() {
        meinAuthService.onSocketClosed(this)
    }

    internal fun setProcess(process: MeinProcess?): MeinAuthSocket {
        this.process = process
        return this
    }

    override fun onShutDown() {
        super.onShutDown()
    }

    override fun start() {
        super.start()
    }

    override fun stop() {
        super.stop()
    }

    //todo make package private
    var socket: Socket
        set(socket) {
            super.socket = socket
        }

    companion object {
        fun getAddressString(address: InetAddress, port: Int): String {
            return address.hostAddress + ":" + port
        }
    }
}