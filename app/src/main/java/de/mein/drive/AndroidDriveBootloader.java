package de.mein.drive;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import de.mein.AndroidBootLoader;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.MeinAuthService;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;
import de.mein.sql.con.AndroidSQLQueries;
import de.mein.sql.con.SQLConnection;

/**
 * Created by xor on 2/4/17.
 */

public class AndroidDriveBootloader extends DriveBootLoader implements AndroidBootLoader {

    @Override
    public View createView(MeinAuthService meinAuthService,View parentView){
        return null;
    }

}
