package de.mel.auth.service

import de.mel.Lok
import de.mel.auth.data.MelAuthSettings
import de.mel.auth.data.MelRequest
import de.mel.auth.data.db.Certificate
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.DefaultFileConfiguration
import de.mel.auth.service.power.PowerManager
import de.mel.auth.socket.process.reg.IRegisterHandler
import de.mel.auth.socket.process.reg.IRegisterHandlerListener
import de.mel.auth.socket.process.reg.IRegisteredHandler
import de.mel.auth.tools.N
import de.mel.filesync.FileSyncBootloader
import de.mel.filesync.bash.BashTools
import de.mel.filesync.data.FileSyncSettings
import de.mel.filesync.data.fs.RootDirectory
import de.mel.filesync.serialization.TestDirCreator
import de.mel.dump.DumpBootloader
import de.mel.dump.DumpCreateServiceHelper
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.io.File

class DumpTest {

    var mb1: MelBoot? = null
    var mb2: MelBoot? = null
    var s1: MelAuthSettings? = null
    var s2: MelAuthSettings? = null
    var r1: RootDirectory? = null
    var r2: RootDirectory? = null
    val testDir = File("dump.test")
    val testTarget = File(testDir, "target")
    val testSource = File(testDir, "source")

    @Before
    fun setUp() {
        AbstractFile.configure(DefaultFileConfiguration())
        BashTools.init()
        BashTools.rmRf(AbstractFile.instance(testDir))
        BashTools.rmRf(AbstractFile.instance(MelBoot.defaultWorkingDir1))
        BashTools.rmRf(AbstractFile.instance(MelBoot.defaultWorkingDir2))

        testDir.mkdirs()
        testTarget.mkdirs()
        testSource.mkdirs()
        TestDirCreator.createFilesTestDir(AbstractFile.instance(testSource), 0)
        val rTarget = AbstractFile.instance(testTarget)
        val rSource = AbstractFile.instance(testSource)
        r1 = FileSyncSettings.buildRootDirectory(rTarget)
        r2 = FileSyncSettings.buildRootDirectory(rSource)
        s1 = DriveTest.createJson1()
        s2 = DriveTest.createJson2()
        mb1 = MelBoot(s1!!, PowerManager(s1), DumpBootloader::class.java)
        mb2 = MelBoot(s2!!, PowerManager(s2), DumpBootloader::class.java)
        mb1!!.boot().done { mas1: MelAuthService ->
            val help1 = DumpCreateServiceHelper(mas1)
            mas1.addRegisterHandler(createRegisterHandler())
            mas1.addRegisteredHandler(createRegisteredHandler())

            FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = FileSyncBootloader.DEV_DriveBootListener {
                N.r {
                    val targetUUID = mas1.databaseManager.allServices.first().uuid.v()

                    mb2!!.boot().done { mas2: MelAuthService ->
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

    private fun createRegisteredHandler(): IRegisteredHandler = IRegisteredHandler { melAuthService, registered ->
        melAuthService.databaseManager.allServices.forEach {
            melAuthService.databaseManager.grant(it.serviceId.v(), registered.id.v())
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

        override fun acceptCertificate(listener: IRegisterHandlerListener, request: MelRequest, myCertificate: Certificate, certificate: Certificate) {
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