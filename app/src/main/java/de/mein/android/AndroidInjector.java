package de.mein.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import de.mein.MeinInjector;
import de.mein.android.drive.bash.BashToolsAndroid;
import de.mein.android.drive.bash.SAFBashTools;
import de.mein.android.drive.watchdog.RecursiveWatcher;
import de.mein.auth.tools.N;
import de.mein.contacts.ContactsInjector;
import de.mein.contacts.data.ContactStrings;
import de.mein.drive.DriveInjector;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.BashToolsImpl;
import de.mein.drive.data.DriveStrings;
import de.mein.android.sql.AndroidDBConnection;
import de.mein.execute.SqliteExecutorInjection;
import de.mein.android.sql.AndroidSQLQueries;
import de.mein.sql.conn.SQLConnection;

/**
 * Created by xor on 3/8/17.
 */

public class AndroidInjector {
    private AndroidInjector() {
    }

    public static void inject(Context context, AssetManager assetManager) throws IOException {
        MeinInjector.setExecutorImpl(new SqliteExecutorInjection() {
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
                        System.out.println("SqliteExecutor.executeStream: " + sql);
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
        MeinInjector.setMeinAuthSqlInputStreamInjector(() -> {
            try {
                return assetManager.open("sql.sql");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
        MeinInjector.setSQLConnectionCreator(databaseManager -> {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "meinauth", null, 1) {
                @Override
                public void onCreate(SQLiteDatabase db) {
                    System.out.println("AndroidDriveBootloader.onCreate");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    System.out.println("AndroidDriveBootloader.onUpgrade");
                }
            };
            return new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
        });
        MeinInjector.setBase64Encoder(bytes -> Base64.encode(bytes, Base64.NO_WRAP));
        MeinInjector.setBase64Decoder(string -> Base64.decode(string, Base64.NO_WRAP));
        // drive
        DriveInjector.setSqlConnectionCreator((driveDatabaseManager, uuid) -> {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "service." + uuid + "." + DriveStrings.DB_FILENAME, null, DriveStrings.DB_VERSION) {
                @Override
                public void onCreate(SQLiteDatabase db) {
                    System.out.println("AndroidDriveBootloader.onCreate");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    System.out.println("AndroidDriveBootloader.onUpgrade");
                }
            };
            return new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
        });
        DriveInjector.setDriveSqlInputStreamInjector(() -> {
            try {
                return assetManager.open("drive.sql");
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
                public void onCreate(SQLiteDatabase db) {
                    System.out.println("AndroidDriveBootloader.onCreate");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    System.out.println("AndroidDriveBootloader.onUpgrade");
                }
            };
            return new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
        });
        ContactsInjector.setDriveSqlInputStreamInjector(() -> {
            try {
                return assetManager.open("contacts.sql");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }
}
