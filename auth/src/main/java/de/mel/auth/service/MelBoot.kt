package de.mel.auth.service

import de.mel.Lok
import de.mel.MelRunnable
import de.mel.auth.MelAuthAdmin
import de.mel.auth.data.MelAuthSettings
import de.mel.auth.data.db.Service
import de.mel.auth.data.db.ServiceError
import de.mel.auth.data.db.ServiceType
import de.mel.auth.service.power.PowerManager
import de.mel.auth.tools.BackgroundExecutor
import de.mel.sql.SqlQueriesException

import `fun`.with.promise.Promise
import `fun`.with.promise.PromiseImpl

import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.logging.Logger

/**
 * Boots up the MelAuth instance and all existing services by calling the corresponding bootloaders.
 */
class MelBoot(private val melAuthSettings: MelAuthSettings, private val powerManager: PowerManager, vararg bootloaderClasses: Class<out Bootloader<out MelService>>) : BackgroundExecutor(), MelRunnable {
    private val bootloaderClasses = HashSet<Class<out Bootloader<out MelService>>>()
    private val bootloaderMap = HashMap<String, Class<out Bootloader<out MelService>>>()
    private val deferredObject: PromiseImpl<MelAuthService, Exception>
    private var melAuthService: MelAuthService? = null
    private val melAuthAdmins = ArrayList<MelAuthAdmin>()


    init {
        this.deferredObject = PromiseImpl.withoutThrowingOnException()
        this.bootloaderClasses.addAll(Arrays.asList(*bootloaderClasses))
    }

    fun addMelAuthAdmin(admin: MelAuthAdmin): MelBoot {
        melAuthAdmins.add(admin)
        return this
    }


    fun addBootLoaderClass(clazz: Class<out Bootloader<out MelService>>): MelBoot {
        bootloaderClasses.add(clazz)
        return this
    }

    fun getBootloaderMap(): Map<String, Class<out Bootloader<out MelService>>> {
        return bootloaderMap
    }

    fun getBootloaderClasses(): Set<Class<out Bootloader<out MelService>>> {
        return bootloaderClasses
    }


    @Throws(Exception::class)
    fun boot(): Promise<MelAuthService, Exception> {
        execute(this)
        return deferredObject
    }

    @Volatile
    private var startFailed = false
    private val outstandingBootloaders = Collections.synchronizedSet(mutableSetOf<Bootloader<out MelService>>())!!
    override fun run() {
        try {
            powerManager.addStateListener(PowerManager.IPowerStateListener {
                bootStage2()
            })
            melAuthService = MelAuthServiceImpl(melAuthSettings, powerManager)
            melAuthService!!.melBoot = this
            val promiseAuthIsUp = melAuthService!!.prepareStart()
            promiseAuthIsUp.onDone { result -> settle { deferredObject.resolveOk(melAuthService) } }
            for (bootClass in this.bootloaderClasses) {
                createBootLoader(melAuthService, bootClass)
            }
            melAuthService!!.addAllMelAuthAdmin(melAuthAdmins)
            melAuthService!!.start()
            bootServices()
            // attached after the services were booted, so the shut down below gets them too
            promiseAuthIsUp.onError { e -> failed(e) }
        } catch (e: Exception) {
            e.printStackTrace()
            failed(e)
        }
    }

    /**
     * Settles the boot promise once: a failure after the instance was handed out is not a boot failure any more.
     */
    private fun settle(how: () -> Unit) {
        synchronized(deferredObject) {
            if (deferredObject.isPending)
                how()
        }
    }

    /**
     * The instance did not come up. It is shut down (whatever was bound is freed, the database is closed, booted services stop)
     * before the caller of [boot] learns why, so the caller can start again right away.
     */
    private fun failed(e: Exception) {
        if (!deferredObject.isPending)
            return
        Lok.error("MelBoot: the instance could not start: ${e.message}")
        synchronized(this) {
            // bootStage2 is synchronized on this as well: nothing gets to the long boot level from here on
            startFailed = true
            outstandingBootloaders.clear()
        }
        val service = melAuthService
        if (service == null) {
            settle { deferredObject.resolveError(e) }
            return
        }
        // not on a thread of the instance, its executors are shut down
        Thread({
            val shutDown = try {
                service.shutDown()
            } catch (t: Exception) {
                Lok.error("MelBoot: shutting down the instance that could not start failed: $t")
                null
            }
            if (shutDown == null)
                settle { deferredObject.resolveError(e) }
            else
                shutDown.onDone { settle { deferredObject.resolveError(e) } }
                        .onError { settle { deferredObject.resolveError(e) } }
        }, "MelBoot failed start of ${melAuthSettings.name}").start()
    }

    @Synchronized
    private fun bootStage2() {
        if (startFailed)
            return
        if (melAuthService!!.powerManager.heavyWorkAllowed()) {
            outstandingBootloaders.forEach { bootloader ->
                try {
                    bootloader.bootLevelLong()
                            .onError({ handleBootError(bootloader, it) })
                } catch (e: BootException) {
                    handleBootError(bootloader, e)
                }
                //?.always { _, _, _ -> outstandingBootloaders.remove(bootloader) }
            }
            outstandingBootloaders.clear()
        }
    }

