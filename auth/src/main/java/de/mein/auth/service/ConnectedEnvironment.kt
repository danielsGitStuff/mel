package de.mein.auth.service

import de.mein.Lok
import de.mein.auth.data.db.Certificate
import de.mein.auth.jobs.AConnectJob
import de.mein.auth.jobs.ConnectJob
import de.mein.auth.socket.ConnectWorker
import de.mein.auth.socket.MeinAuthSocket
import de.mein.auth.socket.MeinValidationProcess
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess
import de.mein.auth.tools.N.*
import de.mein.auth.tools.lock.T
import de.mein.auth.tools.lock.Transaction
import de.mein.sql.SqlQueriesException
import org.jdeferred.Promise
import org.jdeferred.impl.DeferredObject
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by xor on 13.10.2016.
 */
class ConnectedEnvironment internal constructor(private val meinAuthService: MeinAuthService) {
    private val idValidateProcessMap: MutableMap<Long, MeinValidationProcess> = HashMap()
    private val addressValidateProcessMap: MutableMap<String, MeinValidationProcess> = HashMap()
    private val currentlyConnectingCertIds: MutableMap<Long, MeinAuthSocket> = HashMap()
    private val currentlyConnectingAddresses: MutableMap<String, MeinAuthSocket> = HashMap()
    @Synchronized
    @Throws(SqlQueriesException::class, InterruptedException::class)
    internal fun connect(certificate: Certificate): Promise<MeinValidationProcess?, Exception, Void> {
        Lok.debug("connect to cert: " + certificate.id.v().toString() + " , addr: " + certificate.address.v())
        val deferred = DeferredObject<MeinValidationProcess?, Exception, Void>()
        val certificateId: Long = certificate.id.v()
        // check if already connected via id and address


        var transaction: Transaction<*>? = null
        try {
            transaction = T.lockingTransaction(T.read(this))
            val def = isCurrentlyConnecting(certificateId)
            if (def != null) {
                return def.connectJob
            }
            run {
                var mvp: MeinValidationProcess? = getValidationProcess(certificateId)
                if (mvp != null) {
                    return deferred.resolve(mvp)
                } else if (getValidationProcess(certificate.address.v(), certificate.port.v()).also { mvp = it } != null) {
                    return deferred.resolve(mvp)
                }
            }
            run {
                val job = ConnectJob(certificateId, certificate.address.v(), certificate.port.v(), certificate.certDeliveryPort.v(), false)
                job.done { result: MeinValidationProcess ->
                    // use a new transaction, because we want connect in parallel.
                    T.lockingTransaction(this)
                            .run {
                                removeCurrentlyConnecting(certificateId)
                                removeCurrentlyConnecting(certificate.address.v(), certificate.port.v(), certificate.certDeliveryPort.v())
                            }
                            .end()
                    deferred.resolve(result)
                }.fail { result: Exception ->
                    T.lockingTransaction(this)
                            .run {
                                removeCurrentlyConnecting(certificateId)
                                removeCurrentlyConnecting(certificate.address.v(), certificate.port.v(), certificate.certDeliveryPort.v())
                            }
                            .end()
                    deferred.reject(result)
                }
                val meinAuthSocket = MeinAuthSocket(meinAuthService, job)
                currentlyConnecting(certificateId, meinAuthSocket)
                currentlyConnecting(certificate.address.v(), certificate.port.v(), certificate.certDeliveryPort.v(), meinAuthSocket)
                meinAuthService.execute(ConnectWorker(meinAuthService, job))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            transaction?.end()
        }
        return deferred
    }

    @Throws(InterruptedException::class)
    internal fun connect(address: String, port: Int, portCert: Int, regOnUnkown: Boolean): Promise<MeinValidationProcess, Exception, Void> {
        val deferred = DeferredObject<MeinValidationProcess, Exception, Void>()
        var mvp: MeinValidationProcess
        var transaction: Transaction<*>? = null
        // there are two try catch blocks because the connection code might be interrupted and needs to end the transaction under any circumstances


        try {
            transaction = T.lockingTransaction(this)
            Lok.debug("connect to: $address,$port,$portCert,reg=$regOnUnkown")
            val def = isCurrentlyConnecting(address, port, portCert)
            if (def != null) {
                return def.connectJob
            }
            if (getValidationProcess(address, port).also { mvp = it } != null) {
                deferred.resolve(mvp)
            } else {
                val job = ConnectJob(null, address, port, portCert, regOnUnkown)
                job.done { result: MeinValidationProcess ->
                    removeCurrentlyConnecting(address, port, portCert)
                    registerValidationProcess(result)
                    deferred.resolve(result)
                }.fail { result: Exception ->
                    removeCurrentlyConnecting(address, port, portCert)
                    deferred.reject(result)
                }
                val meinAuthSocket = MeinAuthSocket(meinAuthService, job)
                currentlyConnecting(address, port, portCert, meinAuthSocket)
                meinAuthService.execute(ConnectWorker(meinAuthService, meinAuthSocket))
            }
        } finally {
            transaction!!.end()
        }
        return deferred
    }

    /**
     * @param validationProcess
     * @return true if [MeinValidationProcess] has been registered as the only one connected with its [Certificate]
     */
    internal fun registerValidationProcess(validationProcess: MeinValidationProcess): Boolean {
        if (validationProcess.isClosed) return false
        val transaction: Transaction<*> = T.lockingTransaction(this)
        try {
            val existingProcess = idValidateProcessMap[validationProcess.connectedId]
            if (existingProcess != null) {
                if (existingProcess.isClosed) Lok.error("an old socket was closed and somehow was not thrown away!") else Lok.error("an old socket is already present for id: " + validationProcess.connectedId)
                return false
            }
            idValidateProcessMap[validationProcess.connectedId] = validationProcess
            addressValidateProcessMap[validationProcess.addressString] = validationProcess
            return true
        } catch (e: Exception) {
            return false
        } finally {
            transaction.end()
        }
    }

    val validationProcesses: Collection<MeinValidationProcess>?
        get() = idValidateProcessMap.values

    fun getValidationProcess(certificateId: Long): MeinValidationProcess {
        return idValidateProcessMap[certificateId]!!
    }

    fun getValidationProcess(address: String, port: Int): MeinValidationProcess {
        val completeAddress = "$address:$port"
        return addressValidateProcessMap[completeAddress]!!
    }

    val connectedIds: List<Long>
        get() {
            val result: MutableList<Long> = ArrayList()
            for (mvp in idValidateProcessMap.values) result.add(mvp.connectedId)
            return result
        }

    fun removeValidationProcess(meinAuthSocket: MeinAuthSocket) {
        if (meinAuthSocket.process == null || meinAuthSocket.process !is MeinValidationProcess) return
        val process = meinAuthSocket.process as MeinValidationProcess
        if (addressValidateProcessMap[process.addressString] === process) addressValidateProcessMap.remove(process.addressString)
        if (idValidateProcessMap[process.connectedId] === process) idValidateProcessMap.remove(process.connectedId)
        if (currentlyConnectingAddresses.containsKey(process.addressString)
                && currentlyConnectingAddresses[process.addressString] === meinAuthSocket) currentlyConnectingAddresses.remove(meinAuthSocket)
        if (currentlyConnectingCertIds.containsKey(process.connectedId)
                && currentlyConnectingCertIds.get(meinAuthSocket) === meinAuthSocket) currentlyConnectingCertIds.remove(process.connectedId)
    }

    /**
     * checks whether or not you are currently connecting to that certificate
     *
     * @param certificateId
     * @return null if you don't
     */
    fun isCurrentlyConnecting(certificateId: Long): MeinAuthSocket {
        return currentlyConnectingCertIds[certificateId]!!
    }

    private fun uniqueAddress(address: String, port: Int, portCert: Int): String {
        return "$address;$port;$portCert"
    }

    /**
     * checks whether or not you are currently connecting to that address
     *
     * @param address
     * @param port
     * @param portCert
     * @return
     */
    fun isCurrentlyConnecting(address: String, port: Int, portCert: Int): MeinAuthSocket {
        val id = uniqueAddress(address, port, portCert)
        return currentlyConnectingAddresses[id]!!
    }

    fun removeCurrentlyConnecting(certificateId: Long) {
        currentlyConnectingCertIds.remove(certificateId)
    }

    fun removeCurrentlyConnecting(address: String, port: Int, portCert: Int) {
        currentlyConnectingAddresses.remove(uniqueAddress(address, port, portCert))
    }

    /**
     * remembers that you are currently connecting to certificate
     *
     * @param certificateId
     * @param meinAuthSocket
     */
    fun currentlyConnecting(certificateId: Long, meinAuthSocket: MeinAuthSocket) {
        currentlyConnectingCertIds[certificateId] = meinAuthSocket
    }

    /**
     * remembers that you are currently connecting to address
     *
     * @param address
     * @param port
     * @param portCert
     * @return
     */
    private fun currentlyConnecting(address: String, port: Int, portCert: Int, meinAuthSocket: MeinAuthSocket) {
        currentlyConnectingAddresses[uniqueAddress(address, port, portCert)] = meinAuthSocket
    }

    fun shutDown() {
        Lok.debug("attempting shutdown")
        val transaction: Transaction<*> = T.lockingTransaction(this)
        forEachAdvIgnorantly(currentlyConnectingAddresses) { stoppable: Stoppable?, index: Int?, s: String, meinAuthSocket: MeinAuthSocket -> if (meinAuthSocket.connectJob != null) meinAuthSocket.connectJob.reject(Exception("shutting down")) }
        forEachAdvIgnorantly(currentlyConnectingCertIds) { stoppable: Stoppable?, index: Int?, aLong: Long, meinAuthSocket: MeinAuthSocket -> if (meinAuthSocket.connectJob != null) meinAuthSocket.connectJob.reject(Exception("shutting down")) }
        currentlyConnectingAddresses.clear()
        currentlyConnectingCertIds.clear()
        transaction.end()
        Lok.debug("success")
    }

    fun onSocketClosed(meinAuthSocket: MeinAuthSocket) {
        var transaction: Transaction<*>? = null
        try {
            transaction = T.lockingTransaction(this)
// find the socket in the connected environment and remove it
            val connectJob: AConnectJob<*, *>? = meinAuthSocket.connectJob
            if (meinAuthSocket.isValidated && meinAuthSocket.process is MeinValidationProcess) {
                removeValidationProcess(meinAuthSocket)
            } else if (meinAuthSocket.process is MeinIsolatedFileProcess) {
                Lok.debug("continue here")
            } else if (connectJob != null) {
                if (connectJob.certificateId != null) {
                    r { removeCurrentlyConnecting(meinAuthSocket.connectJob.certificateId) }
                } else if (connectJob.address != null) {
                    r { removeCurrentlyConnecting(connectJob.address, connectJob.port, connectJob.portCert) }
                }
                transaction.end()
                oneLine {
                    if (connectJob.isPending) {
                        connectJob.reject(Exception("connection closed"))
                    }
                }
            }
        } finally {
            transaction?.end()
        }
    }

    companion object {
        private val debug_count = AtomicInteger(0)
    }

}