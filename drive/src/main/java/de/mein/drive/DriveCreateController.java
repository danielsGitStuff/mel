package de.mein.drive;

import de.mein.auth.boot.MeinBoot;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Created by xor on 10/26/16.
 */
public class DriveCreateController {
    private final MeinAuthService meinAuthService;
    private N runner = new N(Throwable::printStackTrace);

    public DriveCreateController(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }

    private RootDirectory buildRootDirectory(String path) throws IllegalAccessException, JsonSerializationException, JsonDeserializationException {
        RootDirectory rootDirectory = new RootDirectory().setPath(path);
        rootDirectory.setOriginalFile(new File(path));
        rootDirectory.backup();
        return rootDirectory;
    }

    private Service createService(String name) throws SqlQueriesException {
        ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeByName(new DriveBootLoader().getName());
        Service service = meinAuthService.getDatabaseManager().createService(type.getId().v(), name);
        return service;
    }

    private void boot(Service service, DriveSettings driveSettings) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        DriveBootLoader driveBootLoader = (DriveBootLoader) MeinBoot.getBootLoader(meinAuthService, new DriveBootLoader().getName());
        driveBootLoader.boot(meinAuthService, service, driveSettings);
    }

    public MeinDriveServerService createDriveServerService(String name, String path) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, InstantiationException, SQLException, IOException, ClassNotFoundException {
        RootDirectory rootDirectory = buildRootDirectory(path);
        Service service = createService(name);
        DriveSettings driveSettings = new DriveSettings().setRole(DriveStrings.ROLE_SERVER).setRootDirectory(rootDirectory);
        driveSettings.setTransferDirectoryPath(rootDirectory.getPath()+File.separator+DriveSettings.TRANSFER_DIR);
        boot(service, driveSettings);
        return (MeinDriveServerService) meinAuthService.getMeinService(service.getUuid().v());
    }

    public Promise<MeinDriveClientService, Exception, Void> createDriveClientService(String name, String path, Long certId, String serviceUuid) throws SqlQueriesException, IllegalAccessException, JsonSerializationException, JsonDeserializationException, ClassNotFoundException, SQLException, InstantiationException, IOException, InterruptedException {
        meinAuthService.getDatabaseManager().lockWrite();
        DeferredObject<MeinDriveClientService, Exception, Void> deferred = new DeferredObject<>();
        Certificate certificate = meinAuthService.getCertificateManager().getTrustedCertificateById(certId);
        //create Service
        RootDirectory rootDirectory = buildRootDirectory(path);
        Service service = createService(name);
        DriveSettings driveSettingsCfg = new DriveSettings().setRole(DriveStrings.ROLE_CLIENT).setRootDirectory(rootDirectory);
        driveSettingsCfg.setTransferDirectoryPath(rootDirectory.getPath() + File.separator + DriveSettings.TRANSFER_DIR);
        boot(service, driveSettingsCfg);
        MeinDriveClientService meinDriveClientService = (MeinDriveClientService) meinAuthService.getMeinService(service.getUuid().v());
        DriveSettings driveSettings = meinDriveClientService.getDriveSettings();

        // approve server
        meinAuthService.getDatabaseManager().grant(service.getId().v(), certId);
        System.out.println("DriveCreateController.createDriveClientService");
        System.out.println("approve successful? " + meinAuthService.getDatabaseManager().isApproved(certId, service.getId().v()));

        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(certId, certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
        DriveDetails driveDetails = new DriveDetails().setRole(DriveStrings.ROLE_CLIENT).setLastSyncVersion(0).setServiceUuid(service.getUuid().v());
        connected.done(validationProcess -> runner.runTry(() -> validationProcess.request(serviceUuid, DriveStrings.INTENT_REG_AS_CLIENT, driveDetails).done(result -> runner.runTry(() -> {
            System.out.println("DriveCreateController.createDriveClientServiceAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            driveSettings.getClientSettings().setServerCertId(certId).setServerServiceUuid(serviceUuid);
            driveSettings.save();
            deferred.resolve(meinDriveClientService);
        })))).fail(result -> runner.runTry(() -> {
            System.out.println("DriveCreateController.createDriveClientService.FAIL");
            result.printStackTrace();
            meinAuthService.getDatabaseManager().revoke(service.getId().v(), certId);
            deferred.reject(result);
        }));
        meinAuthService.getDatabaseManager().unlockWrite();
        return deferred;
    }
}
