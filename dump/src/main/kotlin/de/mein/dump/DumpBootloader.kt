package de.mein.dump

import de.mein.Lok
import de.mein.auth.data.db.Service
import de.mein.auth.service.BootException
import de.mein.auth.service.Bootloader
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinService
import de.mein.auth.socket.MeinValidationProcess
import de.mein.auth.tools.CountdownLock
import de.mein.auth.tools.N
import de.mein.core.serialize.exceptions.JsonDeserializationException
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.drive.data.DriveClientSettingsDetails
import de.mein.drive.sql.DriveDatabaseManager
import de.mein.sql.SqlQueriesException
import org.jdeferred.Promise
import java.io.File
import java.io.IOException
import java.sql.SQLException

class DumpBootloader : Bootloader<MeinService>() {
    private var dumpService: MeinService? = null
    private var dumpSettings: DumpSettings? = null
    override fun bootLevelShortImpl(meinAuthService: MeinAuthService, serviceDescription: Service): MeinService {
        try {
            val jsonFile = File(bootLoaderDir.absolutePath + File.separator + serviceDescription.uuid.v().toString() + File.separator + DumpStrings.SETTINGS_FILE_NAME)
            dumpSettings = DumpSettings.load(jsonFile)
            dumpService = spawn(meinAuthService, serviceDescription, dumpSettings!!)
            if (dumpService == null) {
                Lok.error("Dump did not spawn!!!")
                throw Exception("Dump did not spawn!!!")
            }
            Lok.debug(meinAuthService.name + ", booted to level 1: " + dumpService!!::class.java.getSimpleName())
            meinAuthService.registerMeinService(dumpService!!)
        } catch (e: Exception) {
            e.printStackTrace()
            Lok.error(e.toString())
            Lok.error("///////")
            throw BootException(this, e)
        }
        return dumpService!!
    }

    override fun cleanUpDeletedService(meinService: MeinService?, uuid: String?) {

    }

    override fun getName(): String = DumpStrings.NAME

    override fun getDescription(): String {
        return DumpStrings.DESCRTIPTION
    }

    /**
     * boots one instance
     *
     * @param meinAuthService
     * @param service
     * @param dumpSettings
     * @return
     * @throws SqlQueriesException
     * @throws SQLException
     * @throws IOException
     * @throws JsonDeserializationException
     * @throws JsonSerializationException
     */
    @Throws(SqlQueriesException::class, SQLException::class, IOException::class, ClassNotFoundException::class, JsonDeserializationException::class, JsonSerializationException::class, IllegalAccessException::class)
    private fun spawn(meinAuthService: MeinAuthService, service: Service, dumpSettings: DumpSettings): MeinService {
        this.dumpSettings = dumpSettings
        val workingDirectory = File(bootLoaderDir, service.uuid.v())
        workingDirectory.mkdirs()
        dumpSettings.setJsonFile(File(workingDirectory, DumpStrings.SETTINGS_FILE_NAME))
        dumpSettings.save()
        val serviceTypeId: Long? = service.typeId.v()
        val uuid: String? = service.uuid.v()
        val dumpService = if (dumpSettings.isServer) TargetService(meinAuthService, workingDirectory, serviceTypeId!!, uuid!!, dumpSettings) else SourceService(meinAuthService, workingDirectory, serviceTypeId!!, uuid!!, dumpSettings)
        //exec


        meinAuthService.execute(dumpService)
        val workingDir = File(bootLoaderDir, dumpService.uuid)
        workingDir.mkdirs()
        //create cache dir


        File(workingDir.absolutePath + File.separator + "cache").mkdirs()
        val databaseManager = DriveDatabaseManager(dumpService, workingDir, dumpSettings)
        databaseManager.cleanUp()
        dumpService.driveDatabaseManager = databaseManager
        if (!dumpSettings.isServer && !dumpSettings.clientSettings.initFinished) N.r {
            //pair with server service
            val sourceService = dumpService as SourceService
            val clientSettings: DriveClientSettingsDetails = dumpSettings.clientSettings
            val certId: Long? = clientSettings.serverCertId
            val serviceUuid: String? = clientSettings.serverServiceUuid
            val lock = CountdownLock(1)

            // allow server service to talk to us


            meinAuthService.databaseManager.grant(service.id.v(), certId)
            val connected: Promise<MeinValidationProcess, java.lang.Exception, Void> = meinAuthService.connect(certId)
            val driveDetails: DumpDetails = DumpDetails().setRole(DumpStrings.ROLE_CLIENT).setLastSyncVersion(0).setServiceUuid(service.uuid.v())
                    .setUsesSymLinks(dumpSettings.useSymLinks) as DumpDetails
            driveDetails.intent = DumpStrings.INTENT_REG_AS_CLIENT
            connected.done { validationProcess: MeinValidationProcess ->
                N.r {
                    validationProcess.request(serviceUuid, driveDetails).done { result: Any? ->
                        N.r {
                            Lok.debug("Service created and paired")
                            clientSettings.initFinished = true
                            dumpSettings.save()
                            lock.unlock()
                        }
                    }
                }
            }.fail { result: java.lang.Exception ->
                N.r {
                    Lok.debug("DumpBootloader.createSource.FAIL")
                    result.printStackTrace()
                    sourceService.shutDown()
                    meinAuthService.databaseManager.revoke(service.id.v(), certId)
                    meinAuthService.deleteService(service.uuid.v())
                    lock.unlock()
                }
            }
            lock.lock()
        }
        return dumpService
    }

}