package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.service.Bootloader;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.CountWaitLock;
import de.mein.auth.tools.Eva;
import de.mein.auth.tools.N;
import de.mein.drive.DriveBootloader;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;
import org.jdeferred.Promise;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;

public class IndexTest {
    private MeinAuthService mas;
    private Bootloader dbl;
    private AFile testRoot;
    private MeinDriveServerService service;
    private File wd;
    private MeinDriveService mds;
    private boolean index = true;

    @Before
    public void before() throws Exception {
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
        testRoot = AFile.instance(new File("indextest"));
        wd = new File("indexwd");
        if (index) {
            BashTools.rmRf(AFile.instance(wd));
            TestDirCreator.createTestDir(testRoot);
            MeinAuthSettings settings = MeinAuthSettings.createDefaultSettings().setWorkingDirectory(wd);
            CountWaitLock lock = new CountWaitLock();
            MeinBoot meinBoot = new MeinBoot(settings, new PowerManager(settings), DriveBootloader.class);
            Promise<MeinAuthService, Exception, Void> promise = meinBoot.boot();
            promise.done(result -> N.r(() -> {
                mas = result;
                RootDirectory rootDirectory = DriveSettings.buildRootDirectory(testRoot);
                AFile transferDir = AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR);
                ServiceType type = mas.getDatabaseManager().getServiceTypeByName(new DriveBootloader().getName());
                Service service = mas.getDatabaseManager().createService(type.getId().v(), "lel");
                DriveBootloader bl = (DriveBootloader) mas.getMeinBoot().getBootLoader(new DriveBootloader().getName());
                DriveSettings driveSettings = new DriveSettings()
                        .setRole(DriveStrings.ROLE_SERVER)
                        .setMaxAge(1000000L)
                        .setRootDirectory(rootDirectory)
                        .setTransferDirectory(transferDir)
                        .setMaxWastebinSize(999999L)
                        .setFastBoot(true);
                mds = bl.spawn(mas, service, driveSettings);
                mds.start();
                mds.getStartedDeferred().done(result1 -> lock.unlock());
                //mas.registerMeinService(mds);
            }));
            lock.lock();
        }
    }

    @Test
    public void reindex() throws Exception {
        Eva.enable();
        Lok.debug("lel");
        index = false;
        mas.shutDown();
        //re index
        CountWaitLock lock = new CountWaitLock();
        MeinAuthSettings settings = mas.getSettings();
        MeinBoot meinBoot = new MeinBoot(settings, new PowerManager(settings), DriveBootloader.class);
        meinBoot.boot().done(result -> N.r(() -> {
            mas = result;
            MeinDriveServerService driveService = (MeinDriveServerService) mas.getMeinServices().stream().findFirst().get();
            driveService.startedPromise.done(result1 -> {
                Lok.debug("done");
                assertTrue(Eva.hasFlag("fast spawn 1"));
                assertEquals(5, Eva.getFlagCount("fast spawn 1"));
                lock.unlock();
            });
        }));
        lock.lock();
    }
}
