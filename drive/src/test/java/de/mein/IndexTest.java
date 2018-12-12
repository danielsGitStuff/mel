package de.mein;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.CountWaitLock;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.serialization.TestDirCreator;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public class IndexTest {
    private MeinAuthService mas;
    private BootLoader dbl;
    private AFile testRoot;
    private MeinDriveServerService service;
    private File wd;
    private MeinDriveService mds;
    private boolean cleanDirs = true;

    @Before
    public void before() throws Exception {
        AFile.configure(new DefaultFileConfiguration());
        BashTools.init();
        testRoot = AFile.instance(new File("indextest"));
        wd = new File("indexwd");
        if (cleanDirs) {
            BashTools.rmRf(AFile.instance(wd));
            TestDirCreator.createTestDir(testRoot);
        }
        MeinAuthSettings settings = MeinAuthSettings.createDefaultSettings().setWorkingDirectory(wd);
        CountWaitLock lock = new CountWaitLock();
        MeinBoot meinBoot = new MeinBoot(settings, new PowerManager(settings), DriveBootLoader.class);
        Promise<MeinAuthService, Exception, Void> promise = meinBoot.boot();
        promise.done(result -> N.r(() -> {
            mas = result;
            RootDirectory rootDirectory = DriveSettings.buildRootDirectory(testRoot);
            AFile transferDir = AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR);
            ServiceType type = mas.getDatabaseManager().getServiceTypeByName(new DriveBootLoader().getName());
            Service service = mas.getDatabaseManager().createService(type.getId().v(), "lel");
            DriveBootLoader bl = (DriveBootLoader) mas.getMeinBoot().getBootLoader(new DriveBootLoader().getName());
            DriveSettings driveSettings = new DriveSettings()
                    .setRole(DriveStrings.ROLE_SERVER)
                    .setMaxAge(1000000L)
                    .setRootDirectory(rootDirectory)
                    .setTransferDirectory(transferDir)
                    .setMaxWastebinSize(999999L)
                    .setFastIndex(true);
            mds = bl.boot(mas, service, driveSettings);
            mds.start();
            mas.registerMeinService(mds);
            lock.unlock();
        }));
        lock.lock();
    }

    @Test
    public void reindex() throws Exception {
        MeinDriveServerService driveService = (MeinDriveServerService) mas.getMeinServices().stream().findFirst().get();
        Lok.debug("lel");
        cleanDirs = false;
        mas.shutDown();
        //re index
        CountWaitLock lock = new CountWaitLock();
        MeinAuthSettings settings = mas.getSettings();
        MeinBoot meinBoot = new MeinBoot(settings, new PowerManager(settings), DriveBootLoader.class);
        meinBoot.boot().done(result -> N.r(() -> {
            mas = result;
            lock.unlock();

        }));

        lock.lock();
    }
}
