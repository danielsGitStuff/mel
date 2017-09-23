package mein.de.contacts.data.db;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import de.mein.auth.data.access.FileRelatedManager;
import de.mein.execute.SqliteExecutor;
import de.mein.sql.ISQLQueries;
import de.mein.sql.RWLock;
import de.mein.sql.SQLQueries;
import de.mein.sql.SQLStatement;
import de.mein.sql.conn.SQLConnector;
import de.mein.sql.transform.SqlResultTransformer;
import mein.de.contacts.data.ContactStrings;
import mein.de.contacts.data.ContactsSettings;
import mein.de.contacts.service.ContactsService;

/**
 * Created by xor on 9/23/17.
 */

public class ContactsDatabaseManager extends FileRelatedManager {
    private final ISQLQueries sqlQueries;
    private final ContactsService contactsService;
    private final ContactsSettings settings;

    public ContactsDatabaseManager(ContactsService contactsService, File workingDirectory) throws SQLException, ClassNotFoundException, IOException {
        super(workingDirectory);

        this.contactsService = contactsService;
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
        sqlQueries = sqlqueriesCreator.createConnection(this, contactsService.getUuid());
        SQLStatement st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA synchronous=OFF");
        st.execute();
        st = sqlQueries.getSQLConnection().prepareStatement("PRAGMA foreign_keys=ON");
        st.execute();
        SqliteExecutor sqliteExecutor = new SqliteExecutor(sqlQueries.getSQLConnection());
        if (!sqliteExecutor.checkTablesExist("fsentry", "stage", "stageset", "transfer", "waste")) {
            //find sql file in workingdir
            sqliteExecutor.executeStream(contactsSqlInputStreamInjector.createSqlFileInputStream());
            hadToInitialize = true;
        }

        settings = new ContactsSettings();
        File settingsFile = new File(workingDirectory.getAbsolutePath() + File.separator + "contacts.settings.json");
        settings = ContactsSettings.load(settingsFile, driveSettingsCfg).setRole(driveSettingsCfg.getRole()).setRootDirectory(driveSettingsCfg.getRootDirectory());
        this.driveSettings.getRootDirectory().backup();
        this.driveSettings.getRootDirectory().setOriginalFile(new File(this.driveSettings.getRootDirectory().getPath()));
        this.driveSettings.setTransferDirectoryPath(driveSettingsCfg.getTransferDirectoryPath());

    }

    public interface SQLConnectionCreator {
        ISQLQueries createConnection(ContactsDatabaseManager contactsDatabaseManager, String uuid) throws SQLException, ClassNotFoundException;
    }

    private static SQLConnectionCreator sqlqueriesCreator = (contactsDatabaseManager, uuid) -> new SQLQueries(SQLConnector.createSqliteConnection(new File(contactsDatabaseManager.createWorkingPath() + ContactStrings.DB_FILENAME)), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());

    public static void setSqlqueriesCreator(SQLConnectionCreator sqlqueriesCreator) {
        ContactsDatabaseManager.sqlqueriesCreator = sqlqueriesCreator;
    }

    public interface ContactsSqlInputStreamInjector {
        InputStream createSqlFileInputStream();
    }

    private static ContactsSqlInputStreamInjector contactsSqlInputStreamInjector = () -> String.class.getResourceAsStream("/drive.sql");

    public static void setContactsSqlInputStreamInjector(ContactsSqlInputStreamInjector contactsSqlInputStreamInjector) {
        ContactsDatabaseManager.contactsSqlInputStreamInjector = contactsSqlInputStreamInjector;
    }
}
