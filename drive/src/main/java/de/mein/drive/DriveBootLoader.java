package de.mein.drive;

import de.mein.DeferredRunnable;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.JsonSettings;
import de.mein.auth.data.db.Service;
import de.mein.auth.file.AFile;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.MeinDeferredManager;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.DeferredManager;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 16.08.2016.
 */
public class DriveBootLoader extends BootLoader {

    public DriveBootLoader() {
        BashTools.init();
    }


    @Override
    public String getName() {
        return DriveStrings.NAME;
    }

    @Override
    public String getDescription() {
        return "Ultimate Drive Thing!!!1";
    }

    @Override
    public Promise<Void, Exception, Void> boot(MeinAuthService meinAuthService, List<Service> services) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        DeferredManager deferredManager = new MeinDeferredManager();
        DeferredObject<Void, Exception, Void> booted = new DeferredObject<>();
        List<Promise> bootedPromises = new ArrayList<>();
        for (Service service : services) {
            N.r(() -> {
                File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v() + File.separator + "drive.settings.json");
                de.mein.drive.data.DriveSettings driveSettings = (de.mein.drive.data.DriveSettings) JsonSettings.load(jsonFile);
                MeinDriveService meinDriveService = boot(meinAuthService, service, driveSettings);
                bootedPromises.add(meinDriveService.getStartedDeferred());
            });
        }
        deferredManager.when(bootedPromises.toArray(new Promise[0])).done(result -> booted.resolve(null)).fail(result -> booted.reject((Exception) result.getReject()));
        return booted;
    }

    /**
     * boots one instance
     *
     * @param meinAuthService
     * @param service
     * @param driveSettings
     * @return
     * @throws SqlQueriesException
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws JsonDeserializationException
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     */
    public MeinDriveService boot(MeinAuthService meinAuthService, Service service, de.mein.drive.data.DriveSettings driveSettings) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        AFile workingDirectory = AFile.instance(bootLoaderDir, service.getUuid().v());
        Long serviceTypeId = service.getTypeId().v();
        String uuid = service.getUuid().v();
        MeinDriveService meinDriveService = (driveSettings.isServer()) ?
                new MeinDriveServerService(meinAuthService, workingDirectory, serviceTypeId, uuid) : new MeinDriveClientService(meinAuthService, workingDirectory, serviceTypeId, uuid);
        //notify user
        MeinNotification notification = new MeinNotification(service.getUuid().v(), DriveStrings.Notifications.INTENTION_BOOT, "Booting: " + getName(), "indexing in progress");
        notification.setProgress(0, 0, true);
        meinAuthService.onNotificationFromService(meinDriveService, notification);
        //exec
        meinAuthService.execute(meinDriveService);
        System.out.println("DriveBootLoader.boot");
        meinDriveService.setStartedPromise(this.startIndexer(meinDriveService, driveSettings));
        meinDriveService.getStartedDeferred()
                .done(result -> N.r(() -> {
                    notification.cancel();
                    meinAuthService.registerMeinService(meinDriveService);
//                    if (!driveSettings.isServer()){
//                        MeinDriveClientService meinDriveClientService = (MeinDriveClientService) meinDriveService;
//                        meinDriveClientService.syncThisClient();
//                    }
                }))
                .fail(ex -> {
                    notification.setText("failed :(")
                            .finish();
                    System.err.println("DriveBootLoader.boot." + meinDriveService.getUuid() + " failed");
                });
        return meinDriveService;
    }


    private DeferredObject<DeferredRunnable, Exception, Void> startIndexer(MeinDriveService meinDriveService, de.mein.drive.data.DriveSettings driveSettings) throws SQLException, IOException, ClassNotFoundException, SqlQueriesException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        AFile workingDir = AFile.instance(bootLoaderDir, meinDriveService.getUuid());
        workingDir.mkdirs();
        //create cache dir
        new File(workingDir.getAbsolutePath() + File.separator + "cache").mkdirs();
        DriveDatabaseManager databaseManager = new DriveDatabaseManager(meinDriveService, workingDir, driveSettings);
        databaseManager.cleanUp();
        return meinDriveService.startIndexer(databaseManager);
    }

}
