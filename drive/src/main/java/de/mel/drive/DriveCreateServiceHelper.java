package de.mel.drive;

import de.mel.Lok;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceType;
import de.mel.auth.file.AFile;
import de.mel.auth.service.MelAuthService;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.drive.data.*;
import de.mel.drive.data.fs.RootDirectory;
import de.mel.drive.service.MelDriveServerService;
import de.mel.sql.SqlQueriesException;

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
    private final MelAuthService melAuthService;

    public DriveCreateServiceHelper(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
    }


    protected Service createDbService(String name) throws SqlQueriesException {
        ServiceType type = melAuthService.getDatabaseManager().getServiceTypeByName(new DriveBootloader().getName());
        Service service = melAuthService.getDatabaseManager().createService(type.getId().v(), name);
        return service;
    }

    public void createService(DriveSettings driveSettings, String name) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        Service service = createDbService(name);
        AFile transferDir = AFile.instance(driveSettings.getRootDirectory().getOriginalFile(), DriveStrings.TRANSFER_DIR);
        transferDir.mkdirs();
        driveSettings.setTransferDirectory(transferDir);
        File instanceWorkingDir = melAuthService.getMelBoot().createServiceInstanceWorkingDir(service);
        instanceWorkingDir.mkdirs();
        File settingsFile = new File(instanceWorkingDir, DriveStrings.SETTINGS_FILE_NAME);
        driveSettings.setJsonFile(settingsFile);
        driveSettings.save();
        melAuthService.getMelBoot().bootServices();
    }

    public void createServerService(String name, AFile rootFile, float wastebinRatio, long maxDays, boolean useSymLinks) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        RootDirectory rootDirectory = DriveSettings.buildRootDirectory(rootFile);
        de.mel.drive.data.DriveSettings driveSettings = new de.mel.drive.data.DriveSettings()
                .setRole(DriveStrings.ROLE_SERVER)
                .setRootDirectory(rootDirectory)
                .setMaxAge(maxDays)
                .setUseSymLinks(useSymLinks);
        AFile transferDir = AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR);
        transferDir.mkdirs();
        driveSettings.setTransferDirectory(transferDir);
        driveSettings.setMaxWastebinSize((long) (driveSettings.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
        createService(driveSettings, name);
    }

    public Promise<MelDriveServerService, Exception, Void> createDriveServerServiceDeferred(String name, AFile rootFile, float wastebinRatio, int maxDays) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        DeferredObject<MelDriveServerService, Exception, Void> deferred = new DeferredObject<>();
        Lok.debug("REMEMBER ME?");
        return deferred;
    }

    public void createClientService(String name, AFile rootFile, Long certId, String serviceUuid, float wastebinRatio, long maxDays, boolean useSymLinks) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, ClassNotFoundException, SQLException, InstantiationException, IOException, InterruptedException {
//        //create Service
        RootDirectory rootDirectory = DriveSettings.buildRootDirectory(rootFile);
        de.mel.drive.data.DriveSettings driveSettingsCfg = new de.mel.drive.data.DriveSettings().setRole(DriveStrings.ROLE_CLIENT).setRootDirectory(rootDirectory);
        driveSettingsCfg.setTransferDirectory(AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR));
        driveSettingsCfg.setMaxWastebinSize((long) (driveSettingsCfg.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
        driveSettingsCfg.setMaxAge(maxDays);
        driveSettingsCfg.setUseSymLinks(useSymLinks);
        driveSettingsCfg.getClientSettings().setInitFinished(false);
        driveSettingsCfg.getClientSettings().setServerCertId(certId);
        driveSettingsCfg.getClientSettings().setServerServiceUuid(serviceUuid);
        createService(driveSettingsCfg, name);
    }

}
