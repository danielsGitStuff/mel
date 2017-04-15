package de.mein.drive.sql.dao;

import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.TransferDetails;
import de.mein.drive.sql.Waste;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;

import java.util.List;

/**
 * Created by xor on 1/27/17.
 */
public class WasteDao extends Dao.LockingDao {
    public WasteDao(ISQLQueries ISQLQueries) {
        super(ISQLQueries);
    }

    public WasteDao(ISQLQueries ISQLQueries, boolean lock) {
        super(ISQLQueries, lock);
    }

    /**
     * @param file
     * @throws SqlQueriesException
     */
    public Waste fsToWaste(FsFile file) throws SqlQueriesException {
        Waste waste = Waste.fromFsFile(file);
        try {
            Long id = sqlQueries.insert(waste);
            waste.getId().v(id);
        } catch (Exception e) {
            System.err.println("WasteDao.fsToWaste: " + e.getMessage());
        }
        return waste;
    }

    public Waste getWasteByInode(Long inode) throws SqlQueriesException {
        Waste dummy = new Waste();
        String where = dummy.getInode().k() + "=?";
        List<Waste> wastes = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(inode));
        if (wastes.size() > 0)
            return wastes.get(0);
        return null;
    }

    public void update(Waste waste) throws SqlQueriesException {
        sqlQueries.update(waste, waste.getHash().k() + "=?", ISQLQueries.whereArgs(waste.getHash().v()));
    }

    /**
     * @return all hashes that are present in waste and transfer
     */
    public List<String> searchTransfer() throws SqlQueriesException {
        TransferDetails transfer = new TransferDetails();
        Waste waste = new Waste();
        String where = " exists (select " + transfer.getHash().k() + " from " + transfer.getTableName() + " t where t." + transfer.getHash().k() + "=" + waste.getHash().k() + ")";
        return sqlQueries.loadColumn(waste.getHash(), String.class, waste, where, null, null);
    }
}
