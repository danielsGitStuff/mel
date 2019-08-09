package de.mein.drive.sql.dao;

import de.mein.drive.sql.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import de.mein.auth.data.db.MissingHash;
import de.mein.auth.tools.N;
import de.mein.drive.tasks.AvailHashEntry;
import de.mein.sql.*;

import org.sqlite.core.DB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Created by xor on 12/16/16.
 */
public class TransferDao extends Dao {
    private DbTransferDetails dummy = new DbTransferDetails();

    public TransferDao(ISQLQueries ISQLQueries) {
        super(ISQLQueries);
    }

    public TransferDao(ISQLQueries ISQLQueries, boolean lock) {
        super(ISQLQueries, lock);
    }

    public TransferLeftovers getLeftoversByService(Long partnerCertId, String partnerServiceUuid) throws SqlQueriesException {
        TransferLeftovers lo = new TransferLeftovers();
        String query = "select count(1) as " + lo.getFilesTotal().k()
                + ", (select count(1) from " + dummy.getTableName() + " where " + dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=? and " + dummy.getState().k() + "=? ) as " + lo.getFilesTransferred().k()
                + ", sum(" + dummy.getSize().k() + ") as " + lo.getBytesTotal().k()
                + ", (select sum(" + dummy.getTransferred().k() + ") from " + dummy.getTableName() + " where " + dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=? and " + dummy.getState().k() + " != ?) as " + lo.getBytesTransferred().k()
                + " from " + dummy.getTableName() + " where " + dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=? and " + dummy.getState().k() + " !=?";
        List<TransferLeftovers> list = sqlQueries.loadString(lo.getAllAttributes(), lo, query, ISQLQueries.whereArgs(partnerCertId, partnerServiceUuid, TransferState.DONE,
                partnerCertId, partnerServiceUuid, TransferState.SUSPENDED,
                partnerCertId, partnerServiceUuid, TransferState.SUSPENDED));
        if (!list.isEmpty()) {
            // summed bytes are null if no transfer exists
            TransferLeftovers result = list.get(0);
            if (result.getBytesTransferred().equalsValue(null))
                result.getBytesTransferred().v(0L);
            if (result.getBytesTotal().equalsValue(null))
                result.getBytesTotal().v(0L);
            return result;
        }
        return null;
    }

    public void insert(DbTransferDetails dbTransferDetails) throws SqlQueriesException {
        try {
            Long id = sqlQueries.insert(dbTransferDetails);
            dbTransferDetails.getId().v(id);
        } catch (SqlQueriesException sqlQueriesException) {
            // non unique transfers happen quite often and thats normal. don't fill the logs with that.
            if (sqlQueriesException.getException() instanceof SQLiteException) {
                SQLiteException sqLiteException = (SQLiteException) sqlQueriesException.getException();
                if (sqLiteException.getResultCode() != SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE)
                    throw sqlQueriesException;

            }
        }

    }

    public DbTransferDetails getOneTransfer() throws SqlQueriesException {

        List<DbTransferDetails> res = sqlQueries.load(dummy.getAllAttributes(), dummy, null, null, " order by " + dummy.getId().k() + " limit 1");
        if (res.size() == 1)
            return res.get(0);
        return null;
    }

    public void delete(Long id) throws SqlQueriesException {

        sqlQueries.delete(dummy, dummy.getId().k() + "=?", ISQLQueries.whereArgs(id));
    }

    public List<DbTransferDetails> getTwoTransferSets() throws SqlQueriesException {
        return getTransferSets(2);
    }

    /**
     * @param limit
     * @return all TransferSets if limit is null
     */
    public List<DbTransferDetails> getTransferSets(Integer limit) throws SqlQueriesException {
        String where = dummy.getState().k() + "=?";
        String whatElse = "group by " + dummy.getCertId().k() + "," + dummy.getServiceUuid().k();
        if (limit != null)
            whatElse += " limit " + limit;
        List<DbTransferDetails> result = sqlQueries.load(ISQLQueries.columns(dummy.getCertId(), dummy.getServiceUuid()), dummy, where, ISQLQueries.whereArgs(TransferState.NOT_STARTED), whatElse);
        return result;
    }

    public SQLResource<DbTransferDetails> getTransferResource() {
        return null;
    }


    public List<DbTransferDetails> getNotStartedTransfers(Long certId, String serviceUuid, int limit) throws SqlQueriesException {
        String where = dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=? and " + dummy.getState().k() + "=? and " + dummy.getDeleted().k() + "=? and " + dummy.getAvailable().k() + "=? ";
        String whatElse = " limit ?";
        List<DbTransferDetails> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(certId, serviceUuid, TransferState.NOT_STARTED, false, true, limit), whatElse);
        return result;
    }

    public void deleteByHash(String hash) throws SqlQueriesException {
        if (hash == null)
            return;
        sqlQueries.delete(dummy, dummy.getHash().k() + "=?", ISQLQueries.whereArgs(hash));
    }

    public void updateState(Long id, TransferState state) throws SqlQueriesException {
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getState().k() + "=? where " + dummy.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(state, id));
    }

