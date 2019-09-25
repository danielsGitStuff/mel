package de.mel;

import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.file.AFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelBoot;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.Eva;
import de.mel.auth.tools.N;
import de.mel.drive.DriveBootloader;
import de.mel.drive.DriveCreateServiceHelper;
import de.mel.drive.bash.BashTools;
import de.mel.drive.data.DriveSettings;
import de.mel.drive.data.DriveStrings;
import de.mel.drive.data.fs.RootDirectory;
import de.mel.drive.serialization.TestDirCreator;
import de.mel.drive.service.MelDriveServerService;
import de.mel.drive.sql.FsDirectory;
import de.mel.drive.sql.FsFile;
import de.mel.drive.sql.dao.FsDao;
import de.mel.sql.RWLock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * check whether a mix of not-yet-synced files and sync files produces the correct directory contenthash.
 * a test dir is created and indexed, afterwards the service is shut down and a file in FS is set to non-synced.
 * the according file is removed. the contenthash of the parent directory is expected to stay the same.
 */
public class IndexerTest {
    private static AFile rootFile;
    private static MelAuthService mas;
    private static MelDriveServerService mds;
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
        BashTools.rmRf(AFile.instance(MelBoot.Companion.getDefaultWorkingDir1()));

        TestDirCreator.createTestDirSimple(rootFile);
        MelAuthSettings melAuthSettings = MelAuthSettings.createDefaultSettings();
        MelBoot melBoot = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), DriveBootloader.class);
        melBoot.boot().done(mas -> N.r(() -> {
            IndexerTest.mas = mas;
            DriveSettings driveSettings = new DriveSettings()
                    .setRole(DriveStrings.ROLE_SERVER)
                    .setMaxAge(1000000L)
                    .setRootDirectory(rootDirectory)
                    .setTransferDirectory(AFile.instance(rootFile, "transfer"))
                    .setMaxWastebinSize(999999L)
                    .setFastBoot(true);
            new DriveCreateServiceHelper(mas).createService(driveSettings, "server");
            Thread.sleep(2000);
            mds = (MelDriveServerService) mas.getMelServices().iterator().next();
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

        CountLock lock = new CountLock().lock();
        MelAuthSettings melAuthSettings = MelAuthSettings.createDefaultSettings();
        AtomicReference<MelAuthService> melAuthServiceAtomicReference = new AtomicReference<>();
        MelBoot melBoot = new MelBoot(melAuthSettings, new PowerManager(melAuthSettings), DriveBootloader.class);
        final FsDirectory[] subDir = new FsDirectory[1];
        melBoot.boot().done(mas -> N.r(() -> {
            melAuthServiceAtomicReference.set(mas);
            Thread.sleep(2000);
            mds = (MelDriveServerService) mas.getMelServices().iterator().next();
            FsDao fsDao = mds.getDriveDatabaseManager().getFsDao();
            subDir[0] = fsDao.getDirectoryById(2L);
            lock.unlock();
        }));
        lock.lock();
        final String expectedHash = "680c63798b5a1295b1317dab64523c64";
        assertEquals(expectedHash, subDir[0].getContentHash().v());
        melAuthServiceAtomicReference.get().shutDown().done(result -> {
            Lok.debug("done here");
            lock.unlock();
        });
        lock.lock();
    }

    @After
    public void after() {
//        AFile.instance("indextest").delete();
    }
}
