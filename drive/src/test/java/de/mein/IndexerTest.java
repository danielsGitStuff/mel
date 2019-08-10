package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.Eva;
import de.mein.auth.tools.N;
import de.mein.drive.DriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.dao.FsDao;
import de.mein.sql.RWLock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class IndexerTest {
    private static AFile rootFile;
    private static MeinAuthService mas;
    private static MeinDriveServerService mds;
    private static RootDirectory rootDirectory;

    @Before
    public void before() throws Exception {
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
        Eva.enable();
        RWLock bootLock = new RWLock().lockWrite();
        rootFile = AFile.instance("indextest");
        rootDirectory = new RootDirectory().setOriginalFile(rootFile).setPath(rootFile.getPath());

        BashTools.rmRf(rootFile);
        BashTools.rmRf(AFile.instance(MeinBoot.Companion.getDefaultWorkingDir1()));

        TestDirCreator.createTestDirSimple(rootFile);
        MeinAuthSettings meinAuthSettings = MeinAuthSettings.createDefaultSettings();
        MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveBootloader.class);
        meinBoot.boot().done(mas -> N.r(() -> {
            IndexerTest.mas = mas;
            DriveSettings driveSettings = new DriveSettings()
                    .setRole(DriveStrings.ROLE_SERVER)
                    .setMaxAge(1000000L)
                    .setRootDirectory(rootDirectory)
                    .setTransferDirectory(AFile.instance(rootFile, "transfer"))
                    .setMaxWastebinSize(999999L)
                    .setFastBoot(true);
            new DriveCreateController(mas).createDriveService(driveSettings, "server");
            Thread.sleep(2000);
            mds = (MeinDriveServerService) mas.getMeinServices().iterator().next();
            bootLock.unlockWrite();
        }));
        bootLock.lockWrite();
    }

    @Test
    public void mixedSyncedWithNonSynced() throws Exception {
        {
            Lok.debug("modifying db");
            FsDao fsDao = mds.getDriveDatabaseManager().getFsDao();
            FsDirectory fsRoot = fsDao.getSubDirectoriesByParentId(null).iterator().next();
            FsDirectory fsSub = fsDao.getSubDirectoryByName(fsRoot.getId().v(), "sub");
            FsFile fsUnSynced = fsDao.getFsFileByName(fsSub.getId().v(), "unsynced3.txt");
            AFile file2delete = fsDao.getFileByFsFile(rootDirectory, fsUnSynced);
            Lok.debug("mofifying file entry: " + fsUnSynced.getName().v());
            fsUnSynced.getSynced().v(false);
            fsDao.update(fsUnSynced);
            mas.shutDown();
            file2delete.delete();
        }

        // reboot
        RWLock lock = new RWLock().lockWrite();
        MeinAuthSettings meinAuthSettings = MeinAuthSettings.createDefaultSettings();
        MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings), DriveBootloader.class);
        meinBoot.boot().done(mas -> N.r(() -> {
            Thread.sleep(2000);
            mds = (MeinDriveServerService) mas.getMeinServices().iterator().next();
            final String expectedHash = "b940363a274a2fe0a756d13ce368fafd";
            FsDao fsDao = mds.getDriveDatabaseManager().getFsDao();
            FsDirectory subDir = fsDao.getDirectoryById(2L);
            assertEquals(expectedHash, subDir.getContentHash().v());
            lock.unlockWrite();
        }));
        lock.lockWrite();
    }

    @After
    public void after() {
//        AFile.instance("indextest").delete();
    }
}
