import de.mein.auth.MeinNotification
import de.mein.auth.data.db.Certificate
import de.mein.auth.jobs.Job
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinService
import de.mein.auth.socket.process.`val`.Request
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

abstract class CalendarService(meinAuthService: MeinAuthService, serviceInstaceWorkingDirectory: File, serviceTypeId: Long, uuid: String) : MeinService(meinAuthService, serviceInstaceWorkingDirectory, serviceTypeId, uuid) {
    override fun handleRequest(request: Request<*>?) {
    }

    override fun handleMessage(payload: ServicePayload?, partnerCertificate: Certificate?, intent: String?) {
    }

    override fun onServiceRegistered() {
    }

    override fun onCommunicationsEnabled() {
    }

    override fun connectionAuthenticated(partnerCertificate: Certificate?) {
    }


    override fun handleCertificateSpotted(partnerCertificate: Certificate?) {
    }

    override fun onIsolatedConnectionEstablished(isolatedProcess: MeinIsolatedProcess?) {
    }

    override fun createSendingNotification(): MeinNotification? = null

    override fun onCommunicationsDisabled() {
    }
}

class CalendarServerService(meinAuthService: MeinAuthService, serviceInstaceWorkingDirectory: File, serviceTypeId: Long, uuid: String, val calendarSettings: CalendarSettings<*>) : CalendarService(meinAuthService, serviceInstaceWorkingDirectory, serviceTypeId, uuid) {
    override fun onBootLevel2Finished() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBootLevel1Finished() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun workWork(job: Job<*, *, *>?) {
    }

    override fun createExecutorService(threadFactory: ThreadFactory?): ExecutorService? = Executors.newSingleThreadExecutor()

}

class CalendarClientService(meinAuthService: MeinAuthService, serviceInstaceWorkingDirectory: File, serviceTypeId: Long, uuid: String, val calendarSettings: CalendarSettings<*>) : CalendarService(meinAuthService, serviceInstaceWorkingDirectory, serviceTypeId, uuid) {
    override fun onBootLevel2Finished() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBootLevel1Finished() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun workWork(job: Job<*, *, *>?) {

    }

    override fun createExecutorService(threadFactory: ThreadFactory?): ExecutorService? = null
}