package de.mel;

import de.mel.drive.sql.DriveDatabaseManager;
import de.mel.drive.sql.dao.FsDao;
import de.mel.sql.ISQLQueries;

/**
 * Created by xor on 10.11.2017.
 */

public class FsDummyDao extends FsDao {
    public FsDummyDao() {
        super(null, null);
    }
}
