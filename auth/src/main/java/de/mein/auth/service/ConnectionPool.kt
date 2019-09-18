package de.mein.auth.service

import de.mein.auth.data.EmptyPayload
import de.mein.auth.data.ServicePayload
import de.mein.auth.jobs.ConnectJob
import de.mein.auth.socket.ConnectWorker
import de.mein.auth.tools.lock.T

class ConnectionPool(val meinAuthService: MeinAuthService) {
    val certIdWorkerMap = mutableMapOf<Long, ConnectWorker>()
    val addressWorkerMap = mutableMapOf<String, ConnectWorker>()

    fun connect(certId: Long): ConnectResult {
        val transaction = T.lockingTransaction(this)
        try {
            val existing = certIdWorkerMap[certId]
            // connection in progress or established
            if (existing != null) {
                return existing.connectResult
            } else {
                // need to connect
                try {
                    val cert = meinAuthService.certificateManager.getCertificateById(certId)
                    val connectionJob = ConnectJob(certId, cert.address.v(), cert.port.v(), cert.certDeliveryPort.v(), false)
                    val worker = ConnectWorker(meinAuthService, connectionJob = connectionJob)
                    certIdWorkerMap[certId] = worker
                    addressWorkerMap[worker.addressString] = worker
                    meinAuthService.execute(worker)
                    return worker.connectResult
                } catch (e: Exception) {
                    return FailedConnectResult(e)
                }
            }
        } finally {
            transaction.end()
        }
    }

    fun testi() {
        connect(1L).request("", EmptyPayload("")).onSuccess {

        }.onFail {

        }
        connect(1L).onAuthenticated { meinValidationProcess ->
            val request = meinValidationProcess.request("",EmptyPayload())

        }
    }
}