//    private void setStarted(Long id, boolean started) throws SqlQueriesException {
//        TransferDetails details = new TransferDetails();
//        String stmt = "update " + details.getTableName() + " set " + details.getState().k() + "=? where " + details.getId().k() + "=?";
//        sqlQueries.execute(stmt, ISQLQueries.whereArgs(true, id));
//    }

    public boolean hasNotStartedTransfers(Long certId, String serviceUuid) throws SqlQueriesException {
        String query = "select count(*)>0 from " + dummy.getTableName() + " where " + dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=?";
        Integer result = sqlQueries.queryValue(query, Integer.class, ISQLQueries.whereArgs(certId, serviceUuid));
        return SqliteConverter.intToBoolean(result);
    }

    public void resetStarted() throws SqlQueriesException {
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getState().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(TransferState.NOT_STARTED));
    }

    public Long count(Long certId, String serviceUuid) throws SqlQueriesException {
        String query = "select count (*) from " + dummy.getTableName() + " where " + dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=?";
        return sqlQueries.queryValue(query, Long.class, ISQLQueries.whereArgs(certId, serviceUuid));
    }

    public Long countDone(Long certId, String serviceUuid) throws SqlQueriesException {
        String query = "select count (*) from " + dummy.getTableName()
                + " where " + dummy.getCertId().k() + "=? and "
                + dummy.getServiceUuid().k() + "=? and "
                + dummy.getState().k() + "=?";
        return sqlQueries.queryValue(query, Long.class, ISQLQueries.whereArgs(certId, serviceUuid, TransferState.DONE));
    }

    public int countStarted(Long certId, String serviceUuid) throws SqlQueriesException {
        String query = "select count (*) from " + dummy.getTableName() + " where " + dummy.getState().k() + "=? and "
                + dummy.getCertId().k() + "=? and "
                + dummy.getServiceUuid().k() + "=?";
        return sqlQueries.queryValue(query, Integer.class, ISQLQueries.whereArgs(true, certId, serviceUuid));
    }

    public void updateTransferredBytes(Long id, Long transferred) throws SqlQueriesException {
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getTransferred().k() + "=? where " + dummy.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(transferred, id));
    }

    public ISQLResource<DbTransferDetails> getUnnecessaryTransfers() throws SqlQueriesException {
        FsFile f = new FsFile();
        String query = "select * from " + dummy.getTableName() + " t where not exists (select * from " + f.getTableName()
                + " f where f." + f.getContentHash().k() + "=t." + dummy.getHash().k() + " and " + f.getSynced().k() + "=?)";
        return sqlQueries.loadQueryResource(query, dummy.getAllAttributes(), DbTransferDetails.class, ISQLQueries.whereArgs(false));
    }

    public void flagDeleted(Long transferId, boolean flag) throws SqlQueriesException {
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getDeleted().k() + "=? where " + dummy.getId().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(flag, transferId));
    }

    public void updateAvailableByHashSet(Long certId, String serviceUUID, Collection<String> hashes) throws SqlQueriesException {
        String statement = "update " + dummy.getTableName() + " set " + dummy.getAvailable().k() + "=? where " + dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=? and "
                + dummy.getHash().k() + " in ";
        statement += ISQLQueries.buildPartIn(hashes);
        List<Object> whereArgs = ISQLQueries.whereArgs(true, certId, serviceUUID);
        whereArgs.addAll(hashes);
        sqlQueries.execute(statement, whereArgs);
    }

    public ISQLResource<DbTransferDetails> getNotStartedNotAvailableTransfers(Long certId) throws SqlQueriesException {
        FsFile f = new FsFile();
        List<Pair<?>> attributes = new ArrayList<>();
        attributes.add(dummy.getHash());
        String query = "select t." + dummy.getHash().k() + " from " + dummy.getTableName() + " t left join " + f.getTableName() + " f on f." + f.getContentHash().k() + " = t." + dummy.getHash().k() + " " +
                "where t." + dummy.getState().k() + "=? and t." + dummy.getAvailable().k() + "=? and t." + dummy.getCertId().k() + "=? group by t." + dummy.getHash().k();
        ISQLResource<DbTransferDetails> result = sqlQueries.loadQueryResource(query, attributes, DbTransferDetails.class, ISQLQueries.whereArgs(false, false, certId));
        return result;
    }


    public synchronized ISQLResource<FsFile> getAvailableTransfersFromHashes(Iterator<AvailHashEntry> iterator) throws SqlQueriesException {
        /**
         * this shovels all hashes into a temporary table and joins it with the fs table.
         */
        MissingHash m = new MissingHash();
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
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getAvailable().k() + "=? where " + dummy.getState().k() + "=? and " + dummy.getHash().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(true, false, hash));
    }

    public void updateStateByHash(String hash, TransferState state) throws SqlQueriesException {
        String stmt = "update " + dummy.getTableName()
                + " set " + dummy.getState().k() + "=? where " + dummy.getHash().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(state, hash));
    }

    public void deleteDone(@NotNull Long partnerCertificateId, @NotNull String partnerServiceUuid) throws SqlQueriesException {
        String stmt = "delete from " + dummy.getTableName() + " where "
                + dummy.getCertId().k() + "=? and "
                + dummy.getServiceUuid().k() + "=? and "
                + dummy.getState().k() + "=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(partnerCertificateId, partnerServiceUuid, TransferState.DONE));
    }

    public void flagStateForRemainingTransfers(long certId, @NotNull String serviceuuid, TransferState state) throws SqlQueriesException {
        String stmt = "update " + dummy.getTableName() + " set " + dummy.getState().k() + "=?"
                + " where " + dummy.getCertId().k() + "=? and " + dummy.getServiceUuid().k() + "=? and " + dummy.getState().k() + "!=? and " + dummy.getState().k() + "!=?";
        sqlQueries.execute(stmt, ISQLQueries.whereArgs(state, certId, serviceuuid, TransferState.DONE, TransferState.RUNNING));
    }


    public boolean hasHash(String hash) throws SqlQueriesException {
        String query = "select count(1) from " + dummy.getTableName() + " where " + dummy.getHash().k() + "=?";
        Long count = sqlQueries.queryValue(query, Long.class, ISQLQueries.whereArgs(hash));
        return count > 0;
    }
}
