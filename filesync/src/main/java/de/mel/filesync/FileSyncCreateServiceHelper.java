package de.mel.filesync;

import de.mel.Lok;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceType;
import de.mel.auth.file.AFile;
import de.mel.auth.service.MelAuthService;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.filesync.data.*;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Creates settings files and database entries so that {@link FileSyncBootloader} can boot the service
 * Created by xor on 10/26/16.
 */
@SuppressWarnings("Duplicates")
public class FileSyncCreateServiceHelper {
    private final MelAuthService melAuthService;

    public FileSyncCreateServiceHelper(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
    }


    protected Service createDbService(String name) throws SqlQueriesException {
        ServiceType type = melAuthService.getDatabaseManager().getServiceTypeByName(new FileSyncBootloader().getName());
        Service service = melAuthService.getDatabaseManager().createService(type.getId().v(), name);
        return service;
    }

    public void createService(FileSyncSettings fileSyncSettings, String name) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        Service service = createDbService(name);
        AFile transferDir = AFile.instance(fileSyncSettings.getRootDirectory().getOriginalFile(), FileSyncStrings.TRANSFER_DIR);
        transferDir.mkdirs();
        fileSyncSettings.setTransferDirectory(transferDir);
        File instanceWorkingDir = melAuthService.getMelBoot().createServiceInstanceWorkingDir(service);
        instanceWorkingDir.mkdirs();
        File settingsFile = new File(instanceWorkingDir, FileSyncStrings.SETTINGS_FILE_NAME);
        fileSyncSettings.setJsonFile(settingsFile);
        fileSyncSettings.save();
        melAuthService.getMelBoot().bootServices();
    }

    public void createServerService(String name, AFile rootFile, float wastebinRatio, long maxDays, boolean useSymLinks) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        RootDirectory rootDirectory = FileSyncSettings.buildRootDirectory(rootFile);
        FileSyncSettings fileSyncSettings = new FileSyncSettings()
                .setRole(FileSyncStrings.ROLE_SERVER)
                .setRootDirectory(rootDirectory)
                .setMaxAge(maxDays)
                .setUseSymLinks(useSymLinks);
        AFile transferDir = AFile.instance(rootDirectory.getOriginalFile(), FileSyncStrings.TRANSFER_DIR);
        transferDir.mkdirs();
        fileSyncSettings.setTransferDirectory(transferDir);
        fileSyncSettings.setMaxWastebinSize((long) (fileSyncSettings.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
        createService(fileSyncSettings, name);
    }

    public Promise<MelFileSyncServerService, Exception, Void> createDriveServerServiceDeferred(String name, AFile rootFile, float wastebinRatio, int maxDays) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        DeferredObject<MelFileSyncServerService, Exception, Void> deferred = new DeferredObject<>();
        Lok.debug("REMEMBER ME?");
        return deferred;
    }

    public void createClientService(String name, AFile rootFile, Long certId, String serviceUuid, float wastebinRatio, long maxDays, boolean useSymLinks) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, ClassNotFoundException, SQLException, InstantiationException, IOException, InterruptedException {
//        //create Service
        RootDirectory rootDirectory = FileSyncSettings.buildRootDirectory(rootFile);
        FileSyncSettings fileSyncSettingsCfg = new FileSyncSettings().setRole(FileSyncStrings.ROLE_CLIENT).setRootDirectory(rootDirectory);
        fileSyncSettingsCfg.setTransferDirectory(AFile.instance(rootDirectory.getOriginalFile(), FileSyncStrings.TRANSFER_DIR));
        fileSyncSettingsCfg.setMaxWastebinSize((long) (fileSyncSettingsCfg.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
        fileSyncSettingsCfg.setMaxAge(maxDays);
        fileSyncSettingsCfg.setUseSymLinks(useSymLinks);
        fileSyncSettingsCfg.getClientSettings().setInitFinished(false);
        fileSyncSettingsCfg.getClientSettings().setServerCertId(certId);
        fileSyncSettingsCfg.getClientSettings().setServerServiceUuid(serviceUuid);
        createService(fileSyncSettingsCfg, name);
    }

}
