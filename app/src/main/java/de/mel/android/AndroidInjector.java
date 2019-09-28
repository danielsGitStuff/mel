package de.mel.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import de.mel.Lok;
import de.mel.MelInjector;
import de.mel.android.drive.bash.BashToolsAndroid;
import de.mel.android.drive.nio.FileDistributorFactoryAndroid;
import de.mel.android.drive.watchdog.RecursiveWatcher;
import de.mel.auth.tools.N;
import de.mel.contacts.ContactsInjector;
import de.mel.contacts.data.ContactStrings;
import de.mel.drive.DriveInjector;
import de.mel.drive.bash.BashTools;
import de.mel.drive.bash.BashToolsImpl;
import de.mel.drive.data.DriveStrings;
import de.mel.android.sql.AndroidDBConnection;
import de.mel.execute.SqliteExecutorInjection;
import de.mel.android.sql.AndroidSQLQueries;
import de.mel.sql.conn.SQLConnection;

/**
 * Created by xor on 3/8/17.
 */

public class AndroidInjector {
    private AndroidInjector() {
    }

    public static void inject(Context context, AssetManager assetManager) throws IOException {
        MelInjector.setExecutorImpl(new SqliteExecutorInjection() {
            @Override
            public void executeStream(SQLConnection con, InputStream in) {
                N.r(() -> {
                    SQLiteDatabase db = ((AndroidDBConnection) con).getDb();
                    Scanner scanner = new Scanner(in, "UTF-8").useDelimiter(";");
                    while (scanner.hasNext()) {
                        String sql = scanner.next();
                        // "create trigger" hackery
                        if (sql.trim().toLowerCase().startsWith("create trigger "))
                            sql += "; " + scanner.next();
                        Lok.debug("SqliteExecutor.executeStream: " + sql);
                        db.execSQL(sql);
                    }
                });
            }

            @Override
            public boolean checkTableExists(SQLConnection connection, String tableName) {
                SQLiteDatabase db = ((AndroidDBConnection) connection).getDb();
                db.rawQuery("select * from " + tableName, null);
                return true;
            }
        });
        MelInjector.setMelAuthSqlInputStreamInjector(() -> {
            try {
                return assetManager.open("de/mel/auth/sql.sql");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
        MelInjector.setSQLConnectionCreator(databaseManager -> {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "melauth", null, 1) {
                @Override
                public void onConfigure(SQLiteDatabase db) {
                    Lok.debug("configure sqlite for melauth");
                    super.onConfigure(db);
                    // WAL does not work with multiple threads
                    db.disableWriteAheadLogging();
                    db.setForeignKeyConstraintsEnabled(true);
                }

                @Override
                public void onOpen(SQLiteDatabase db) {
                    super.onOpen(db);
                }

                @Override
                public void onCreate(SQLiteDatabase db) {
                    Lok.debug("AndroidDriveBootloader.onCreate");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    Lok.debug("AndroidDriveBootloader.onUpgrade");
                }
            };
            return new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
        });
        MelInjector.setBase64Encoder(bytes -> Base64.encode(bytes, Base64.NO_WRAP));
        MelInjector.setBase64Decoder(string -> Base64.decode(string, Base64.NO_WRAP));
        // drive
        DriveInjector.setFileDistributorFactory(new FileDistributorFactoryAndroid());
        DriveInjector.setSqlConnectionCreator((driveDatabaseManager, uuid) -> {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "service." + uuid + "." + DriveStrings.DB_FILENAME, null, DriveStrings.DB_VERSION) {
                @Override
                public void onConfigure(SQLiteDatabase db) {
                    Lok.debug("configure sqlite for drive");
                    super.onConfigure(db);
                    db.setForeignKeyConstraintsEnabled(true);
                    db.disableWriteAheadLogging();
                }

                @Override
                public void onCreate(SQLiteDatabase db) {
                    Lok.debug("AndroidDriveBootloader.onCreate");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    Lok.debug("AndroidDriveBootloader.onUpgrade");
                }
            };
            return new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
        });
        DriveInjector.setDriveSqlInputStreamInjector(() -> {
            try {
                return assetManager.open("de/mel/drive/drive.sql");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
        // use a proper bash tools variant
        BashToolsImpl bashTools;
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
//           bashTools = new SAFBashTools(context);
//        } else {
        bashTools = new BashToolsAndroid(context);
//        }
        BashTools.setInstance(bashTools);
        DriveInjector.setWatchDogRunner(RecursiveWatcher::new);
        DriveInjector.setBinPath("/system/bin/sh");

        //contacts
        ContactsInjector.setSqlConnectionCreator((driveDatabaseManager, uuid) -> {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "service." + uuid + "." + ContactStrings.DB_FILENAME, null, ContactStrings.DB_VERSION) {
                @Override
                public void onConfigure(SQLiteDatabase db) {
                    super.onConfigure(db);
                    db.disableWriteAheadLogging();
                    db.setForeignKeyConstraintsEnabled(true);
                }

                @Override
                public void onCreate(SQLiteDatabase db) {
                    Lok.debug("AndroidDriveBootloader.onCreate");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    Lok.debug("AndroidDriveBootloader.onUpgrade");
                }
            };
            return new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
        });
        ContactsInjector.setDriveSqlInputStreamInjector(() -> {
            try {
                return assetManager.open("de/mel/contacts/contacts.sql");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }
}