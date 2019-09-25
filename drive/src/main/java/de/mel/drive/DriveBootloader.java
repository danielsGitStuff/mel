package de.mel.drive;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.data.db.Service;
import de.mel.auth.service.BootException;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.tools.CountdownLock;
import de.mel.auth.tools.N;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.drive.bash.BashTools;
import de.mel.drive.data.DriveClientSettingsDetails;
import de.mel.drive.data.DriveDetails;
import de.mel.drive.data.DriveSettings;
import de.mel.drive.data.DriveStrings;
import de.mel.drive.service.MelDriveClientService;
import de.mel.drive.service.MelDriveServerService;
import de.mel.drive.service.MelDriveService;
import de.mel.drive.sql.DriveDatabaseManager;
import de.mel.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Created by xor on 16.08.2016.
 */
@SuppressWarnings("Duplicates")
public class DriveBootloader extends Bootloader<MelDriveService> {

    public static interface DEV_DriveBootListener {
        void driveServiceBooted(MelDriveService driveService);
    }

    public static DEV_DriveBootListener DEV_DRIVE_BOOT_LISTENER;
    private MelDriveService melDriveService;
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
    public MelDriveService bootLevelShortImpl(MelAuthService melAuthService, Service serviceDescription) throws BootException {
        try {
            File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + serviceDescription.getUuid().v() + File.separator + DriveStrings.SETTINGS_FILE_NAME);
            driveSettings = DriveSettings.load(jsonFile);
            melDriveService = spawn(melAuthService, serviceDescription, driveSettings);
            if (melDriveService == null) {
                Lok.error("Drive did not spawn!!!");
                throw new Exception("Drive did not spawn!!!");
            }
            Lok.debug(melAuthService.getName() + ", booted to level 1: " + melDriveService.getClass().getSimpleName());
            melAuthService.registerMelService(melDriveService);
        } catch (Exception e) {
            Lok.error(e.toString());
            throw new BootException(this, e);
        }
        return melDriveService;
    }

    @Override
    public Promise<Void, BootException, Void> bootLevelLongImpl() throws BootException {
        try {
            Lok.debug(melDriveService.getMelAuthService().getName() + ", booting to level 2: " + melDriveService.getClass().getSimpleName());
            DeferredObject<Void, BootException, Void> done = new DeferredObject<>();
            //notify user
            MelNotification notification = new MelNotification(melDriveService.getUuid(), DriveStrings.Notifications.INTENTION_BOOT, "Booting: " + getName(), "indexing in progress");
            notification.setUserCancelable(false);
            notification.setProgress(0, 0, true);
            melAuthService.onNotificationFromService(melDriveService, notification);
            DeferredObject<DeferredRunnable, Exception, Void> indexDonePromise = startIndexer(melDriveService, melDriveService.getDriveSettings());
            indexDonePromise
                    .done(result -> N.r(() -> {
                        Lok.debug("indexing done for: " + melDriveService.getDriveSettings().getRootDirectory().getPath());
                        notification.cancel();
                        done.resolve(null);
                        if (DEV_DRIVE_BOOT_LISTENER != null) {
                            DEV_DriveBootListener tmp = DEV_DRIVE_BOOT_LISTENER;
                            DEV_DRIVE_BOOT_LISTENER = null;
                            tmp.driveServiceBooted(melDriveService);
                        }
                    }))
                    .fail(ex -> {
                        notification.setText("failed :(")
                                .finish();
                        System.err.println("DriveBootloader.spawn." + melDriveService.getUuid() + " failed");
                        done.reject(new BootException(this, ex));
                    });
            return done;
        } catch (SQLException | IOException | ClassNotFoundException | SqlQueriesException | JsonDeserializationException | JsonSerializationException | IllegalAccessException e) {
            throw new BootException(this, e);
        }
    }

    @Override
    public void cleanUpDeletedService(MelDriveService melService, String uuid) {
        new File(bootLoaderDir, uuid).delete();
        melAuthService.getPowerManager().removeListener(melService);
    }

    /**
     * boots one instance
     *
     * @param melAuthService
     * @param service
     * @param driveSettings
     * @return
     * @throws SqlQueriesException
     * @throws SQLException
     * @throws IOException
     * @throws JsonDeserializationException
     * @throws JsonSerializationException
     */
    private MelDriveService spawn(MelAuthService melAuthService, Service service, DriveSettings driveSettings) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        this.driveSettings = driveSettings;
        File workingDirectory = new File(bootLoaderDir, service.getUuid().v());
        workingDirectory.mkdirs();
        driveSettings.setJsonFile(new File(workingDirectory, DriveStrings.SETTINGS_FILE_NAME));
        driveSettings.save();
        Long serviceTypeId = service.getTypeId().v();
        String uuid = service.getUuid().v();
        MelDriveService melDriveService = createInstance(driveSettings, workingDirectory, serviceTypeId, uuid);
        //exec
        melAuthService.execute(melDriveService);
        File workingDir = new File(bootLoaderDir, melDriveService.getUuid());
        workingDir.mkdirs();
        //create cache dir
        new File(workingDir.getAbsolutePath() + File.separator + "cache").mkdirs();
        DriveDatabaseManager databaseManager = new DriveDatabaseManager(melDriveService, workingDir, driveSettings);
        databaseManager.cleanUp();
        melDriveService.setDriveDatabaseManager(databaseManager);

        if (!driveSettings.isServer() && !driveSettings.getClientSettings().getInitFinished())
            N.r(() -> {
                //pair with server service
                MelDriveClientService melDriveClientService = (MelDriveClientService) melDriveService;
                DriveClientSettingsDetails clientSettings = driveSettings.getClientSettings();
                Long certId = clientSettings.getServerCertId();
                String serviceUuid = clientSettings.getServerServiceUuid();
                CountdownLock lock = new CountdownLock(1);

                // allow server service to talk to us
                melAuthService.getDatabaseManager().grant(service.getId().v(), certId);

                Promise<MelValidationProcess, Exception, Void> connected = melAuthService.connect(certId);
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
                    melDriveClientService.shutDown();
                    melAuthService.getDatabaseManager().revoke(service.getId().v(), certId);
                    melAuthService.deleteService(service.getUuid().v());
                    lock.unlock();
                }));
                lock.lock();
            });
        return melDriveService;
    }

    protected MelDriveService createInstance(DriveSettings driveSettings, File workingDirectory, Long serviceTypeId, String uuid) {
        MelDriveService melDriveService = (driveSettings.isServer()) ?
                new MelDriveServerService(melAuthService, workingDirectory, serviceTypeId, uuid, driveSettings) : new MelDriveClientService(melAuthService, workingDirectory, serviceTypeId, uuid, driveSettings);
        return melDriveService;
    }

    private DeferredObject<DeferredRunnable, Exception, Void> startIndexer(MelDriveService melDriveService, de.mel.drive.data.DriveSettings driveSettings) throws SQLException, IOException, ClassNotFoundException, SqlQueriesException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        return melDriveService.startIndexer();
    }

}
