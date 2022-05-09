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
import de.mel.auth.tools.MelDeferredManager
import de.mel.sql.SqlQueriesException

import org.jdeferred.Promise
import org.jdeferred.impl.DeferredObject

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
    private val deferredObject: DeferredObject<MelAuthService, Exception, Void>
    private var melAuthService: MelAuthService? = null
    private val melAuthAdmins = ArrayList<MelAuthAdmin>()


    init {
        this.deferredObject = DeferredObject()
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
    fun boot(): Promise<MelAuthService, Exception, Void> {
        execute(this)
        return deferredObject
    }

    private val outstandingBootloaders = Collections.synchronizedSet(mutableSetOf<Bootloader<out MelService>>())!!
    override fun run() {
        try {
            powerManager.addStateListener(PowerManager.IPowerStateListener {
                bootStage2()
            })
            melAuthService = MelAuthServiceImpl(melAuthSettings, powerManager)
            melAuthService!!.melBoot = this
            val promiseAuthIsUp = melAuthService!!.prepareStart()
            promiseAuthIsUp.done { result -> deferredObject.resolve(melAuthService) }
            for (bootClass in this.bootloaderClasses) {
                createBootLoader(melAuthService, bootClass)
            }
            melAuthService!!.addAllMelAuthAdmin(melAuthAdmins)
            melAuthService!!.start()
            bootServices()
        } catch (e: Exception) {
            e.printStackTrace()
            deferredObject.reject(e)
        }
    }

    @Synchronized
    private fun bootStage2() {
        if (melAuthService!!.powerManager.heavyWorkAllowed()) {
            outstandingBootloaders.forEach { bootloader ->
                try {
                    bootloader.bootLevelLong()
                            .fail({ handleBootError(bootloader, it) })
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
        val bootedPromises = ArrayList<Promise<*, *, *>>()
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

        MelDeferredManager().`when`(bootedPromises)
                .done {
                    // boot stage2 of all services
                    bootStage2()
                }.fail { Lok.error("MelBoot.run.AT LEAST ONE SERVICE FAILED TO BOOT") }
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
