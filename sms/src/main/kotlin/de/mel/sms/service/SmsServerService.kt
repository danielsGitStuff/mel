package de.mel.sms.service

import de.mel.auth.data.MelServiceProperties
import de.mel.auth.data.ServiceDetails
import de.mel.auth.data.ServicePayload
import de.mel.auth.data.db.Certificate
import de.mel.auth.jobs.Job
import de.mel.auth.jobs.ServiceRequestHandlerJob
import de.mel.auth.service.MelAuthService
import de.mel.auth.socket.process.`val`.Request
import de.mel.auth.tools.N
import de.mel.sms.data.SmsBatchPayload
import de.mel.sms.data.SmsSettings
import de.mel.sms.data.SmsStrings
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class SmsServerService(
    melAuthService: MelAuthService,
    workingDirectory: File,
    serviceTypeId: Long,
    uuid: String,
    settingsCfg: SmsSettings?
) : SmsService(melAuthService, workingDirectory, serviceTypeId, uuid, settingsCfg) {

    override fun createExecutorService(threadFactory: ThreadFactory): ExecutorService =
        Executors.newSingleThreadExecutor()

    override fun getServiceProperties(): MelServiceProperties<*>? = null

    override fun handleRequest(request: Request<*>) {
        addJob(ServiceRequestHandlerJob().setRequest(request))
    }

    override fun handleMessage(payload: ServicePayload, partnerCertificate: Certificate) {
        addJob(
            ServiceRequestHandlerJob()
                .setPayload(payload)
                .setPartnerCertificate(partnerCertificate)
                .setIntent(payload.intent)
        )
    }

    override fun workWork(job: Job<*, *>) {
        if (job !is ServiceRequestHandlerJob) return
        val req = job.request ?: return

        when {
            req.hasIntent(SmsStrings.INTENT_REG_AS_CLIENT) -> {
                val details = req.payload as ServiceDetails
                settings.addClient(job.partnerCertificate.id.v(), details.serviceUuid)
                settings.save()
                req.resolveOk(null)
            }

            req.hasIntent(SmsStrings.INTENT_PUSH_BATCH) -> {
                val batch = req.payload as SmsBatchPayload
                var inserted = 0
                for (rec in batch.messages) {
                    if (databaseManager.smsDao.insertIfAbsent(rec)) {
                        inserted++
                    }
                }
                req.resolveOk(null)
                if (inserted > 0) {
                    notifySmsChanged()
                    propagateNewMessages(job.partnerCertificate?.id?.v())
                }
            }

            req.hasIntent(SmsStrings.INTENT_PULL_SINCE) -> {
                val query = req.payload as SmsBatchPayload
                val rows = databaseManager.smsDao.loadSince(query.sinceId)
                val resp = SmsBatchPayload(SmsStrings.INTENT_PULL_SINCE)
                resp.messages.addAll(rows)
                req.resolveOk(resp)
            }

            req.hasIntent(SmsStrings.INTENT_SHARE_SECRET) -> {
                val payload = req.payload as SmsBatchPayload
                if (!payload.secret.isNullOrBlank()) {
                    settings.secret = payload.secret
                    settings.save()
                    notifySmsChanged()
                    req.resolveOk(null)
                } else {
                    req.resolveError(IllegalArgumentException("Empty secret"))
                }
            }

            else -> req.resolveError(IllegalArgumentException("Unknown intent: ${req.payload?.intent}"))
        }
    }

    private fun propagateNewMessages(excludeCertId: Long?) {
        for (client in settings.clients) {
            if (excludeCertId != null && client.certId == excludeCertId) continue
            N.r {
                melAuthService.connect(client.certId).onDone { mvp ->
                    N.r {
                        mvp.message(client.serviceUuid, SmsBatchPayload(SmsStrings.INTENT_NEW_MESSAGES))
                    }
                }
            }
        }
    }

    fun setLocalSecret(secret: String?) {
        settings.secret = secret?.takeIf { it.isNotBlank() }
        settings.save()
        notifySmsChanged()
    }
}
