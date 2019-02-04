package de.mein.drive;

import de.mein.DeferredRunnable;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.JsonSettings;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.Bootloader;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Created by xor on 16.08.2016.
 */
@SuppressWarnings("Duplicates")
public class DriveBootloader extends Bootloader<MeinDriveService> {

    private MeinDriveService meinDriveService;
    private DriveSettings driveSettings;

    public DriveBootloader() {
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
    public Promise<MeinDriveService, BootException, Void> bootStage1Impl(MeinAuthService meinAuthService, Service serviceDescription) throws BootException {
        DeferredObject<MeinDriveService, BootException, Void> booted = new DeferredObject<>();
        N.r(() -> {
            File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + serviceDescription.getUuid().v() + File.separator + "drive.settings.json");
            driveSettings = (de.mein.drive.data.DriveSettings) JsonSettings.load(jsonFile);
            meinDriveService = spawn(meinAuthService, serviceDescription, driveSettings);
            booted.resolve(meinDriveService);
//            meinDriveService.getStartedDeferred().done(result -> booted.resolve(null)).fail(e -> booted.reject(new BootException(this, e)));
        });
        return booted;
    }

    @Override
    public Promise<Void, BootException, Void> bootStage2Impl() throws BootException {
        try {
            DeferredObject<Void, BootException, Void> done = new DeferredObject<>();
            //notify user
            MeinNotification notification = new MeinNotification(meinDriveService.getUuid(), DriveStrings.Notifications.INTENTION_BOOT, "Booting: " + getName(), "indexing in progress");
            notification.setProgress(0, 0, true);
            meinAuthService.onNotificationFromService(meinDriveService, notification);
            meinDriveService.setStartedPromise(this.startIndexer(meinDriveService, meinDriveService.getDriveSettings()));
            meinDriveService.getStartedDeferred()
                    .done(result -> N.r(() -> {
                        notification.cancel();
                        done.resolve(null);
//                    if (!driveSettings.isServer()){
//                        MeinDriveClientService meinDriveClientService = (MeinDriveClientService) meinDriveService;
//                        meinDriveClientService.syncThisClient();
//                    }
                    }))
                    .fail(ex -> {
                        notification.setText("failed :(")
                                .finish();
                        System.err.println("DriveBootloader.spawn." + meinDriveService.getUuid() + " failed");
                        done.reject(new BootException(this, ex));
                    });
            return done;
        } catch (SQLException | IOException | ClassNotFoundException | SqlQueriesException | JsonDeserializationException | JsonSerializationException | IllegalAccessException e) {
            throw new BootException(this, e);
        }
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
     * @throws JsonDeserializationException
     * @throws JsonSerializationException
     */
    public MeinDriveService spawn(MeinAuthService meinAuthService, Service service, DriveSettings driveSettings) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        this.driveSettings = driveSettings;
        File workingDirectory = new File(bootLoaderDir, service.getUuid().v());
        workingDirectory.mkdirs();
        driveSettings.setJsonFile(new File(workingDirectory, "drive.settings.json"));
        driveSettings.save();
        Long serviceTypeId = service.getTypeId().v();
        String uuid = service.getUuid().v();
        MeinDriveService meinDriveService = (driveSettings.isServer()) ?
                new MeinDriveServerService(meinAuthService, workingDirectory, serviceTypeId, uuid, driveSettings) : new MeinDriveClientService(meinAuthService, workingDirectory, serviceTypeId, uuid, driveSettings);
        //notify user
        MeinNotification notification = new MeinNotification(service.getUuid().v(), DriveStrings.Notifications.INTENTION_BOOT, "Booting: " + getName(), "indexing in progress");
        notification.setProgress(0, 0, true);
        meinAuthService.onNotificationFromService(meinDriveService, notification);
        //exec
        meinAuthService.execute(meinDriveService);
        File workingDir = new File(bootLoaderDir, meinDriveService.getUuid());
        workingDir.mkdirs();
        //create cache dir
        new File(workingDir.getAbsolutePath() + File.separator + "cache").mkdirs();
        DriveDatabaseManager databaseManager = new DriveDatabaseManager(meinDriveService, workingDir, driveSettings);
        databaseManager.cleanUp();
        meinDriveService.setDriveDatabaseManager(databaseManager);
//        Lok.debug("DriveBootloader.spawn.done");
//        meinDriveService.setStartedPromise(this.startIndexer(meinDriveService, driveSettings));
//        meinDriveService.getStartedDeferred()
//                .done(result -> N.r(() -> {
//                    notification.cancel();
//                    meinAuthService.registerMeinService(meinDriveService);
////                    if (!driveSettings.isServer()){
////                        MeinDriveClientService meinDriveClientService = (MeinDriveClientService) meinDriveService;
////                        meinDriveClientService.syncThisClient();
////                    }
//                }))
//                .fail(ex -> {
//                    notification.setText("failed :(")
//                            .finish();
//                    System.err.println("DriveBootloader.spawn." + meinDriveService.getUuid() + " failed");
//                });
        return meinDriveService;
    }

//    /**
//     * boots one instance
//     *
//     * @param meinAuthService
//     * @param service
//     * @param driveSettings
//     * @return
//     * @throws SqlQueriesException
//     * @throws SQLException
//     * @throws IOException
//     * @throws JsonDeserializationException
//     * @throws JsonSerializationException
//     */
//    public MeinDriveService boot2(MeinAuthService meinAuthService, Service service, de.mein.drive.data.DriveSettings driveSettings) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
//        String uuid = service.getUuid().v();
//        MeinDriveService meinDriveService = (driveSettings.isServer()) ?
//                new MeinDriveServerService(meinAuthService, workingDirectory, serviceTypeId, uuid) : new MeinDriveClientService(meinAuthService, workingDirectory, serviceTypeId, uuid);
//        //notify user
//        MeinNotification notification = new MeinNotification(service.getUuid().v(), DriveStrings.Notifications.INTENTION_BOOT, "Booting: " + getName(), "indexing in progress");
//        notification.setProgress(0, 0, true);
//        meinAuthService.onNotificationFromService(meinDriveService, notification);
//        //exec
//        meinAuthService.execute(meinDriveService);
//        Lok.debug("DriveBootloader.spawn");
//        meinDriveService.setStartedPromise(this.startIndexer(meinDriveService, driveSettings));
//        meinDriveService.getStartedDeferred()
//                .done(result -> N.r(() -> {
//                    notification.cancel();
//                    meinAuthService.registerMeinService(meinDriveService);
////                    if (!driveSettings.isServer()){
////                        MeinDriveClientService meinDriveClientService = (MeinDriveClientService) meinDriveService;
////                        meinDriveClientService.syncThisClient();
////                    }
//                }))
//                .fail(ex -> {
//                    notification.setText("failed :(")
//                            .finish();
//                    System.err.println("DriveBootloader.spawn." + meinDriveService.getUuid() + " failed");
//                });
//        return meinDriveService;
//    }


    private DeferredObject<DeferredRunnable, Exception, Void> startIndexer(MeinDriveService meinDriveService, de.mein.drive.data.DriveSettings driveSettings) throws SQLException, IOException, ClassNotFoundException, SqlQueriesException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        return meinDriveService.startIndexer();
    }

}
