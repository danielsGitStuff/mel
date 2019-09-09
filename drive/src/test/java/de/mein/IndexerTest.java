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
import de.mein.drive.DriveCreateServiceHelper;
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

/**
 * check whether a mix of not-yet-synced files and sync files produces the correct directory contenthash.
 * a test dir is created and indexed, afterwards the service is shut down and a file in FS is set to non-synced.
 * the according file is removed. the contenthash of the parent directory is expected to stay the same.
 */
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
        rootFile = AFile.instance(AFile.instance("indextest").getAbsolutePath());
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
            new DriveCreateServiceHelper(mas).createDriveService(driveSettings, "server");
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
        final FsDirectory[] subDir = new FsDirectory[1];
        meinBoot.boot().done(mas -> N.r(() -> {
            Thread.sleep(2000);
            mds = (MeinDriveServerService) mas.getMeinServices().iterator().next();
            FsDao fsDao = mds.getDriveDatabaseManager().getFsDao();
            subDir[0] = fsDao.getDirectoryById(2L);
            lock.unlockWrite();
        }));
        lock.lockWrite();
        final String expectedHash = "680c63798b5a1295b1317dab64523c64";
        assertEquals(expectedHash, subDir[0].getContentHash().v());

    }

    @After
    public void after() {
//        AFile.instance("indextest").delete();
    }
}
