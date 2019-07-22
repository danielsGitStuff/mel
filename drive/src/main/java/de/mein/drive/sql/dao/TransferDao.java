package de.mein.drive.sql.dao;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import de.mein.Lok;
import de.mein.auth.data.db.MissingHash;
import de.mein.auth.tools.N;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.SqliteConverter;
import de.mein.drive.sql.TransferDetails;
import de.mein.drive.tasks.AvailHashEntry;
import de.mein.sql.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
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
        try {
            Long id = sqlQueries.insert(transferDetails);
            transferDetails.getId().v(id);
        } catch (SqlQueriesException sqlQueriesException) {
            // non unique transfers happen quite often and thats normal. don't fill the logs with that.
            if (sqlQueriesException.getException() instanceof SQLiteException) {
                SQLiteException sqLiteException = (SQLiteException) sqlQueriesException.getException();
                if (sqLiteException.getResultCode() != SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE)
                    throw sqlQueriesException;

            }
        }

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
        String stmt = "update " + t.getTableName() + " set " + t.getDeleted().k() + "=? where " + t.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(flag, transferId));
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

    public ISQLResource<TransferDetails> getNotStartedNotAvailableTransfers(Long certId) throws SqlQueriesException {
        TransferDetails t = new TransferDetails();
        FsFile f = new FsFile();
        List<Pair<?>> attributes = new ArrayList<>();
        attributes.add(t.getHash());
        String query = "select t." + t.getHash().k() + " from " + t.getTableName() + " t left join " + f.getTableName() + " f on f." + f.getContentHash().k() + " = t." + t.getHash().k() + " " +
                "where t." + t.getStarted().k() + "=? and t." + t.getAvailable().k() + "=? and t." + t.getCertId().k() + "=? group by t." + t.getHash().k();
        ISQLResource<TransferDetails> result = sqlQueries.loadQueryResource(query, attributes, TransferDetails.class, ISQLQueries.whereArgs(false, false, certId));
        return result;
    }


    public synchronized ISQLResource<FsFile> getAvailableTransfersFromHashes(Iterator<AvailHashEntry> iterator) throws SqlQueriesException {
        /**
         * this shovels all hashes into a temporary table and joins it with the fs table.
         */
        MissingHash m = new MissingHash();
        TransferDetails t = new TransferDetails();
        FsFile f = new FsFile();
        sqlQueries.delete(m, null, null);
        N.forEach(iterator, availHashEntry -> {
            this.insertHashTmp(availHashEntry.getHash());
        });
        List<Pair<?>> attributes = new ArrayList<>();
        attributes.add(f.getContentHash());
        String query = "select f." + f.getContentHash().k() + " from " + m.getTableName() + " m left join " + f.getTableName() + " f on m." + m.getHash().k() + "=" + f.getContentHash().k()
                + " where f." + f.getSynced().k() + "=? and f." + f.getIsDirectory().k() + "=?";
        ISQLResource<FsFile> resource = sqlQueries.loadQueryResource(query, attributes, FsFile.class, ISQLQueries.whereArgs(true, false));
        return resource;
    }

    /**
     * insert hashes to temporary table
     *
     * @param hash
     */
    private void insertHashTmp(String hash) throws SqlQueriesException {
        sqlQueries.insert(new MissingHash(hash));
    }

    public void flagNotStartedHashAvailable(String hash) throws SqlQueriesException {
        TransferDetails t = new TransferDetails();
        String stmt = "update " + t.getTableName() + " set " + t.getAvailable().k() + "=? where " + t.getStarted().k() + "=? and " + t.getHash().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(true, false, hash));
    }
}
