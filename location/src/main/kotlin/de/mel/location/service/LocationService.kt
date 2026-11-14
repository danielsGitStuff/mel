package de.mel.location.service

import de.mel.Lok
import de.mel.auth.MelNotification
import de.mel.auth.data.MelServiceProperties
import de.mel.auth.data.ServicePayload
import de.mel.auth.data.db.Certificate
import de.mel.auth.jobs.Job
import de.mel.auth.service.Bootloader
import de.mel.auth.service.MelAuthService
import de.mel.auth.service.MelService
import de.mel.auth.socket.process.`val`.Request
import de.mel.auth.socket.process.transfer.MelIsolatedProcess
import de.mel.auth.tools.N
import de.mel.location.data.LocationSettings
import de.mel.location.data.db.LocationDatabaseManager
import de.mel.location.data.db.LocationPoint
import de.mel.location.http.OwnTracksHttpServer
import `fun`.with.promise.Promise
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class LocationService(
    melAuthService: MelAuthService,
    workingDirectory: File,
    serviceTypeId: Long,
    uuid: String,
    settingsCfg: LocationSettings?
) : MelService(melAuthService, workingDirectory, serviceTypeId, uuid, Bootloader.BootLevel.SHORT) {

    val databaseManager = LocationDatabaseManager(workingDirectory, uuid, settingsCfg)
    val settings: LocationSettings = databaseManager.settings
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var httpServer: OwnTracksHttpServer? = null

    fun addOnChangedListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun startHttpEndpoint() {
        httpServer?.stop()
        val server = OwnTracksHttpServer(settings) { point ->
            if (databaseManager.locationDao.insertIfAbsent(point)) {
                listeners.forEach { N.r { it() } }
            }
        }
        try {
            server.start()
            httpServer = server
        } catch (e: Exception) {
            Lok.error("Failed to start OwnTracks HTTP server on port ${settings.port}: ${e.message}")
        }
    }

    fun updateSettings(port: Int, bindAddress: String, username: String, password: String) {
        val portChanged = settings.port != port || settings.bindAddress != bindAddress
        settings.port = port
        settings.bindAddress = bindAddress.ifBlank { "0.0.0.0" }
        settings.username = username
        settings.password = password
        settings.save()
        if (portChanged || httpServer == null) {
            startHttpEndpoint()
        }
    }

    fun loadRecent(limit: Int = 200): List<LocationPoint> = databaseManager.locationDao.loadRecent(limit)

    override fun onShutDown(): Promise<Void, Void>? {
        httpServer?.stop()
        return super.onShutDown()
    }

    override fun createExecutorService(threadFactory: ThreadFactory): ExecutorService =
        Executors.newSingleThreadExecutor()

    override fun getServiceProperties(): MelServiceProperties<*>? = null
    override fun handleRequest(request: Request<*>) {
        request.resolveError(UnsupportedOperationException("Use OwnTracks HTTP endpoint"))
    }
    override fun handleMessage(payload: ServicePayload, partnerCertificate: Certificate) {}
    override fun workWork(job: Job<*, *>) {}
    override fun connectionAuthenticated(partnerCertificate: Certificate) {}
    override fun handleCertificateSpotted(partnerCertificate: Certificate) {}
    override fun onServiceRegistered() {}
    override fun createSendingNotification(): MelNotification? = null
    override fun onCommunicationsDisabled() {}
    override fun onCommunicationsEnabled() {}
    override fun onIsolatedConnectionEstablished(isolatedProcess: MelIsolatedProcess) {}
    override fun onBootLevel1Finished() {
        startHttpEndpoint()
        N.r { resolveStarted() }
    }
    override fun onBootLevel2Finished() {}
}
