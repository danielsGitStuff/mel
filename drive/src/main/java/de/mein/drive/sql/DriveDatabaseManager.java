package de.mein.drive.sql;

import de.mein.Lok;
import de.mein.auth.data.access.FileRelatedManager;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.dao.*;
import de.mein.execute.SqliteExecutor;
import de.mein.sql.*;
import de.mein.sql.conn.SQLConnector;
import de.mein.sql.transform.SqlResultTransformer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * does everything file (database) related
 * Created by xor on 09.07.2016.
 */
public class DriveDatabaseManager extends FileRelatedManager {
    private final MeinDriveService meinDriveService;
    private final ISQLQueries sqlQueries;
    private final FileDistTaskDao fileDistTaskDao;
    private FsDao fsDao;
    private StageDao stageDao;
    private final DriveSettings driveSettings;
    private TransferDao transferDao;
    private WasteDao wasteDao;

    public void shutDown() {
        sqlQueries.onShutDown();
    }

    public void cleanUp() throws SqlQueriesException {
        Stage stage = new Stage();
        StageSet stageSet = new StageSet();
        String stmt1 = "delete from " + stage.getTableName();
        String stmt2 = "delete from " + stageSet.getTableName();
        stageDao.getSqlQueries().execute(stmt1, null);
        stageDao.getSqlQueries().execute(stmt2, null);
    }

    public FileDistTaskDao getFileDistTaskDao() {
        return fileDistTaskDao;
    }

    public interface SQLConnectionCreator {
        ISQLQueries createConnection(DriveDatabaseManager driveDatabaseManager, String uuid) throws SQLException, ClassNotFoundException;
    }

    public static SQLConnectionCreator getSqlqueriesCreator() {
        return sqlqueriesCreator;
    }

    private static SQLConnectionCreator sqlqueriesCreator = (driveDatabaseManager, uuid) -> {
        File f = new File(driveDatabaseManager.createWorkingPath() + DriveStrings.DB_FILENAME);
        return new SQLQueries(SQLConnector.createSqliteConnection(f), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
    };

    public static void setSqlqueriesCreator(SQLConnectionCreator sqlqueriesCreator) {
        DriveDatabaseManager.sqlqueriesCreator = sqlqueriesCreator;
    }

    public static Connection createSqliteConnection() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    public interface DriveSqlInputStreamInjector {
        InputStream createSqlFileInputStream();
    }

    private static DriveSqlInputStreamInjector driveSqlInputStreamInjector = () -> DriveDatabaseManager.class.getClassLoader().getResourceAsStream("de/mein/drive/drive.sql");

    public static void setDriveSqlInputStreamInjector(DriveSqlInputStreamInjector driveSqlInputStreamInjector) {
        DriveDatabaseManager.driveSqlInputStreamInjector = driveSqlInputStreamInjector;
    }

    public DriveDatabaseManager(MeinDriveService meinDriveService, File workingDirectory, DriveSettings driveSettings) throws SQLException, ClassNotFoundException, IOException, JsonDeserializationException, JsonSerializationException, IllegalAccessException, SqlQueriesException {
        super(workingDirectory);

        this.meinDriveService = meinDriveService;
        this.driveSettings = driveSettings;
//        this.dbConnection = sqlConnection; //sqlqueriesCreator.createConnection(this);//
        //SQLConnector.createSqliteConnection(new File(createWorkingPath() + DriveStrings.DB_FILENAME));
        //this.dbConnection = createSqliteConnection();
        /**
         * todo improve sqlite pragma suff
         *  org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
         config.enforceForeignKeys(true);
         config.setSynchronous(SynchronousMode.OFF);

         String url = "jdbc:sqlite:C:/temp/foo.db";


         java.sql.Driver driver = (java.sql.Driver) Class.forName("org.sqlite.JDBC").newInstance();
         java.sql.Connection conn = driver.connect(url, config.toProperties());
         */
        sqlQueries = sqlqueriesCreator.createConnection(this, meinDriveService.getUuid());
        {
            Lok.error("synchronous PRAGMA is turned off!");
            SQLStatement st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA synchronous=OFF");
            st.execute();
        }
        {
            SQLStatement st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA foreign_keys=ON");
            st.execute();
        }
        sqlQueries.getSQLConnection().setAutoCommit(false);
//        sqlQueries.enableWAL();

        SqliteExecutor sqliteExecutor = new SqliteExecutor(sqlQueries.getSQLConnection());
        if (!sqliteExecutor.checkTablesExist("fsentry", "stage", "stageset", "transfer", "waste", "filedist")) {
            //find sql file in workingdir
            sqliteExecutor.executeStream(driveSqlInputStreamInjector.createSqlFileInputStream());
            hadToInitialize = true;
        }
        this.driveSettings.getRootDirectory().backup();


        fsDao = new FsDao(this, sqlQueries);
        stageDao = new StageDao(driveSettings, sqlQueries, fsDao);
        transferDao = new TransferDao(sqlQueries);
        wasteDao = new WasteDao(sqlQueries);
        fileDistTaskDao = new FileDistTaskDao(sqlQueries);


        fsDao.setDriveSettings(this.driveSettings);
        transferDao.resetStarted();
        Lok.debug("DriveDatabaseManager.initialised");
    }

    public MeinDriveService getMeinDriveService() {
        return meinDriveService;
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

    public ISQLResource<GenericFSEntry> getDeltaResource(long version) throws SqlQueriesException {
        return fsDao.getDeltaResource(version);
    }

    public WasteDao getWasteDao() {
        return wasteDao;
    }
}
