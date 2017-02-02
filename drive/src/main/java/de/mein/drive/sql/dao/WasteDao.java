package de.mein.drive.sql.dao;

import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.Waste;
import de.mein.sql.Dao;
import de.mein.sql.SQLQueries;
import de.mein.sql.SqlQueriesException;

import java.util.List;

/**
 * Created by xor on 1/27/17.
 */
public class WasteDao extends Dao.LockingDao {
    public WasteDao(SQLQueries sqlQueries) {
        super(sqlQueries);
    }

    public WasteDao(SQLQueries sqlQueries, boolean lock) {
        super(sqlQueries, lock);
    }

    public void fsToWaste(FsFile file) throws SqlQueriesException {
        Waste waste = new Waste();
        waste.getHash().v(file.getContentHash());
        waste.getInode().v(file.getiNode());
        waste.getInplace().v(false);
        waste.getName().v(file.getName());
        waste.getSize().v(file.getSize());
        waste.getModified().v(file.getModified());
        sqlQueries.insert(waste);
    }

    public Waste getWasteByInode(Long inode) throws SqlQueriesException {
        Waste dummy = new Waste();
        String where = dummy.getInode().k() + "=?";
        List<Waste> wastes = sqlQueries.load(dummy.getAllAttributes(), dummy, where, SQLQueries.whereArgs(inode));
        if (wastes.size() > 0)
            return wastes.get(0);
        return null;
    }

    public void update(Waste waste) throws SqlQueriesException {
        sqlQueries.update(waste, waste.getHash().k() + "=?", SQLQueries.whereArgs(waste.getHash().v()));
    }
}
