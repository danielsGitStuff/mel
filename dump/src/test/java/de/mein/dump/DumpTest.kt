package de.mein.dump

import de.mein.Lok
import de.mein.auth.MeinAuthAdmin
import de.mein.auth.MeinNotification
import de.mein.auth.data.MeinAuthSettings
import de.mein.auth.data.MeinRequest
import de.mein.auth.data.db.Certificate
import de.mein.auth.file.AFile
import de.mein.auth.file.DefaultFileConfiguration
import de.mein.auth.service.DriveTest
import de.mein.auth.service.IMeinService
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinBoot
import de.mein.auth.service.power.PowerManager
import de.mein.auth.socket.process.reg.IRegisterHandler
import de.mein.auth.socket.process.reg.IRegisterHandlerListener
import de.mein.auth.socket.process.reg.IRegisteredHandler
import de.mein.drive.bash.BashTools
import de.mein.drive.data.DriveSettings
import de.mein.drive.data.fs.RootDirectory
import org.junit.After
import org.junit.Before

import org.junit.Test
import java.io.File

class DumpTest {

    var mb1: MeinBoot? = null
    var mb2: MeinBoot? = null
    var s1: MeinAuthSettings? = null
    var s2: MeinAuthSettings? = null
    var r1: RootDirectory? = null
    var r2: RootDirectory? = null
    val testDir = File("dump.test")
    val testTarget = File(testDir, "target")
    val testSource = File(testDir, "source")

    @Before
    fun setUp() {
        AFile.configure(DefaultFileConfiguration())
        BashTools.init()
        BashTools.rmRf(AFile.instance(testDir))
        BashTools.rmRf(AFile.instance(MeinBoot.defaultWorkingDir1))
        testDir.mkdirs()
        testTarget.mkdirs()
        testSource.mkdirs()
        val rTarget = AFile.instance(testTarget)
        val rSource = AFile.instance(testSource)
        r1 = DriveSettings.buildRootDirectory(rTarget)
        r2 = DriveSettings.buildRootDirectory(rSource)
        s1 = DriveTest.createJson1()
        s2 = DriveTest.createJson2()
        mb1 = MeinBoot(s1!!, PowerManager(s1), DumpBootloader::class.java)
        mb2 = MeinBoot(s2!!, PowerManager(s2), DumpBootloader::class.java)
        mb1!!.boot().done { mas1: MeinAuthService ->
            val help1 = DumpCreateServiceHelper(mas1)
            mas1.addRegisterHandler(createRegisterHandler())
            mas1.addRegisteredHandler(createRegisteredHandler())
            help1.createDumpTargetService("target service", rTarget, .5f, 30L, false)

            val targetUUID = mas1.databaseManager.allServices.first().uuid.v()

            mb2!!.boot().done { mas2: MeinAuthService ->
                mas2.addRegisteredHandler(createRegisteredHandler())
                mas2.addRegisterHandler(createRegisterHandler())
                val help2 = DumpCreateServiceHelper(mas2)
                help2.createDumpSourceService("source", rSource, 1L, targetUUID, 0.5f, 30, false)
            }
        }
    }

    private fun createRegisteredHandler(): IRegisteredHandler = IRegisteredHandler { meinAuthService, registered ->
        meinAuthService.databaseManager.allServices.forEach {
            meinAuthService.databaseManager.grant(it.serviceId.v(), registered.id.v())
        }
    }

    private fun createRegisterHandler(): IRegisterHandler = object : IRegisterHandler {
        override fun onRegistrationCompleted(partnerCertificate: Certificate?) {

        }

        override fun onLocallyAccepted(partnerCertificate: Certificate?) {
        }

        override fun onRemoteAccepted(partnerCertificate: Certificate?) {

        }

        override fun onRemoteRejected(partnerCertificate: Certificate?) {

        }

        override fun acceptCertificate(listener: IRegisterHandlerListener, request: MeinRequest, myCertificate: Certificate, certificate: Certificate) {
            listener.onCertificateAccepted(request, certificate)
        }

        override fun onLocallyRejected(partnerCertificate: Certificate?) {

        }
    }

    @Test
    fun test() {
        Lok.debug("test")
        Lok.debug("DEADLOCKING HERE")
        Thread.currentThread().join()
    }

    @After
    fun tearDown() {

    }
}