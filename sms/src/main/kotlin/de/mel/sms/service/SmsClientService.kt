package de.mel.sms.service

import de.mel.Lok
import de.mel.auth.data.MelServiceProperties
import de.mel.auth.data.ServicePayload
import de.mel.auth.data.db.Certificate
import de.mel.auth.jobs.Job
import de.mel.auth.service.MelAuthService
import de.mel.auth.socket.process.`val`.Request
import de.mel.auth.tools.N
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer
import de.mel.sms.crypto.SmsCrypto
import de.mel.sms.data.EncryptedSmsRecord
import de.mel.sms.data.SmsBatchPayload
import de.mel.sms.data.SmsPlaintext
import de.mel.sms.data.SmsSettings
import de.mel.sms.data.SmsStrings
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class SmsClientService(
    melAuthService: MelAuthService,
    workingDirectory: File,
    serviceTypeId: Long,
    uuid: String,
    settingsCfg: SmsSettings?
) : SmsService(melAuthService, workingDirectory, serviceTypeId, uuid, settingsCfg) {

    private class PushJob(val messages: List<SmsPlaintext>) : Job<Void, Exception>()
    private class PullJob : Job<Void, Exception>()
    private class ShareSecretJob : Job<Void, Exception>()

    override fun createExecutorService(threadFactory: ThreadFactory): ExecutorService =
        Executors.newSingleThreadExecutor()

    override fun getServiceProperties(): MelServiceProperties<*>? = null

    override fun connectionAuthenticated(partnerCertificate: Certificate) {
        if (settings.serverCertId != null && partnerCertificate.id.equalsValue(settings.serverCertId)) {
            pullFromServer()
        }
    }

    override fun handleRequest(request: Request<*>) {
        request.resolveError(UnsupportedOperationException("Client does not accept requests"))
    }

    override fun handleMessage(payload: ServicePayload, partnerCertificate: Certificate) {
        if (payload.intent == SmsStrings.INTENT_NEW_MESSAGES) {
            pullFromServer()
        }
    }

    fun pushPlaintextMessages(messages: List<SmsPlaintext>) {
        if (messages.isEmpty()) return
        addJob(PushJob(messages))
    }

    fun pullFromServer() {
        addJob(PullJob())
    }

    fun shareSecretWithServerNow() {
        addJob(ShareSecretJob())
    }

    fun updateSecret(secret: String, shareWithServer: Boolean) {
        settings.secret = secret.takeIf { it.isNotBlank() }
        settings.shareSecretWithServer = shareWithServer
        settings.save()
        if (shareWithServer && !settings.secret.isNullOrBlank()) {
            shareSecretWithServerNow()
        }
        notifySmsChanged()
    }

    override fun workWork(job: Job<*, *>) {
        val serverCertId = settings.serverCertId ?: return
        val serverUuid = settings.serverServiceUuid ?: return

        when (job) {
            is PushJob -> {
                val secret = settings.secret
                if (secret.isNullOrBlank()) {
                    Lok.warn("SmsClientService: cannot push SMS without secret")
                    return
                }
                val key = SmsCrypto.resolveKey(secret)
                val batch = SmsBatchPayload(SmsStrings.INTENT_PUSH_BATCH)
                var maxDate = settings.lastSyncedDateMs
                for (msg in job.messages) {
                    val json = SerializableEntitySerializer.serialize(msg)
                    val (iv, cipher) = SmsCrypto.encrypt(key, json)
                    val hash = SmsCrypto.hmacHash(key, msg.canonicalKey())
                    val rec = EncryptedSmsRecord(null, hash, iv, cipher, msg.dateMs)
                    databaseManager.smsDao.insertIfAbsent(rec)
                    batch.messages.add(rec)
                    if (msg.dateMs > maxDate) maxDate = msg.dateMs
                }
                melAuthService.connect(serverCertId).onDone { mvp ->
                    N.r {
                        mvp.request(serverUuid, batch).onDone {
                            settings.lastSyncedDateMs = maxDate
                            settings.save()
                            notifySmsChanged()
                        }
                    }
                }
            }

            is PullJob -> {
                val query = SmsBatchPayload(SmsStrings.INTENT_PULL_SINCE)
                query.sinceId = settings.lastPulledServerId
                melAuthService.connect(serverCertId).onDone { mvp ->
                    N.r {
                        mvp.request(serverUuid, query).onDone { resp ->
                            val batch = resp as? SmsBatchPayload
                            if (batch != null && batch.messages.isNotEmpty()) {
                                var maxId = settings.lastPulledServerId
                                var changed = false
                                for (rec in batch.messages) {
                                    if (databaseManager.smsDao.insertIfAbsent(rec)) {
                                        changed = true
                                    }
                                    val sid = rec.serverId ?: 0L
                                    if (sid > maxId) maxId = sid
                                }
                                settings.lastPulledServerId = maxId
                                settings.save()
                                if (changed) notifySmsChanged()
                            }
                        }
                    }
                }
            }

            is ShareSecretJob -> {
                val secret = settings.secret ?: return
                val payload = SmsBatchPayload(SmsStrings.INTENT_SHARE_SECRET)
                payload.secret = secret
                melAuthService.connect(serverCertId).onDone { mvp ->
                    N.r { mvp.request(serverUuid, payload) }
                }
            }
        }
    }
}
