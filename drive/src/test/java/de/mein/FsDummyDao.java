package de.mein;

import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.dao.FsDao;
import de.mein.sql.ISQLQueries;

/**
 * Created by xor on 10.11.2017.
 */

public class FsDummyDao extends FsDao {
    public FsDummyDao() {
        super(null, null);
    }
}
