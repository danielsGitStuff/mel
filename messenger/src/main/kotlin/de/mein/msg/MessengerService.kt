package de.mein.msg

import de.mein.auth.MeinNotification
import de.mein.auth.data.ServicePayload
import de.mein.auth.data.db.Certificate
import de.mein.auth.jobs.Job
import de.mein.auth.service.Bootloader
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinServiceWorker
import de.mein.auth.socket.process.`val`.Request
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class MessengerService(meinAuthService: MeinAuthService, workingDir: File, typeId: Long, uuid: String) : MeinServiceWorker(meinAuthService, workingDir, typeId, uuid, Bootloader.BootLevel.SHORT) {
    override fun onBootLevel2Finished() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBootLevel1Finished() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun workWork(job: Job<*, *, *>?) {
    }

    override fun handleCertificateSpotted(partnerCertificate: Certificate?) {
    }

    override fun createSendingNotification(): MeinNotification {
        return MeinNotification(uuid,"none","No title","not text")
    }

    override fun onCommunicationsDisabled() {
    }

    override fun createExecutorService(threadFactory: ThreadFactory?): ExecutorService {
        return Executors.newCachedThreadPool()
    }

    override fun onCommunicationsEnabled() {
    }

    override fun onServiceRegistered() {

    }

    override fun connectionAuthenticated(partnerCertificate: Certificate?) {
    }

    override fun handleMessage(payload: ServicePayload?, partnerCertificate: Certificate?) {
    }

    override fun handleRequest(request: Request<*>?) {

    }

}