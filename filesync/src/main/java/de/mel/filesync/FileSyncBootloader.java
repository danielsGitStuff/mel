package de.mel.filesync;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.service.BootException;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.tools.CountdownLock;
import de.mel.auth.tools.N;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.data.FileSyncClientSettingsDetails;
import de.mel.filesync.data.FileSyncDetails;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.filesync.service.MelFileSyncService;
import de.mel.filesync.sql.FileSyncDatabaseManager;
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
public class FileSyncBootloader extends Bootloader<MelFileSyncService> {

    public static DEV_DriveBootListener DEV_DRIVE_BOOT_LISTENER;
    private MelFileSyncService melFileSyncService;
    private FileSyncSettings fileSyncSettings;
    public FileSyncBootloader() {
        BashTools.Companion.init();
    }

    @Override
    public String getName() {
        return FileSyncStrings.TYPE;
    }

    @Override
    public String getDescription() {
        return "Syncs files across devices";
    }

    @Override
    public MelFileSyncService bootLevelShortImpl(MelAuthService melAuthService, Service serviceDescription) throws BootException {
        try {
            File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + serviceDescription.getUuid().v() + File.separator + FileSyncStrings.SETTINGS_FILE_NAME);
            fileSyncSettings = FileSyncSettings.load(jsonFile);
            melFileSyncService = spawn(melAuthService, serviceDescription, fileSyncSettings);
            if (melFileSyncService == null) {
                Lok.error("File Sync did not spawn!!!");
                throw new Exception("File Sync did not spawn!!!");
            }
            Lok.debug(melAuthService.getName() + ", booted to level 1: " + melFileSyncService.getClass().getSimpleName());
            melAuthService.registerMelService(melFileSyncService);
        } catch (Exception e) {
            Lok.error(e.toString());
            throw new BootException(this, e);
        }
        return melFileSyncService;
    }

    @Override
    public Promise<Void, BootException, Void> bootLevelLongImpl() throws BootException {
        try {
            Lok.debug(melFileSyncService.getMelAuthService().getName() + ", booting to level 2: " + melFileSyncService.getClass().getSimpleName());
            DeferredObject<Void, BootException, Void> done = new DeferredObject<>();
            //notify user
            MelNotification notification = new MelNotification(melFileSyncService.getUuid(), FileSyncStrings.Notifications.INTENTION_BOOT, "Booting: " + getName(), "indexing in progress");
            notification.setUserCancelable(false);
            notification.setProgress(0, 0, true);
            melAuthService.onNotificationFromService(melFileSyncService, notification);
            DeferredObject<DeferredRunnable, Exception, Void> indexDonePromise = startIndexer(melFileSyncService, melFileSyncService.getFileSyncSettings());
            indexDonePromise
                    .done(result -> N.r(() -> {
                        Lok.debug("indexing done for: " + melFileSyncService.getFileSyncSettings().getRootDirectory().getPath());
                        notification.cancel();
                        done.resolve(null);
                        if (DEV_DRIVE_BOOT_LISTENER != null) {
                            DEV_DriveBootListener tmp = DEV_DRIVE_BOOT_LISTENER;
                            DEV_DRIVE_BOOT_LISTENER = null;
                            tmp.driveServiceBooted(melFileSyncService);
                        }
                    }))
                    .fail(ex -> {
                        notification.setText("failed :(")
                                .finish();
                        System.err.println("DriveBootloader.spawn." + melFileSyncService.getUuid() + " failed");
                        done.reject(new BootException(this, ex));
                    });
            return done;
        } catch (SQLException | IOException | ClassNotFoundException | SqlQueriesException | JsonDeserializationException | JsonSerializationException | IllegalAccessException e) {
            throw new BootException(this, e);
        }
    }

    @Override
    public void cleanUpDeletedService(MelFileSyncService melService, String uuid) {
        new File(bootLoaderDir, uuid).delete();
        melAuthService.getPowerManager().removeListener(melService);
    }

    @Override
    public boolean isCompatiblePartner(ServiceJoinServiceType service) {
        return service.getType().equalsValue(FileSyncStrings.TYPE) && service.getAdditionalServicePayload() != null;
    }

    /**
     * boots one instance
     *
     * @param melAuthService
     * @param service
     * @param fileSyncSettings
     * @return
     * @throws SqlQueriesException
     * @throws SQLException
     * @throws IOException
     * @throws JsonDeserializationException
     * @throws JsonSerializationException
     */
    private MelFileSyncService spawn(MelAuthService melAuthService, Service service, FileSyncSettings fileSyncSettings) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        this.fileSyncSettings = fileSyncSettings;
        File workingDirectory = new File(bootLoaderDir, service.getUuid().v());
        workingDirectory.mkdirs();
        fileSyncSettings.setJsonFile(new File(workingDirectory, FileSyncStrings.SETTINGS_FILE_NAME));
        fileSyncSettings.save();
        Long serviceTypeId = service.getTypeId().v();
        String uuid = service.getUuid().v();

        // create dirs
        File workingDir = new File(bootLoaderDir, service.getUuid().v());
        workingDir.mkdirs();
        //create cache dir
        new File(workingDir.getAbsolutePath() + File.separator + "cache").mkdirs();
        // create databases
        FileSyncDatabaseManager databaseManager = new FileSyncDatabaseManager(uuid, workingDir, fileSyncSettings);
        databaseManager.cleanUp();

        // create instance
        MelFileSyncService melFileSyncService = createInstance(fileSyncSettings, workingDirectory, serviceTypeId, uuid, databaseManager);
        databaseManager.setMelFileSyncService(melFileSyncService);
        //exec
        melAuthService.execute(melFileSyncService);

        if (!fileSyncSettings.isServer() && !fileSyncSettings.getClientSettings().getInitFinished())
            N.r(() -> {
                //pair with server service
                MelFileSyncClientService melFileSyncClientService = (MelFileSyncClientService) melFileSyncService;
                FileSyncClientSettingsDetails clientSettings = fileSyncSettings.getClientSettings();
                Long certId = clientSettings.getServerCertId();
                String serviceUuid = clientSettings.getServerServiceUuid();
                CountdownLock lock = new CountdownLock(1);

                // allow server service to talk to us
                melAuthService.getDatabaseManager().grant(service.getId().v(), certId);

                Promise<MelValidationProcess, Exception, Void> connected = melAuthService.connect(certId);
                FileSyncDetails fileSyncDetails = new FileSyncDetails().setRole(FileSyncStrings.ROLE_CLIENT).setLastSyncVersion(0).setServiceUuid(service.getUuid().v())
                        .setUsesSymLinks(fileSyncSettings.getUseSymLinks());
                fileSyncDetails.setIntent(FileSyncStrings.INTENT_REG_AS_CLIENT);
                connected.done(validationProcess -> N.r(() -> validationProcess.request(serviceUuid, fileSyncDetails).done(result -> N.r(() -> {
                    Lok.debug("Service created and paired");
                    clientSettings.setInitFinished(true);
                    fileSyncSettings.save();
                    lock.unlock();
                })))).fail(result -> N.r(() -> {
                    Lok.debug("DriveCreateController.createDriveClientService.FAIL");
                    result.printStackTrace();
                    melFileSyncClientService.shutDown();
                    melAuthService.getDatabaseManager().revoke(service.getId().v(), certId);
                    melAuthService.deleteService(service.getUuid().v());
                    lock.unlock();
                }));
                lock.lock();
            });
        return melFileSyncService;
    }

    protected MelFileSyncService createInstance(FileSyncSettings fileSyncSettings, File workingDirectory, Long serviceTypeId, String uuid, FileSyncDatabaseManager databaseManager) {
        MelFileSyncService melFileSyncService = (fileSyncSettings.isServer()) ?
                new MelFileSyncServerService(melAuthService, workingDirectory, serviceTypeId, uuid, fileSyncSettings, databaseManager) : new MelFileSyncClientService(melAuthService, workingDirectory, serviceTypeId, uuid, fileSyncSettings, databaseManager);
        return melFileSyncService;
    }

    private DeferredObject<DeferredRunnable, Exception, Void> startIndexer(MelFileSyncService melFileSyncService, FileSyncSettings fileSyncSettings) throws SQLException, IOException, ClassNotFoundException, SqlQueriesException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        return melFileSyncService.startIndexer();
    }

    public static interface DEV_DriveBootListener {
        void driveServiceBooted(MelFileSyncService driveService);
    }

}
