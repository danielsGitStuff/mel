package de.mel;

import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.file.IFile;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelBoot;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.CountWaitLock;
import de.mel.auth.tools.Eva;
import de.mel.auth.tools.N;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.RootDirectory;
import de.mel.filesync.serialization.TestDirCreator;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.filesync.service.MelFileSyncService;
import org.jdeferred.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

public class IndexTest {
    protected MelAuthService mas;
    private Bootloader dbl;
    private IFile testRoot;
    private MelFileSyncServerService service;
    private File wd;
    private MelFileSyncService mds;
    protected boolean index = true;

//    @Test
//    public void withNonSyncedFiles() {
//
//    }

    @Before
    public void before() throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        BashTools.Companion.init();
        testRoot = AbstractFile.instance(new File("indextest"));
        BashTools.Companion.rmRf(testRoot);
        wd = new File("indexwd");
        if (index) {
            BashTools.Companion.rmRf(AbstractFile.instance(wd));
            TestDirCreator.createTestDir(testRoot);
            MelAuthSettings settings = MelAuthSettings.createDefaultSettings().setWorkingDirectory(wd).setName("First");
            CountWaitLock lock = new CountWaitLock();
            MelBoot melBoot = new MelBoot(settings, new PowerManager(settings), FileSyncBootloader.class);
            Promise<MelAuthService, Exception, Void> promise = melBoot.boot();
            promise.done(result -> N.r(() -> {
                mas = result;
                RootDirectory rootDirectory = RootDirectory.buildRootDirectory(testRoot);
                IFile transferDir = AbstractFile.instance(rootDirectory.getOriginalFile(), FileSyncStrings.TRANSFER_DIR);
                FileSyncBootloader bl = (FileSyncBootloader) mas.getMelBoot().getBootLoader(new FileSyncBootloader().getName());
                FileSyncSettings fileSyncSettings = new FileSyncSettings()
                        .setRole(FileSyncStrings.ROLE_SERVER)
                        .setMaxAge(1000000L)
                        .setRootDirectory(rootDirectory)
                        .setTransferDirectory(transferDir)
                        .setMaxWastebinSize(999999L)
                        .setFastBoot(true);
                FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
                    mds = driveService;
                    mds.start();
                    mds.getStartedDeferred().done(result1 -> lock.unlock());
                };
                new FileSyncCreateServiceHelper(mas).createService(fileSyncSettings, "server");
                lock.unlock();
                //mas.registerMelService(mds);
            }));
            lock.lock();
        }
    }

    @After
    public void after() throws IOException {
        BashTools.Companion.rmRf(testRoot);
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
            MelBoot melBoot = new MelBoot(settings, new PowerManager(settings), FileSyncBootloader.class);
            melBoot.boot().done(result -> N.r(() -> {
                mas = result;
                MelFileSyncServerService driveService = (MelFileSyncServerService) mas.getMelServices().stream().findFirst().get();
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
