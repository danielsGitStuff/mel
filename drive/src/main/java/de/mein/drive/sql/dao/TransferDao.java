package de.mein.drive.sql.dao;

import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.SqliteConverter;
import de.mein.drive.sql.TransferDetails;
import de.mein.sql.*;

import java.util.Collection;
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
        return getTransferSets(2);
    }

    /**
     * @param limit
     * @return all TransferSets if limit is null
     */
    public List<TransferDetails> getTransferSets(Integer limit) throws SqlQueriesException {
        TransferDetails dummy = new TransferDetails();
        String where = dummy.getStarted().k() + "=?";
        String whatElse = "group by " + dummy.getCertId().k() + "," + dummy.getServiceUuid().k();
        if (limit != null)
            whatElse += " limit " + limit;
        List<TransferDetails> result = sqlQueries.load(ISQLQueries.columns(dummy.getCertId(), dummy.getServiceUuid()), dummy, where, ISQLQueries.whereArgs(false), whatElse);
        return result;
    }

    public SQLResource<TransferDetails> getTransferResource() {
        return null;
    }


    public List<TransferDetails> getNotStartedTransfers(Long certId, String serviceUuid, int limit) throws SqlQueriesException {
        TransferDetails dummy = new TransferDetails();
        String where = dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=? and " + dummy.getStarted().k() + "=? and " + dummy.getDeleted().k() + "=? and " + dummy.getAvailable().k() + "=? ";
        String whatElse = " limit ?";
        List<TransferDetails> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(certId, serviceUuid, false, false, true, limit), whatElse);
        return result;
    }

    public void deleteByHash(String hash) throws SqlQueriesException {
        TransferDetails transfer = new TransferDetails();
        sqlQueries.delete(transfer, transfer.getHash().k() + "=?", ISQLQueries.whereArgs(hash));
    }

    public void setStarted(Long id, boolean started) throws SqlQueriesException {
        TransferDetails details = new TransferDetails();
        String stmt = "update " + details.getTableName() + " set " + details.getStarted().k() + "=? where " + details.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(true, id));
    }

    public boolean hasNotStartedTransfers(Long certId, String serviceUuid) throws SqlQueriesException {
        TransferDetails dummy = new TransferDetails();
        String query = "select count(*)>0 from " + dummy.getTableName() + " where " + dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=?";
        Integer result = sqlQueries.queryValue(query, Integer.class, ISQLQueries.whereArgs(certId, serviceUuid));
        return SqliteConverter.intToBoolean(result);
    }

    public void resetStarted() throws SqlQueriesException {
        TransferDetails dummy = new TransferDetails();
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getStarted().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(false));
    }

    public int count(Long certId, String serviceUuid) throws SqlQueriesException {
        TransferDetails details = new TransferDetails();
        String query = "select count (*) from " + details.getTableName() + " where " + details.getCertId().k() + "=? and " + details.getServiceUuid().k() + "=?";
        return sqlQueries.queryValue(query, Integer.class, ISQLQueries.whereArgs(certId, serviceUuid));
    }

    public int countStarted(Long certId, String serviceUuid) throws SqlQueriesException {
        TransferDetails details = new TransferDetails();
        String query = "select count (*) from " + details.getTableName() + " where " + details.getStarted().k() + "=? and "
                + details.getCertId().k() + "=? and "
                + details.getServiceUuid().k() + "=?";
        return sqlQueries.queryValue(query, Integer.class, ISQLQueries.whereArgs(true, certId, serviceUuid));
    }

    public void updateTransferredBytes(Long id, Long transferred) throws SqlQueriesException {
        TransferDetails t = new TransferDetails();
        String stmt = "update " + t.getTableName() + " set " + t.getTransferred().k() + "=? where " + t.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(transferred, id));
    }

    public ISQLResource<TransferDetails> getUnnecessaryTransfers() throws SqlQueriesException {
        TransferDetails t = new TransferDetails();
        FsFile f = new FsFile();
        String query = "select * from " + t.getTableName() + " t where not exists (select * from " + f.getTableName()
                + " f where f." + f.getContentHash().k() + "=t." + t.getHash().k() + ")";
        return sqlQueries.loadQueryResource(query, t.getAllAttributes(), TransferDetails.class, null);
    }

    public void flagDeleted(Long transferId, boolean flag) throws SqlQueriesException {
        TransferDetails t = new TransferDetails();
        String stmt = "update " + t.getTableName() + " set " + t.getDeleted().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(flag));
    }

    public void updateAvailableByHashSet(Long certId, String serviceUUID, Collection<String> hashes) throws SqlQueriesException {
        TransferDetails t = new TransferDetails();
        String statement = "update " + t.getTableName() + " set " + t.getAvailable().k() + "=? where " + t.getCertId().k() + "=? and " + t.getServiceUuid().k() + "=? and "
                + t.getHash().k() + " in ";
        statement += ISQLQueries.buildPartIn(hashes);
        List<Object> whereArgs = ISQLQueries.whereArgs(true, certId, serviceUUID);
        whereArgs.addAll(hashes);
        sqlQueries.execute(statement, whereArgs);
    }
}
