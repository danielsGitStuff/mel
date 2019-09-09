package de.mein.drive;

import de.mein.Lok;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.service.MeinAuthService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.data.*;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Creates settings files and database entries so that {@link DriveBootloader} can boot the service
 * Created by xor on 10/26/16.
 */
@SuppressWarnings("Duplicates")
public class DriveCreateServiceHelper {
    private final MeinAuthService meinAuthService;

    public DriveCreateServiceHelper(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }


    private Service createDbService(String name) throws SqlQueriesException {
        ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeByName(new DriveBootloader().getName());
        Service service = meinAuthService.getDatabaseManager().createService(type.getId().v(), name);
        return service;
    }

    public void createDriveService(DriveSettings driveSettings, String name) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        Service service = createDbService(name);
        AFile transferDir = AFile.instance(driveSettings.getRootDirectory().getOriginalFile(), DriveStrings.TRANSFER_DIR);
        transferDir.mkdirs();
        driveSettings.setTransferDirectory(transferDir);
        File instanceWorkingDir = meinAuthService.getMeinBoot().createServiceInstanceWorkingDir(service);
        instanceWorkingDir.mkdirs();
        File settingsFile = new File(instanceWorkingDir, DriveStrings.SETTINGS_FILE_NAME);
        driveSettings.setJsonFile(settingsFile);
        driveSettings.save();
        meinAuthService.getMeinBoot().bootServices();
    }

    public void createDriveServerService(String name, AFile rootFile, float wastebinRatio, long maxDays, boolean useSymLinks) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        RootDirectory rootDirectory = DriveSettings.buildRootDirectory(rootFile);
        de.mein.drive.data.DriveSettings driveSettings = new de.mein.drive.data.DriveSettings()
                .setRole(DriveStrings.ROLE_SERVER)
                .setRootDirectory(rootDirectory)
                .setMaxAge(maxDays)
                .setUseSymLinks(useSymLinks);
        AFile transferDir = AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR);
        transferDir.mkdirs();
        driveSettings.setTransferDirectory(transferDir);
        driveSettings.setMaxWastebinSize((long) (driveSettings.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
        createDriveService(driveSettings, name);
    }

    public Promise<MeinDriveServerService, Exception, Void> createDriveServerServiceDeferred(String name, AFile rootFile, float wastebinRatio, int maxDays) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        DeferredObject<MeinDriveServerService, Exception, Void> deferred = new DeferredObject<>();
        Lok.debug("REMEMBER ME?");
        return deferred;
    }

    public void createDriveClientService(String name, AFile rootFile, Long certId, String serviceUuid, float wastebinRatio, long maxDays, boolean useSymLinks) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, ClassNotFoundException, SQLException, InstantiationException, IOException, InterruptedException {
//        //create Service
        RootDirectory rootDirectory = DriveSettings.buildRootDirectory(rootFile);
        de.mein.drive.data.DriveSettings driveSettingsCfg = new de.mein.drive.data.DriveSettings().setRole(DriveStrings.ROLE_CLIENT).setRootDirectory(rootDirectory);
        driveSettingsCfg.setTransferDirectory(AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR));
        driveSettingsCfg.setMaxWastebinSize((long) (driveSettingsCfg.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
        driveSettingsCfg.setMaxAge(maxDays);
        driveSettingsCfg.setUseSymLinks(useSymLinks);
        driveSettingsCfg.getClientSettings().setInitFinished(false);
        driveSettingsCfg.getClientSettings().setServerCertId(certId);
        driveSettingsCfg.getClientSettings().setServerServiceUuid(serviceUuid);
        createDriveService(driveSettingsCfg, name);
    }

}