    private fun handleBootError(service: Service, e: BootException) {
        Lok.error("Service ${service.typeId}/${service.name} failed to boot")
        Lok.error("Exception: ${e.cause} ... ${e.message}")
        Lok.stacktTrace(e.stackTrace)
        service.lastError = ServiceError(e)
        melAuthService!!.databaseManager!!.updateService(service)
    }

    private fun handleBootError(bootloader: Bootloader<*>, e: BootException) {
        melAuthService?.unregisterMelService(bootloader.melService.uuid)
        val service = melAuthService!!.databaseManager!!.getServiceByUuid(bootloader.melService!!.uuid)
        handleBootError(service, e)
    }


    @Throws(SqlQueriesException::class, IllegalAccessException::class, InstantiationException::class)
    fun createBootLoader(melAuthService: MelAuthService?, bootClass: Class<out Bootloader<out MelService>>): Bootloader<out MelService> {
        val bootLoader = bootClass.newInstance()
        bootloaderMap[bootLoader.name] = bootClass
        bootLoader.setMelAuthService(melAuthService)
        val databaseManager = melAuthService!!.databaseManager
        var serviceType: ServiceType? = databaseManager.getServiceTypeByName(bootLoader.name)
        if (serviceType == null) {
            serviceType = databaseManager.createServiceType(bootLoader.name, bootLoader.description)
        }
        bootLoader.setTypeId(serviceType!!.id.v())
        val serviceTypesDir = File(melAuthService.workingDirectory, "servicetypes")
        serviceTypesDir.mkdirs()
        val bootDir = File(serviceTypesDir, serviceType.type.v())
        bootDir.mkdirs()
        bootLoader.setBootLoaderDir(bootDir)
        return bootLoader
    }

    @Throws(IllegalAccessException::class, SqlQueriesException::class, InstantiationException::class)
    fun getBootLoader(typeName: String): Bootloader<out MelService> {
        var bootClazz: Class<out Bootloader<out MelService>>? = bootloaderMap[typeName]
        val bootLoader = createBootLoader(melAuthService, bootClazz!!)
        return bootLoader
    }

    override fun getRunnableName(): String {
        return javaClass.simpleName
    }


    override fun createExecutorService(threadFactory: ThreadFactory): ExecutorService {
        return Executors.newCachedThreadPool(threadFactory)
    }

    @Throws(SqlQueriesException::class, InstantiationException::class, IllegalAccessException::class)
    fun getBootLoader(melService: IMelService): Bootloader<out MelService> {
        val typeName = melAuthService!!.databaseManager.getServiceNameByServiceUuid(melService.uuid)
        return getBootLoader(typeName)
    }

    /**
     * boots every service that is not booted yet and marked active.
     * booting happens in two steps and in parallel. level 2 starts heavy work if necessary
     */
    fun bootServices() {
        val bootedPromises = ArrayList<Promise<*, *>>()
        for (bootClass in bootloaderClasses) {
            Lok.debug("MelBoot.boot.booting: " + bootClass.canonicalName)
            val dummyBootloader = createBootLoader(melAuthService, bootClass)
            val services = melAuthService!!.databaseManager.getActiveServicesByType(dummyBootloader.getTypeId())
            services.filter { service -> melAuthService!!.getMelService(service.uuid.v()) == null }.forEach { service ->
                try {
                    val bootloader = createBootLoader(melAuthService, bootClass)
                    val melService = bootloader.bootLevelShort(melAuthService, service)
                    if (melService.bootLevel == Bootloader.BootLevel.LONG) {
                        outstandingBootloaders += bootloader
                    }
                } catch (e: BootException) {
                    handleBootError(service, e)
                }
            }
        }
//        bootedPromises.add(promiseAuthIsUp);

        Promise.fusedAnd(*bootedPromises.toTypedArray())
                .onDone {
                    // boot stage2 of all services
                    bootStage2()
                }.onError { Lok.error("MelBoot.run.AT LEAST ONE SERVICE FAILED TO BOOT") }
    }

    /**
     * @param service the service database entry that you want the directory for
     * @return the working directory for the given service instance. it lives under /"MelAuthWorkingDir"/"ServiceName"/"ServiceUUID"
     */
    fun createServiceInstanceWorkingDir(service: Service): File {
        val serviceJoinServiceType = melAuthService!!.databaseManager.getServiceTypeById(service.typeId.v())
        val workingDir = melAuthService!!.workingDirectory.absoluteFile
        val serviceTypesDir = File(workingDir, "servicetypes")
        val serviceDir = File(serviceTypesDir, serviceJoinServiceType.type.v())
        return File(serviceDir, service.uuid.v())
    }

    fun onHeavyWorkAllowed() {
        Lok.debug("MelBoot boots stage 2 services")
        bootStage2()
    }

    companion object {
        val DEFAULT_WORKING_DIR_NAME = "mel.auth"
        val DEFAULT_SETTINGS_FILE_NAME = "auth.settings"
        private val logger = Logger.getLogger(MelBoot::class.java.name)
        val defaultWorkingDir1 = File("mel.data.1")
        val defaultWorkingDir2 = File("mel.data.2")

        @JvmStatic
        fun main(args: Array<String>) {
            // MelAuthService melAuthService =
        }
    }
}
