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
import de.mein.auth.tools.NoTryRunner;
import de.mein.drive.DriveInjector;
import de.mein.drive.data.DriveStrings;
import de.mein.android.drive.service.AndroidDBConnection;
import de.mein.android.drive.watchdog.AndroidWatchdogListener;
import de.mein.execute.SqliteExecutorInjection;
import de.mein.android.sql.AndroidSQLQueries;
import de.mein.sql.con.SQLConnection;

/**
 * Created by xor on 3/8/17.
 */

public class AndroidInjector {
    private AndroidInjector() {
    }

    public static void inject(Context context, AssetManager assetManager) throws IOException {
        InputStream sqlInput = assetManager.open("sql.sql");
        InputStream driveSqlInput = assetManager.open("drive0.sql");
        MeinInjector.setExecutorImpl(new SqliteExecutorInjection() {
            @Override
            public void executeStream(SQLConnection con, InputStream in) {
                NoTryRunner.run(() -> {
                    SQLiteDatabase db = ((AndroidDBConnection) con).getDb();
                    Scanner scanner = new Scanner(in, "UTF-8").useDelimiter(";");
                    while (scanner.hasNext()) {
                        String sql = scanner.next();
                        System.out.println("SqliteExecutor.executeStream: " + sql);
                        db.execSQL(sql);
                    }
                });
            }

            @Override
            public boolean checkTableExists(SQLConnection connection, String tableName) {
                SQLiteDatabase db = ((AndroidDBConnection) connection).getDb();
                db.rawQuery("select * from "+tableName,null);
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
                return assetManager.open("drive0.sql");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
        DriveInjector.setWatchDogRunner(meinDriveService -> new AndroidWatchdogListener(meinDriveService));
        DriveInjector.setBinPath("/system/bin/sh");
    }
}
