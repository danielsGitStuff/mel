package de.mel.drive.sql;

import de.mel.Lok;
import de.mel.auth.data.access.FileRelatedManager;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.drive.data.FileSyncSettings;
import de.mel.drive.data.FileSyncStrings;
import de.mel.drive.service.MelFileSyncService;
import de.mel.drive.sql.dao.*;
import de.mel.execute.SqliteExecutor;
import de.mel.sql.*;
import de.mel.sql.conn.SQLConnector;
import de.mel.sql.transform.SqlResultTransformer;

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
public class FileSyncDatabaseManager extends FileRelatedManager {
    private final MelFileSyncService melFileSyncService;
    private final ISQLQueries sqlQueries;
    private final FileDistTaskDao fileDistTaskDao;
    private FsDao fsDao;
    private StageDao stageDao;
    private final FileSyncSettings fileSyncSettings;
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
        ISQLQueries createConnection(FileSyncDatabaseManager fileSyncDatabaseManager, String uuid) throws SQLException, ClassNotFoundException;
    }

    public static SQLConnectionCreator getSqlqueriesCreator() {
        return sqlqueriesCreator;
    }

    private static SQLConnectionCreator sqlqueriesCreator = (driveDatabaseManager, uuid) -> {
        File f = new File(driveDatabaseManager.createWorkingPath() + FileSyncStrings.DB_FILENAME);
        return new SQLQueries(SQLConnector.createSqliteConnection(f), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
    };

    public static void setSqlqueriesCreator(SQLConnectionCreator sqlqueriesCreator) {
        FileSyncDatabaseManager.sqlqueriesCreator = sqlqueriesCreator;
    }

    public static Connection createSqliteConnection() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    public interface FileSyncSqlInputStreamInjector {
        InputStream createSqlFileInputStream();
    }

    private static FileSyncSqlInputStreamInjector fileSyncSqlInputStreamInjector = () -> FileSyncDatabaseManager.class.getClassLoader().getResourceAsStream("de/mel/filesync/filesync.sql");

    public static void setFileSyncSqlInputStreamInjector(FileSyncSqlInputStreamInjector fileSyncSqlInputStreamInjector) {
        FileSyncDatabaseManager.fileSyncSqlInputStreamInjector = fileSyncSqlInputStreamInjector;
    }

    public FileSyncDatabaseManager(MelFileSyncService melFileSyncService, File workingDirectory, FileSyncSettings fileSyncSettings) throws SQLException, ClassNotFoundException, IOException, JsonDeserializationException, JsonSerializationException, IllegalAccessException, SqlQueriesException {
        super(workingDirectory);
        this.melFileSyncService = melFileSyncService;
        this.fileSyncSettings = fileSyncSettings;

        sqlQueries = sqlqueriesCreator.createConnection(this, melFileSyncService.getUuid());
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
            sqliteExecutor.executeStream(fileSyncSqlInputStreamInjector.createSqlFileInputStream());
            hadToInitialize = true;
        }
        this.fileSyncSettings.getRootDirectory().backup();


        fsDao = new FsDao(this, sqlQueries);
        stageDao = new StageDao(fileSyncSettings, sqlQueries, fsDao);
        transferDao = new TransferDao(sqlQueries);
        wasteDao = new WasteDao(sqlQueries);
        fileDistTaskDao = new FileDistTaskDao(sqlQueries);


        fsDao.setFileSyncSettings(this.fileSyncSettings);
        transferDao.resetStarted();
        Lok.debug("DriveDatabaseManager.initialised");
    }

    public MelFileSyncService getMelFileSyncService() {
        return melFileSyncService;
    }

    public FsDao getFsDao() {
        return fsDao;
    }

    public FileSyncSettings getFileSyncSettings() {
        return fileSyncSettings;
    }


    public StageDao getStageDao() {
        return stageDao;
    }

    public void updateVersion() throws IllegalAccessException, IOException, JsonSerializationException, SqlQueriesException {
        long version = getLatestVersion();
        Lok.debug("updating settings, set version from " + fileSyncSettings.getLastSyncedVersion() + " to " + version);
        fileSyncSettings.setLastSyncedVersion(version);
        fileSyncSettings.save();
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
