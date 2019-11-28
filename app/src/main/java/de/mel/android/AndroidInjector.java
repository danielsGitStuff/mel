package de.mel.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import de.mel.Lok;
import de.mel.MelInjector;
import de.mel.android.filesync.bash.BashToolsAndroid;
import de.mel.android.filesync.bash.SAFBashTools;
import de.mel.android.filesync.nio.FileDistributorFactoryAndroid;
import de.mel.android.filesync.watchdog.AndroidFileWatcher;
import de.mel.android.filesync.watchdog.AndroidFileWatcherFactory;
import de.mel.android.filesync.watchdog.RecursiveWatcher;
import de.mel.auth.tools.N;
import de.mel.contacts.ContactsInjector;
import de.mel.contacts.data.ContactStrings;
import de.mel.filesync.FileSyncInjector;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.BashToolsImpl;
import de.mel.filesync.data.FileSyncStrings;
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
        FileSyncInjector.setFileDistributorFactory(new FileDistributorFactoryAndroid());
        FileSyncInjector.setSqlConnectionCreator((driveDatabaseManager, uuid) -> {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "service." + uuid + "." + FileSyncStrings.DB_FILENAME, null, FileSyncStrings.DB_VERSION) {
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
        // use a proper bash tools variant
        BashTools bashTools;
        bashTools = N.result(() -> {
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)
                return new SAFBashTools();
            else
                return new BashToolsAndroid(context);
        });
        BashTools.Companion.setImplementation(bashTools);
        // use a proper file watcher
        FileSyncInjector.setFileWatcherFactory(new AndroidFileWatcherFactory());
        FileSyncInjector.setBinPath("/system/bin/sh");

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
