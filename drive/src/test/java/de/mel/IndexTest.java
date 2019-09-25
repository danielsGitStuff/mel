package de.mel;

import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.file.AFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelBoot;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.CountWaitLock;
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
import de.mel.drive.service.MelDriveService;
import org.jdeferred.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.security.cert.Certificate;

public class IndexTest {
    protected MelAuthService mas;
    private Bootloader dbl;
    private AFile testRoot;
    private MelDriveServerService service;
    private File wd;
    private MelDriveService mds;
    protected boolean index = true;

//    @Test
//    public void withNonSyncedFiles() {
//
//    }

    @Before
    public void before() throws Exception {
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
        testRoot = AFile.instance(new File("indextest"));
        BashTools.rmRf(testRoot);
        wd = new File("indexwd");
        if (index) {
            BashTools.rmRf(AFile.instance(wd));
            TestDirCreator.createTestDir(testRoot);
            MelAuthSettings settings = MelAuthSettings.createDefaultSettings().setWorkingDirectory(wd).setName("First");
            CountWaitLock lock = new CountWaitLock();
            MelBoot melBoot = new MelBoot(settings, new PowerManager(settings), DriveBootloader.class);
            Promise<MelAuthService, Exception, Void> promise = melBoot.boot();
            promise.done(result -> N.r(() -> {
                mas = result;
                RootDirectory rootDirectory = DriveSettings.buildRootDirectory(testRoot);
                AFile transferDir = AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR);
                DriveBootloader bl = (DriveBootloader) mas.getMelBoot().getBootLoader(new DriveBootloader().getName());
                DriveSettings driveSettings = new DriveSettings()
                        .setRole(DriveStrings.ROLE_SERVER)
                        .setMaxAge(1000000L)
                        .setRootDirectory(rootDirectory)
                        .setTransferDirectory(transferDir)
                        .setMaxWastebinSize(999999L)
                        .setFastBoot(true);
                DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
                    mds = driveService;
                    mds.start();
                    mds.getStartedDeferred().done(result1 -> lock.unlock());
                };
                new DriveCreateServiceHelper(mas).createService(driveSettings, "server");
                lock.unlock();
                //mas.registerMelService(mds);
            }));
            lock.lock();
        }
    }

    @After
    public void after() throws IOException {
        BashTools.rmRf(testRoot);
        CertificateManager.deleteDirectory(wd);
    }

    @Test
    public void reindex() throws Exception {
        Eva.enable();
        index = false;
        mas.shutDown().done(nil -> N.r(() -> {
            //re index
            CountWaitLock lock = new CountWaitLock();
            MelAuthSettings settings = mas.getSettings();
            MelBoot melBoot = new MelBoot(settings, new PowerManager(settings), DriveBootloader.class);
            melBoot.boot().done(result -> N.r(() -> {
                mas = result;
                MelDriveServerService driveService = (MelDriveServerService) mas.getMelServices().stream().findFirst().get();
                driveService.startedPromise.done(result1 -> {
                    Lok.debug("reindex done");
                    assertTrue(true);
                    //todo refine
                    Lok.debug("shutting down...");
                    mas.shutDown().done(result2 -> {
                        Lok.debug("done, shutting down");
                        lock.unlock();
                    });
                });
            }));
            lock.lock();
        }));
    }

}
