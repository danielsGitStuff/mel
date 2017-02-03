package de.mein.drive.sql;

import de.mein.auth.data.access.FileRelatedManager;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.DriveSettings;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.sql.dao.TransferDao;
import de.mein.drive.sql.dao.WasteDao;
import de.mein.execute.SqliteExecutor;
import de.mein.sql.SQLConnection;
import de.mein.sql.SQLQueries;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * does everything file (database) related
 * Created by xor on 09.07.2016.
 */
public class DriveDatabaseManager extends FileRelatedManager {
    public static final String DB_FILENAME = "meindrive.db";
    private final MeinDriveService meinDriveService;
    private SQLQueries driveSqlQueries;
    private final Connection dbConnection;
    private FsDao fsDao;
    private StageDao stageDao;
    private final DriveSettings driveSettings;
    private TransferDao transferDao;
    private WasteDao wasteDao;

    public static Connection createSqliteConnection() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    public interface DriveSqlInputStreamInjector {
        InputStream createSqlFileInputStream();
    }

    private static DriveSqlInputStreamInjector driveSqlInputStreamInjector = () -> String.class.getResourceAsStream("/drive.sql");

    public static void setDriveSqlInputStreamInjector(DriveSqlInputStreamInjector driveSqlInputStreamInjector) {
        DriveDatabaseManager.driveSqlInputStreamInjector = driveSqlInputStreamInjector;
    }

    public DriveDatabaseManager(MeinDriveService meinDriveService, File workingDirectory, DriveSettings driveSettingsCfg) throws SQLException, ClassNotFoundException, IOException, JsonDeserializationException, JsonSerializationException, IllegalAccessException, SqlQueriesException {
        super(workingDirectory);

        this.meinDriveService = meinDriveService;
        this.dbConnection = SQLConnection.createSqliteConnection(new File(createWorkingPath() + DB_FILENAME));
        //this.dbConnection = createSqliteConnection();
        Statement st = dbConnection.createStatement();
        /**
         * todo improve sqlite pragma suff
         *  org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
         config.enforceForeignKeys(true);
         config.setSynchronous(SynchronousMode.OFF);

         String url = "jdbc:sqlite:C:/temp/foo.db";


         java.sql.Driver driver = (java.sql.Driver) Class.forName("org.sqlite.JDBC").newInstance();
         java.sql.Connection conn = driver.connect(url, config.toProperties());
         */
        st.execute("PRAGMA synchronous=OFF");
        st.execute("PRAGMA foreign_keys=ON");
        SqliteExecutor sqliteExecutor = new SqliteExecutor(dbConnection);
        if (!sqliteExecutor.checkTablesExist("fsentry", "stage", "stageset", "transfer", "waste")) {
            //find sql file in workingdir
            sqliteExecutor.executeStream(driveSqlInputStreamInjector.createSqlFileInputStream());
            hadToInitialize = true;
        }

        SQLQueries sqlQueries = new DriveSqlQueries(dbConnection);
        fsDao = new FsDao(this, sqlQueries);
        stageDao = new StageDao(this, sqlQueries);
        transferDao = new TransferDao(sqlQueries);
        wasteDao = new WasteDao(sqlQueries);
        File driveSettingsFile = new File(workingDirectory.getAbsolutePath() + File.separator + "drive.settings.json");
        this.driveSettings = DriveSettings.load(fsDao, driveSettingsFile, driveSettingsCfg).setRole(driveSettingsCfg.getRole()).setRootDirectory(driveSettingsCfg.getRootDirectory());
        this.driveSettings.getRootDirectory().backup();
        this.driveSettings.getRootDirectory().setOriginalFile(new File(this.driveSettings.getRootDirectory().getPath()));
        this.driveSettings.setTransferDirectoryPath(driveSettingsCfg.getTransferDirectoryPath());
        fsDao.setDriveSettings(this.driveSettings);
        System.out.println("DriveDatabaseManager.initialised");
    }

    public MeinDriveService getMeinDriveService() {
        return meinDriveService;
    }

    public Connection getDbConnection() {
        return dbConnection;
    }

    public FsDao getFsDao() {
        return fsDao;
    }

    public DriveSettings getDriveSettings() {
        return driveSettings;
    }


    public StageDao getStageDao() {
        return stageDao;
    }

    public void updateVersion() throws IllegalAccessException, IOException, JsonSerializationException, SqlQueriesException {
        long version = getLatestVersion();
        driveSettings.setLastSyncedVersion(version);
        driveSettings.save();
    }

    public TransferDao getTransferDao() {
        return transferDao;
    }

    public long getLatestVersion() throws SqlQueriesException {
        long fs = fsDao.getLatestVersion();
        return fs;
    }

    public List<GenericFSEntry> getDelta(long version) throws SqlQueriesException {
        List<GenericFSEntry> delta = fsDao.getDelta(version);
        return delta;
    }

    public WasteDao getWasteDao() {
        return wasteDao;
    }
}
