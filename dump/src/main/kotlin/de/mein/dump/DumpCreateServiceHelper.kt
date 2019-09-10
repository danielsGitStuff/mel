package de.mein.dump

import de.mein.auth.data.db.Service
import de.mein.auth.data.db.ServiceType
import de.mein.auth.file.AFile
import de.mein.auth.service.MeinAuthService
import de.mein.core.serialize.exceptions.JsonDeserializationException
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.drive.data.DriveSettings
import de.mein.drive.data.DriveStrings
import de.mein.drive.data.fs.RootDirectory
import de.mein.sql.SqlQueriesException
import java.io.File
import java.io.IOException
import java.sql.SQLException

class DumpCreateServiceHelper(val meinAuthService: MeinAuthService) {

    @Throws(SqlQueriesException::class)
    private fun createDbService(name: String?): Service {
        val type: ServiceType = meinAuthService.getDatabaseManager().getServiceTypeByName(DumpBootloader().name)
        return meinAuthService.getDatabaseManager().createService(type.id.v(), name)
    }

    @Throws(SqlQueriesException::class, IllegalAccessException::class, JsonSerializationException::class, JsonDeserializationException::class, InstantiationException::class, SQLException::class, IOException::class, ClassNotFoundException::class)
    fun createDumpService(dumpSettings: DriveSettings, name: String) {
        val service = createDbService(name)
        val transferDir: AFile<*>? = AFile.instance(dumpSettings!!.rootDirectory.originalFile, DriveStrings.TRANSFER_DIR)
        transferDir!!.mkdirs()
        dumpSettings.transferDirectory = transferDir
        val instanceWorkingDir = meinAuthService.meinBoot.createServiceInstanceWorkingDir(service)
        instanceWorkingDir.mkdirs()
        val settingsFile = File(instanceWorkingDir, DumpStrings.SETTINGS_FILE_NAME)
        dumpSettings.setJsonFile(settingsFile)
        dumpSettings.save()
        meinAuthService.meinBoot.bootServices()
    }

    @Throws(SqlQueriesException::class, IllegalAccessException::class, JsonSerializationException::class, JsonDeserializationException::class, ClassNotFoundException::class, SQLException::class, InstantiationException::class, IOException::class, InterruptedException::class)
    fun createDumpSourceService(name: String, rootFile: AFile<*>, certId: Long, serviceUuid: String, wastebinRatio: Float, maxDays: Long, useSymLinks: Boolean) {
        val rootDirectory: RootDirectory? = DriveSettings.buildRootDirectory(rootFile)
        val dumpSettings = DriveSettings().setRole(DriveStrings.ROLE_CLIENT).setRootDirectory(rootDirectory)
        dumpSettings!!.transferDirectory = AFile.instance(rootDirectory!!.originalFile, DriveStrings.TRANSFER_DIR)
        dumpSettings.maxWastebinSize = (dumpSettings.rootDirectory.originalFile.usableSpace * wastebinRatio).toLong()
        dumpSettings.maxAge = maxDays
        dumpSettings.useSymLinks = useSymLinks
        dumpSettings.clientSettings.initFinished = false
        dumpSettings.clientSettings.serverCertId = certId
        dumpSettings.clientSettings.serverServiceUuid = serviceUuid
        createDumpService(dumpSettings, name!!)
    }

    @Throws(SqlQueriesException::class, IllegalAccessException::class, JsonSerializationException::class, JsonDeserializationException::class, InstantiationException::class, SQLException::class, IOException::class, ClassNotFoundException::class)
    fun createDumpTargetService(name: String, rootFile: AFile<*>, wastebinRatio: Float, maxDays: Long, useSymLinks: Boolean) {
        val rootDirectory: RootDirectory? = DriveSettings.buildRootDirectory(rootFile)
        val driveSettings = DriveSettings()
                .setRole(DriveStrings.ROLE_SERVER)
                .setRootDirectory(rootDirectory)
                .setMaxAge(maxDays)
                .setUseSymLinks(useSymLinks)
        val transferDir: AFile<*>? = AFile.instance(rootDirectory!!.originalFile, DriveStrings.TRANSFER_DIR)
        transferDir!!.mkdirs()
        driveSettings!!.transferDirectory = transferDir
        driveSettings.maxWastebinSize = (driveSettings.rootDirectory.originalFile.usableSpace * wastebinRatio).toLong()
        createDumpService(driveSettings, name!!)
    }
}