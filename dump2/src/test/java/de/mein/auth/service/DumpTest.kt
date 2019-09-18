package de.mein.auth.service

import de.mein.Lok
import de.mein.auth.data.MeinAuthSettings
import de.mein.auth.data.MeinRequest
import de.mein.auth.data.db.Certificate
import de.mein.auth.file.AFile
import de.mein.auth.file.DefaultFileConfiguration
import de.mein.auth.service.power.PowerManager
import de.mein.auth.socket.process.reg.IRegisterHandler
import de.mein.auth.socket.process.reg.IRegisterHandlerListener
import de.mein.auth.socket.process.reg.IRegisteredHandler
import de.mein.auth.tools.N
import de.mein.drive.DriveBootloader
import de.mein.drive.bash.BashTools
import de.mein.drive.data.DriveSettings
import de.mein.drive.data.fs.RootDirectory
import de.mein.dump.DumpBootloader
import de.mein.dump.DumpCreateServiceHelper
import org.junit.After
import org.junit.Before

import org.junit.Test
import java.io.File
import kotlin.concurrent.thread

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
        BashTools.rmRf(AFile.instance(MeinBoot.defaultWorkingDir2))

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

            DriveBootloader.DEV_DRIVE_BOOT_LISTENER = DriveBootloader.DEV_DriveBootListener {
                N.r {
                    val targetUUID = mas1.databaseManager.allServices.first().uuid.v()

                    mb2!!.boot().done { mas2: MeinAuthService ->
                        mas2.addRegisterHandler(createRegisterHandler())
                        mas2.addRegisteredHandler { _, registered ->
                            mas2.connect("localhost", s1!!.port, s1!!.deliveryPort, false).done { mvp ->
                                Thread {
                                    Lok.debug("ok23")
                                    val help2 = DumpCreateServiceHelper(mas2)
                                    help2.createClientService("source", rSource, 1L, targetUUID, 0.5f, 30, false)
                                }.start()
                            }.fail {
                                Lok.error("FAIL")
                            }
                        }

                        Lok.debug("con23")
                        N.r { mas2.connect("localhost", s1!!.port, s1!!.deliveryPort, true) }

                    }
                }
            }

            help1.createServerService("target service", rTarget, .5f, 30L, false)


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
            Lok.debug("accepted!")
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