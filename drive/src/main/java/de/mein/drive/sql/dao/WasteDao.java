package de.mein.drive.sql.dao;

import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.TransferDetails;
import de.mein.drive.sql.Waste;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.JoinObject;
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
        JoinObject joinObject = new JoinObject() {
            @Override
            public String getTableName() {
                return waste.getTableName();
//                return waste.getTableName() + " w inner join " + transfer.getTableName()
//                        + " t on w." + waste.getHash().k() + " = t." + transfer.getHash().k();
            }
        };
        String where = waste.getHash().k() + " exists in (select " + transfer.getHash().k() + " from " + transfer.getTableName()+")";
        // Pair<String> col = new Pair<String>(waste.getHash().getGenericClass(),waste.getHash().k()+" as")
        return sqlQueries.loadColumn(waste.getHash(), String.class, joinObject, where, null, null);
    }
}
