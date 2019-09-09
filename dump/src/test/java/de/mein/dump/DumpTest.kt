package de.mein.dump

import de.mein.Lok
import de.mein.auth.data.MeinAuthSettings
import de.mein.auth.file.AFile
import de.mein.auth.file.DefaultFileConfiguration
import de.mein.auth.service.DriveTest
import de.mein.auth.service.MeinAuthService
import de.mein.auth.service.MeinBoot
import de.mein.auth.service.power.PowerManager
import de.mein.drive.DriveBootloader
import de.mein.drive.bash.BashTools
import de.mein.drive.data.DriveSettings
import de.mein.drive.data.fs.RootDirectory
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DumpTest {

    var mb1: MeinBoot? = null
    var mb2: MeinBoot? = null
    var s1: MeinAuthSettings? = null
    var s2: MeinAuthSettings? = null
    var r1: RootDirectory? = null
    val testDir = File("dump.test")
    val testTarget = File(testDir, "target")

    @Before
    fun setUp() {
        AFile.configure(DefaultFileConfiguration())
        BashTools.init()
        BashTools.rmRf(AFile.instance(testDir))
        BashTools.rmRf(AFile.instance(MeinBoot.defaultWorkingDir1))
        testDir.mkdirs()
        val rTarget = AFile.instance(testTarget)
        r1 = DriveSettings.buildRootDirectory(rTarget)
        s1 = DriveTest.createJson1()
        s2 = DriveTest.createJson2()
        mb1 = MeinBoot(s1!!, PowerManager(s1), DumpBootloader::class.java)
        mb2 = MeinBoot(s2!!, PowerManager(s2), DumpBootloader::class.java)
        mb1!!.boot().done { mas1: MeinAuthService? ->
            val helper = DumpCreateServiceHelper(mas1!!)
            helper.createDumpTargetService("target service", rTarget, .5f, 30L, false)
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