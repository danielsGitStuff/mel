package de.mein.drive;

import de.mein.Lok;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.data.*;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Created by xor on 10/26/16.
 */
@SuppressWarnings("Duplicates")
public class DriveCreateController {
    private final MeinAuthService meinAuthService;
    private N runner = new N(Throwable::printStackTrace);

    public DriveCreateController(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }


    private Service createService(String name) throws SqlQueriesException {
        ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeByName(new DriveBootloader().getName());
        Service service = meinAuthService.getDatabaseManager().createService(type.getId().v(), name);
        return service;
    }

//    private void boot(Service service, de.mein.drive.data.DriveSettings driveSettings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException, InstantiationException {
//        MeinBoot meinBoot = meinAuthService.getMeinBoot();
//        DriveBootloader driveBootLoader = (DriveBootloader) meinBoot.getBootLoader(new DriveBootloader().getName());
//        MeinDriveService meinDriveService = driveBootLoader.spawn(meinAuthService, service,driveSettings);
//        meinDriveService.shutDown();
//        meinBoot.bootServices();
//        WaitLock waitLock = new WaitLock().lock();
//        meinDriveService.getStartedDeferred().done(result -> {
//            waitLock.unlock();
//        }).fail(result -> {
//            System.err.println("DriveCreateController.spawn");
//            waitLock.unlock();
//        });
//        waitLock.lock();
//        Lok.debug("DriveCreateController.spawn.booted");
//    }

    public void createDriveService(DriveSettings driveSettings, String name) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        Service service = createService(name);
        AFile transferDir = AFile.instance(driveSettings.getRootDirectory().getOriginalFile(), DriveStrings.TRANSFER_DIR);
        transferDir.mkdirs();
        driveSettings.setTransferDirectory(transferDir);
        File instanceWorkingDir = meinAuthService.getMeinBoot().createServiceInstanceWorkingDir(service);
        instanceWorkingDir.mkdirs();
        File settingsFile = new File(instanceWorkingDir, DriveStrings.SETTINGS_FILE_NAME);
        driveSettings.setJsonFile(settingsFile);
        driveSettings.save();
        meinAuthService.getMeinBoot().bootServices();
//        boot(service, driveSettings);
//        MeinDriveServerService mdss = (MeinDriveServerService) meinAuthService.getMeinService(service.getUuid().v());
//        mdss.start();
//        return mdss;
    }

    public void createDriveServerService(String name, AFile rootFile, float wastebinRatio, long maxDays) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        RootDirectory rootDirectory = DriveSettings.buildRootDirectory(rootFile);
        de.mein.drive.data.DriveSettings driveSettings = new de.mein.drive.data.DriveSettings().setRole(DriveStrings.ROLE_SERVER).setRootDirectory(rootDirectory).setMaxAge(maxDays);
        AFile transferDir = AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR);
        transferDir.mkdirs();
        driveSettings.setTransferDirectory(transferDir);
        driveSettings.setMaxWastebinSize((long) (driveSettings.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
        createDriveService(driveSettings, name);
//        File instanceWorkingDir = meinAuthService.getMeinBoot().createServiceInstanceWorkingDir(service);
//        instanceWorkingDir.mkdirs();
//        File settingsFile = new File(instanceWorkingDir,DriveStrings.SETTINGS_FILE_NAME);
//        driveSettings.setJsonFile(settingsFile);
//        driveSettings.save();
//        meinAuthService.getMeinBoot().bootServices();
//        boot(service, driveSettings);
//        MeinDriveServerService mdss = (MeinDriveServerService) meinAuthService.getMeinService(service.getUuid().v());
//        mdss.start();
//        return mdss;
    }

    public Promise<MeinDriveServerService, Exception, Void> createDriveServerServiceDeferred(String name, AFile rootFile, float wastebinRatio, int maxDays) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        DeferredObject<MeinDriveServerService, Exception, Void> deferred = new DeferredObject<>();
//        RootDirectory rootDirectory = DriveSettings.buildRootDirectory(rootFile);
//        Service service = createService(name);
//        de.mein.drive.data.DriveSettings driveSettings = new de.mein.drive.data.DriveSettings().setRole(DriveStrings.ROLE_SERVER).setRootDirectory(rootDirectory);
//        AFile transferDir = AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR);
//        transferDir.mkdirs();
//        driveSettings.setTransferDirectory(transferDir);
//        driveSettings.setMaxWastebinSize((long) (driveSettings.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
//        boot(service, driveSettings);
//        MeinDriveServerService mdss = (MeinDriveServerService) meinAuthService.getMeinService(service.getUuid().v());
//        mdss.start();
//        deferred.resolve(mdss);
        return deferred;
    }

    public void createDriveClientService(String name, AFile rootFile, Long certId, String serviceUuid, float wastebinRatio, long maxDays) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, ClassNotFoundException, SQLException, InstantiationException, IOException, InterruptedException {
        Certificate certificate = meinAuthService.getCertificateManager().getTrustedCertificateById(certId);
//        //create Service
        RootDirectory rootDirectory = DriveSettings.buildRootDirectory(rootFile);
        de.mein.drive.data.DriveSettings driveSettingsCfg = new de.mein.drive.data.DriveSettings().setRole(DriveStrings.ROLE_CLIENT).setRootDirectory(rootDirectory);
        driveSettingsCfg.setTransferDirectory(AFile.instance(rootDirectory.getOriginalFile(), DriveStrings.TRANSFER_DIR));
        driveSettingsCfg.setMaxWastebinSize((long) (driveSettingsCfg.getRootDirectory().getOriginalFile().getUsableSpace() * wastebinRatio));
        driveSettingsCfg.setMaxAge(maxDays);
        driveSettingsCfg.getClientSettings().setInitFinished(false);
        driveSettingsCfg.getClientSettings().setServerCertId(certId);
        driveSettingsCfg.getClientSettings().setServerServiceUuid(serviceUuid);
        createDriveService(driveSettingsCfg, name);

////        driveSettingsCfg.getClientSettings().setServerCertId(certId).setServerServiceUuid(serviceUuid);
//        boot(service, driveSettingsCfg);
//        MeinDriveClientService meinDriveClientService = (MeinDriveClientService) meinAuthService.getMeinService(service.getUuid().v());
//        de.mein.drive.data.DriveSettings driveSettings = meinDriveClientService.getDriveSettings();
//
//        // approve server
//        meinAuthService.getDatabaseManager().grant(service.getId().v(), certId);
//        Lok.debug("DriveCreateController.createDriveClientService");
//        Lok.debug("approve successful? " + meinAuthService.getDatabaseManager().isApproved(certId, service.getId().v()));
//
//        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId);
//        DriveDetails driveDetails = new DriveDetails().setRole(DriveStrings.ROLE_CLIENT).setLastSyncVersion(0).setServiceUuid(service.getUuid().v());
//        connected.done(validationProcess -> runner.runTry(() -> validationProcess.request(serviceUuid, DriveStrings.INTENT_REG_AS_CLIENT, driveDetails).done(result -> runner.runTry(() -> {
//            Lok.debug("DriveCreateController.createDriveClientServiceAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
//            driveSettings.getClientSettings().setServerCertId(certId).setServerServiceUuid(serviceUuid);
//            driveSettings.save();
//            meinDriveClientService.start();
//            deferred.resolve(meinDriveClientService);
//        })))).fail(result -> runner.runTry(() -> {
//            Lok.debug("DriveCreateController.createDriveClientService.FAIL");
//            result.printStackTrace();
//            meinAuthService.getDatabaseManager().revoke(service.getId().v(), certId);
//            deferred.reject(result);
//        }));
    }

}
