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
            insert(waste);
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
        sqlQueries.update(waste, waste.getId().k() + "=?", ISQLQueries.whereArgs(waste.getId().v()));
    }

    /**
     * @return all hashes that are present in waste and transfer
     */
    public List<String> searchTransfer() throws SqlQueriesException {
        TransferDetails transfer = new TransferDetails();
        Waste waste = new Waste();
        String where = "exists (select " + transfer.getHash().k() + " from " + transfer.getTableName() + " t where t." + transfer.getHash().k() + "=w." + waste.getHash().k() + ")";
        return sqlQueries.loadColumn(waste.getHash(), String.class, waste, "w", where, null, null);
    }

    public Waste insert(Waste waste) throws SqlQueriesException {
        //todo debug
        if (!waste.getInplace().v() && waste.getName().v().equals("same1.txt"))
            System.out.println("WasteDao.insert.debug0fj3ß4u");
        Long id = sqlQueries.insert(waste);
        waste.getId().v(id);
        return waste;
    }

    public Waste getWasteByHash(String hash) throws SqlQueriesException {
        Waste waste = new Waste();
        String where = waste.getHash().k() + "=? and " + waste.getInplace().k() + "=? limit 1";
        List<Waste> wastes = sqlQueries.load(waste.getAllAttributes(), waste, where, ISQLQueries.whereArgs(hash, true));
        if (wastes.size() > 0)
            return wastes.get(0);
        return null;
    }

    public void delete(Long id) throws SqlQueriesException {
        Waste waste = new Waste();
        sqlQueries.delete(waste, waste.getId().k() + "=?", ISQLQueries.whereArgs(id));
    }

    public Waste getWasteById(Long id) throws SqlQueriesException {
        Waste waste = new Waste();
        return sqlQueries.loadFirstRow(waste.getAllAttributes(), waste, waste.getId().k() + "=?", ISQLQueries.whereArgs(id), Waste.class);
    }
}
