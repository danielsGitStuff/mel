package de.mein.drive.sql.dao;

import de.mein.drive.sql.TransferDetails;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.Pair;
import de.mein.sql.SqlQueriesException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 12/16/16.
 */
public class TransferDao extends Dao {
    public TransferDao(ISQLQueries ISQLQueries) {
        super(ISQLQueries);
    }

    public TransferDao(ISQLQueries ISQLQueries, boolean lock) {
        super(ISQLQueries, lock);
    }

    public void insert(TransferDetails transferDetails) throws SqlQueriesException {
        Long id = sqlQueries.insert(transferDetails);
        transferDetails.getId().v(id);
    }

    public TransferDetails getOneTransfer() throws SqlQueriesException {
        TransferDetails dummy = new TransferDetails();
        List<TransferDetails> res = sqlQueries.load(dummy.getAllAttributes(), dummy, null, null, " order by " + dummy.getId().k() + " limit 1");
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public void delete(Long id) throws SqlQueriesException {
        TransferDetails dummy = new TransferDetails();
        sqlQueries.delete(dummy, dummy.getId().k() + "=?", ISQLQueries.whereArgs(id));
    }

    public List<TransferDetails> getTwoTransferSets() throws SqlQueriesException {
        TransferDetails dummy = new TransferDetails();
        String whatElse = "group by " + dummy.getCertId().k() + "," + dummy.getServiceUuid().k() + " limit 2";
        List<Pair<?>> columns = new ArrayList<>();
        columns.add(dummy.getCertId());
        columns.add(dummy.getServiceUuid());
        List<TransferDetails> result = sqlQueries.load(columns, dummy, null, null, whatElse);
        return result;
    }


    public List<TransferDetails> getTransfers(Long certId, String serviceUuid, int limit) throws SqlQueriesException {
        TransferDetails dummy = new TransferDetails();
        String where = dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=?";
        String whatElse = " limit ?";
        List<TransferDetails> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(certId, serviceUuid, limit), whatElse);
        return result;
    }
}
