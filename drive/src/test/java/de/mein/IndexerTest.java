package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.N;
import de.mein.drive.DriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.sql.RWLock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IndexerTest {
    private AFile rootDir;

    @Before
    public void before() throws Exception {
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
        BashTools.rmRf(AFile.instance(MeinBoot.Companion.getDefaultWorkingDir1()));
        RWLock bootLock = new RWLock().lockWrite();
        rootDir = AFile.instance("indextest");
        TestDirCreator.createTestDir(rootDir);
        MeinAuthSettings meinAuthSettings = MeinAuthSettings.createDefaultSettings();
        MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveBootloader.class);
        meinBoot.boot().done(mas -> N.r(() -> {
            DriveSettings driveSettings = new DriveSettings()
                    .setRole(DriveStrings.ROLE_SERVER)
                    .setMaxAge(1000000L)
                    .setRootDirectory(new RootDirectory().setOriginalFile(rootDir).setPath(rootDir.getPath()))
                    .setTransferDirectory(AFile.instance(rootDir, "transfer"))
                    .setMaxWastebinSize(999999L)
                    .setFastBoot(true);
            new DriveCreateController(mas).createDriveService(driveSettings, "server");
            Thread.sleep(2000);
            bootLock.unlockWrite();
        }));
        bootLock.lockWrite();
    }

    @Test
    public void mixedSyncedWithNonSynced() {
        Lok.debug("lel");
    }

    @After
    public void after() {
//        AFile.instance("indextest").delete();
    }
}
