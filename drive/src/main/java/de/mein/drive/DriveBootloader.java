package de.mein.drive;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.BootException;
import de.mein.auth.service.Bootloader;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.tools.CountdownLock;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.DriveClientSettingsDetails;
import de.mein.drive.data.DriveDetails;
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

    public static interface DEV_DriveBootListener {
        void driveServiceBooted(MeinDriveService driveService);
    }

    public static DEV_DriveBootListener DEV_DRIVE_BOOT_LISTENER;
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
    public MeinDriveService bootLevel1Impl(MeinAuthService meinAuthService, Service serviceDescription) throws BootException {
        try {
            File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + serviceDescription.getUuid().v() + File.separator + DriveStrings.SETTINGS_FILE_NAME);
            driveSettings = DriveSettings.load(jsonFile);
            meinDriveService = spawn(meinAuthService, serviceDescription, driveSettings);
            if (meinDriveService == null) {
                Lok.error("Drive did not spawn!!!");
                throw new Exception("Drive did not spawn!!!");
            }
            Lok.debug(meinAuthService.getName() + ", booted to level 1: " + meinDriveService.getClass().getSimpleName());
            meinAuthService.registerMeinService(meinDriveService);
        } catch (Exception e) {
            Lok.error(e.toString());
            throw new BootException(this, e);
        }
        return meinDriveService;
    }

    @Override
    public Promise<Void, BootException, Void> bootLevel2Impl() throws BootException {
        try {
            Lok.debug(meinDriveService.getMeinAuthService().getName() + ", booting to level 2: " + meinDriveService.getClass().getSimpleName());
            DeferredObject<Void, BootException, Void> done = new DeferredObject<>();
            //notify user
            MeinNotification notification = new MeinNotification(meinDriveService.getUuid(), DriveStrings.Notifications.INTENTION_BOOT, "Booting: " + getName(), "indexing in progress");
            notification.setProgress(0, 0, true);
            meinAuthService.onNotificationFromService(meinDriveService, notification);
            DeferredObject<DeferredRunnable, Exception, Void> indexDonePromise = startIndexer(meinDriveService, meinDriveService.getDriveSettings());
            indexDonePromise
                    .done(result -> N.r(() -> {
                        Lok.debug("indexing done for: " + meinDriveService.getDriveSettings().getRootDirectory().getPath());
                        notification.cancel();
//                        meinDriveService.onBootLevel2Finished();
                        done.resolve(null);
                        if (DEV_DRIVE_BOOT_LISTENER != null) {
                            DEV_DriveBootListener tmp = DEV_DRIVE_BOOT_LISTENER;
                            DEV_DRIVE_BOOT_LISTENER = null;
                            tmp.driveServiceBooted(meinDriveService);
                        }
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

    @Override
    public void cleanUpDeletedService(MeinDriveService meinService, String uuid) {
        new File(bootLoaderDir, uuid).delete();
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
    private MeinDriveService spawn(MeinAuthService meinAuthService, Service service, DriveSettings driveSettings) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
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
//        MeinNotification notification = new MeinNotification(service.getUuid().v(), DriveStrings.Notifications.INTENTION_BOOT, "Booting: " + getName(), "indexing in progress");
//        notification.setProgress(0, 0, true);
//        meinAuthService.onNotificationFromService(meinDriveService, notification);
        //exec
        meinAuthService.execute(meinDriveService);
        File workingDir = new File(bootLoaderDir, meinDriveService.getUuid());
        workingDir.mkdirs();
        //create cache dir
        new File(workingDir.getAbsolutePath() + File.separator + "cache").mkdirs();
        DriveDatabaseManager databaseManager = new DriveDatabaseManager(meinDriveService, workingDir, driveSettings);
        databaseManager.cleanUp();
        meinDriveService.setDriveDatabaseManager(databaseManager);

        if (!driveSettings.isServer() && !driveSettings.getClientSettings().getInitFinished())
            N.r(() -> {
                //pair with server service
                MeinDriveClientService meinDriveClientService = (MeinDriveClientService) meinDriveService;
                DriveClientSettingsDetails clientSettings = driveSettings.getClientSettings();
                Long certId = clientSettings.getServerCertId();
                String serviceUuid = clientSettings.getServerServiceUuid();
                CountdownLock lock = new CountdownLock(1);

                // allow server service to talk to us
                meinAuthService.getDatabaseManager().grant(service.getId().v(), certId);

                Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId);
                DriveDetails driveDetails = new DriveDetails().setRole(DriveStrings.ROLE_CLIENT).setLastSyncVersion(0).setServiceUuid(service.getUuid().v())
                        .setUsesSymLinks(driveSettings.getUseSymLinks());
                driveDetails.setIntent(DriveStrings.INTENT_REG_AS_CLIENT);
                connected.done(validationProcess -> N.r(() -> validationProcess.request(serviceUuid, driveDetails).done(result -> N.r(() -> {
                    Lok.debug("Service created and paired");
                    clientSettings.setInitFinished(true);
                    driveSettings.save();
                    lock.unlock();
                })))).fail(result -> N.r(() -> {
                    Lok.debug("DriveCreateController.createDriveClientService.FAIL");
                    result.printStackTrace();
                    meinDriveClientService.shutDown();
                    meinAuthService.getDatabaseManager().revoke(service.getId().v(), certId);
                    meinAuthService.deleteService(service.getUuid().v());
                    lock.unlock();
                }));
                lock.lock();
            });

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
