package de.mein.drive;

import de.mein.auth.boot.BootLoader;
import de.mein.auth.data.JsonSettings;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.NoTryRunner;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.index.Indexer;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.watchdog.IndexWatchdogListener;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SQLQueries;
import de.mein.sql.SqlQueriesException;
import de.mein.sql.con.SQLConnection;
import de.mein.sql.con.SQLConnector;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by xor on 16.08.2016.
 */
public class DriveBootLoader extends BootLoader {

    public DriveBootLoader() {
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
    public void boot(MeinAuthService meinAuthService, List<Service> services) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        for (Service service : services) {
            NoTryRunner.run(() -> {
                File jsonFile = new File(bootLoaderDir.getAbsolutePath() + File.separator + service.getUuid().v() + File.separator + "drive.settings.json");

                DriveSettings driveSettings = (DriveSettings) JsonSettings.load(jsonFile);
                //driveSettings.getRootDirectory().setOriginalFile(new File(driveSettings.getRootDirectory().getPath()));*/
                boot(meinAuthService, service, driveSettings);
            });

        }
    }

    public void boot(MeinAuthService meinAuthService, Service service, DriveSettings driveSettings) throws SqlQueriesException, SQLException, IOException, ClassNotFoundException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
        ServiceType type = databaseManager.getServiceTypeByName(getName());
        MeinDriveService meinDriveService = (driveSettings.isServer()) ?
                new MeinDriveServerService(meinAuthService) : new MeinDriveClientService(meinAuthService);
        meinDriveService.setUuid(service.getUuid().v());
        meinAuthService.registerMeinService(meinDriveService);
        this.startIndexer(meinDriveService, driveSettings);

        System.out.println("DriveBootLoader.boot");
    }


    public void startIndexer(MeinDriveService meinDriveService, DriveSettings driveSettings) throws SQLException, IOException, ClassNotFoundException, SqlQueriesException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        File workingDir = new File(bootLoaderDir.getAbsolutePath() + File.separator + meinDriveService.getUuid());
        workingDir.mkdirs();
        DriveDatabaseManager databaseManager = new DriveDatabaseManager(meinDriveService, workingDir, driveSettings);
        meinDriveService.initDatabase(databaseManager);
    }

/*
    protected SQLConnection createDBConnection(DriveBootLoader bootLoader, String serviceUuid) throws SQLException, ClassNotFoundException {
        File workingDirectory = new File(bootLoaderDir.getAbsolutePath() + File.separator + serviceUuid);
        workingDirectory.mkdirs();
        return SQLConnector.createConnection(workingDirectory.getAbsolutePath() + File.separator + DriveStrings.DB_FILENAME);
    }*/
}
