package de.mel.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.util.Pair;

import java.io.InputStream;

import de.mel.Lok;
import de.mel.LokImpl;
import de.mel.android.sql.AndroidDBConnection;
import de.mel.android.sql.AndroidSQLQueries;
import de.mel.auth.tools.DBLokImpl;
import de.mel.auth.tools.N;
import de.mel.execute.SqliteExecutor;

public class AndroidLok extends DBLokImpl {

    /**
     * setup Lok according to android app settings
     *
     * @param context
     */
    public static void setupDbLok(Context context) {
        boolean timestamp = Tools.getSharedPreferences().getBoolean(PreferenceStrings.LOK_TIMESTAMP, true);
        int lines = Tools.getSharedPreferences().getInt(PreferenceStrings.LOK_LINE_COUNT, 0);
        Long preserveLokLines = Tools.getSharedPreferences().getLong(PreferenceStrings.LOK_PRESERVED_LINES, 1000L);
        AndroidLok lokImpl = (AndroidLok) new AndroidLok(preserveLokLines).setPrintDebug(true).setup(lines, timestamp);
        if (preserveLokLines > 0L && !(Lok.getImpl() instanceof AndroidLok)) {
            SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "log", null, 1) {
                @Override
                public void onConfigure(SQLiteDatabase db) {
                    Lok.debug("configure sqlite for log");
                    super.onConfigure(db);
                    db.disableWriteAheadLogging();
                    db.setForeignKeyConstraintsEnabled(true);
                }

                @Override
                public void onOpen(SQLiteDatabase db) {
                    super.onOpen(db);
                }

                @Override
                public void onCreate(SQLiteDatabase db) {
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                }
            };
            AndroidSQLQueries sqlQueries = new AndroidSQLQueries(new AndroidDBConnection(helper.getWritableDatabase()));
            AssetManager assetManager = context.getAssets();
            N.r(() -> {
                SqliteExecutor sqliteExecutor = new SqliteExecutor(sqlQueries.getSQLConnection());
                if (!sqliteExecutor.checkTableExists("log")) {
                    InputStream inputStream = assetManager.open("de/mel/auth/log.sql");
                    sqliteExecutor.executeStream(inputStream);
                }
                lokImpl.setupLogToDb(sqlQueries);
            });
        }
        Lok.setLokImpl(lokImpl);
    }


    public AndroidLok(Long preserveLogLinesInDb) {
        super(preserveLogLinesInDb);
        this.stackIndex = 6;
    }

    private Pair<String, String> fabricatePair(String mode, Object msg) {
        StackTraceElement stackTrace = findStackElement();
        String tag = stackTrace.getFileName();
        String line = super.fabricate(findStackElement(), mode, msg, false);
        return new Pair<>(tag, line);
    }

    @Override
    public void debug(Object msg) {
        if (printDebug) {
            Pair<String, String> pair = fabricatePair("d", msg);
            Log.d(pair.first, pair.second);
            storeToDb(pair.first, order.getAndIncrement(), pair.second);
        }
    }

    @Override
    public void error(Object msg) {
        if (printError) {
            Pair<String, String> pair = fabricatePair("e", msg);
            Log.e(pair.first, pair.second);
            storeToDb(pair.first, order.getAndIncrement(), pair.second);
        }
    }

    @Override
    public void warn(Object msg) {
        if (printWarn) {
            Pair<String, String> pair = fabricatePair("w", msg);
            Log.w(pair.first, pair.second);
            storeToDb(pair.first, order.getAndIncrement(), pair.second);
        }
    }

    @Override
    public void info(Object msg) {
        if (printInfo) {
            Pair<String, String> pair = fabricatePair("i", msg);
            Log.i(pair.first, pair.second);
            storeToDb(pair.first, order.getAndIncrement(), pair.second);
        }
    }


}
