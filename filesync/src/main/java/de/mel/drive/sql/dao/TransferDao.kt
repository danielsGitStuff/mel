package de.mel.drive.sql.dao

import de.mel.auth.data.db.MissingHash
import de.mel.auth.tools.N
import de.mel.drive.sql.*
import de.mel.drive.tasks.AvailHashEntry
import de.mel.sql.*
import java.util.*

/**
 * Created by xor on 12/16/16.
 */
class TransferDao : Dao {

    companion object {
        /**
         * this method allows us to remove the dependency to sqlite.
         * This saves more than a megabyte on Android.
         */
        private fun violatesUnique(e: Exception): Boolean {
            if (e.javaClass.name == "org.sqlite.SQLiteException") {
                return e.message?.contains("(UNIQUE constraint") ?: false
            }
            return false
        }
    }

    private val dummy = DbTransferDetails()

    val oneTransfer: DbTransferDetails?
        @Throws(SqlQueriesException::class)
        get() {

            val res = sqlQueries.load(dummy.allAttributes, dummy, null, null, " order by " + dummy.id.k() + " limit 1")
            return if (res.size == 1) res[0] else null
        }

    val twoTransferSets: MutableList<DbTransferDetails>
        @Throws(SqlQueriesException::class)
        get() = getTransferSets(2)

    val transferResource: SQLResource<DbTransferDetails>?
        get() = null

    val unnecessaryTransfers: ISQLResource<DbTransferDetails>
        @Throws(SqlQueriesException::class)
        get() {
            val f = FsFile()
            val query = ("select * from " + dummy.tableName + " t where not exists (select * from " + f.tableName
                    + " f where f." + f.contentHash.k() + "=t." + dummy.hash.k() + " and " + f.synced.k() + "=?)")
            return sqlQueries.loadQueryResource(query, dummy.allAttributes, DbTransferDetails::class.java, ISQLQueries.args(false))
        }

    constructor(ISQLQueries: ISQLQueries) : super(ISQLQueries) {}

    constructor(ISQLQueries: ISQLQueries, lock: Boolean) : super(ISQLQueries, lock) {}

    @Throws(SqlQueriesException::class)
    fun getLeftoversByService(certId: Long?, serviceUuid: String): TransferLeftovers? {
        val lo = TransferLeftovers()
//        val query = ("select (select count(1) from " + dummy.tableName + ") as " + lo.filesTotal.k()
//                + ", (select count(1) from " + dummy.tableName + " where " + dummy.certId.k() + "=? and " + dummy.serviceUuid.k() + "=? and " + dummy.state.k() + "=? ) as " + lo.filesTransferred.k()
//                + ", select( sum(" + dummy.size.k() + ") as " + lo.bytesTotal.k()
//                + ", (select sum(" + dummy.transferred.k() + ") from " + dummy.tableName + " where " + dummy.certId.k() + "=? and " + dummy.serviceUuid.k() + "=? and " + dummy.state.k() + " != ?) as " + lo.bytesTransferred.k()
//                + " from " + dummy.tableName + " where " + dummy.certId.k() + "=? and " + dummy.serviceUuid.k() + "=? and " + dummy.state.k() + " !=?")

        val query = "select count(1) as ${lo.filesTotal.k()}" +
                ", (select count(1) from ${dummy.tableName} where ${dummy.certId.k()}=? and ${dummy.serviceUuid.k()}=? and ${dummy.state.k()}=?) as ${lo.filesTransferred.k()}" +
                ", sum(${dummy.size.k()}) as ${lo.bytesTotal.k()}" +
                ", sum(${dummy.transferred.k()}) as ${lo.bytesTransferred.k()}" +
                " from ${dummy.tableName} where ${dummy.certId.k()}=? and ${dummy.serviceUuid.k()}=?"
        val list = sqlQueries.loadString(lo.allAttributes, lo, query, ISQLQueries.args(certId, serviceUuid, TransferState.DONE, certId, serviceUuid))
        if (!list.isEmpty()) {
            // summed bytes are null if no transfer exists
            val result = list[0]
            if (result.bytesTransferred.equalsValue(null))
                result.bytesTransferred.v(0L)
            if (result.bytesTotal.equalsValue(null))
                result.bytesTotal.v(0L)
            return result
        }
        return null
    }

    @Throws(SqlQueriesException::class)
    fun insert(dbTransferDetails: DbTransferDetails) {
        try {
            val id = sqlQueries.insert(dbTransferDetails)
            dbTransferDetails.id.v(id)
        } catch (sqlQueriesException: SqlQueriesException) {
            // non unique transfers happen quite often and thats normal. don't fill the logs with that.
            if (!violatesUnique(sqlQueriesException.exception))
                throw sqlQueriesException
        }
    }

    @Throws(SqlQueriesException::class)
    fun delete(id: Long?) {

        sqlQueries.delete(dummy, dummy.id.k() + "=?", ISQLQueries.args(id))
    }

    /**
     * @param limit
     * @return all TransferSets if limit is null
     */
    @Throws(SqlQueriesException::class)
    fun getTransferSets(limit: Int?): MutableList<DbTransferDetails> {
        val where = dummy.state.k() + "=?"
        var whatElse = "group by " + dummy.certId.k() + "," + dummy.serviceUuid.k()
        if (limit != null)
            whatElse += " limit $limit"
        return sqlQueries.load(ISQLQueries.columns(dummy.certId, dummy.serviceUuid), dummy, where, ISQLQueries.args(TransferState.NOT_STARTED), whatElse)
    }


    @Throws(SqlQueriesException::class)
    fun getNotStartedTransfers(certId: Long?, serviceUuid: String, limit: Int): MutableList<DbTransferDetails> {
        val where = dummy.certId.k() + "=? and " + dummy.serviceUuid.k() + "=? and " + dummy.state.k() + "=? and " + dummy.deleted.k() + "=? and " + dummy.available.k() + "=? "
        val whatElse = " limit ?"
        return sqlQueries.load(dummy.allAttributes, dummy, where, ISQLQueries.args(certId, serviceUuid, TransferState.NOT_STARTED, false, true, limit), whatElse)
    }

    @Throws(SqlQueriesException::class)
    fun deleteByHash(hash: String?) {
        if (hash == null)
            return
        sqlQueries.delete(dummy, dummy.hash.k() + "=?", ISQLQueries.args(hash))
    }

    @Throws(SqlQueriesException::class)
    fun updateState(id: Long?, state: TransferState) {
        val stmt = "update " + dummy.tableName + " set " + dummy.state.k() + "=? where " + dummy.id.k() + "=?"
        sqlQueries.execute(stmt, ISQLQueries.args(state, id))
    }

    //    private void setStarted(Long id, boolean started) throws SqlQueriesException {
    //        TransferDetails details = new TransferDetails();
    //        String stmt = "update " + details.getTableName() + " set " + details.getState().k() + "=? where " + details.getId().k() + "=?";
    //        sqlQueries.execute(stmt, ISQLQueries.whereArgs(true, id));
    //    }

    @Throws(SqlQueriesException::class)
    fun hasNotStartedTransfers(certId: Long?, serviceUuid: String): Boolean {
        val query = "select count(*)>0 from " + dummy.tableName + " where " + dummy.certId.k() + "=? and " + dummy.serviceUuid.k() + "=?"
        val result = sqlQueries.queryValue(query, Int::class.java, ISQLQueries.args(certId, serviceUuid))
        return SqliteConverter.intToBoolean(result)!!
    }

    @Throws(SqlQueriesException::class)
    fun resetStarted() {
        val stmt = "update " + dummy.tableName + " set " + dummy.state.k() + "=?"
        sqlQueries.execute(stmt, ISQLQueries.args(TransferState.NOT_STARTED))
    }

    @Throws(SqlQueriesException::class)
    fun count(certId: Long?, serviceUuid: String): Long {
        val query = "select count (*) from " + dummy.tableName + " where " + dummy.certId.k() + "=? and " + dummy.serviceUuid.k() + "=?"
        return sqlQueries.queryValue(query, Long::class.java, ISQLQueries.args(certId, serviceUuid))
    }

    @Throws(SqlQueriesException::class)
    fun countRemaining(certId: Long?, serviceUuid: String): Long {
        val query = "select count (*) from ${dummy.tableName} where ${dummy.certId.k()}=? and ${dummy.serviceUuid.k()}=? " +
                "and ${dummy.state.k()}=?"
        return sqlQueries.queryValue(query, Long::class.java, ISQLQueries.args(certId, serviceUuid, TransferState.NOT_STARTED))
    }

    @Throws(SqlQueriesException::class)
    fun countStarted(certId: Long?, serviceUuid: String): Int {
        val query = ("select count (*) from " + dummy.tableName + " where " + dummy.state.k() + "=? and "
                + dummy.certId.k() + "=? and "
                + dummy.serviceUuid.k() + "=?")
        return sqlQueries.queryValue(query, Int::class.java, ISQLQueries.args(true, certId, serviceUuid))
    }

    @Throws(SqlQueriesException::class)
    fun updateTransferredBytes(id: Long?, transferred: Long?) {
        val stmt = "update " + dummy.tableName + " set " + dummy.transferred.k() + "=? where " + dummy.id.k() + "=?"
        sqlQueries.execute(stmt, ISQLQueries.args(transferred, id))
    }

    @Throws(SqlQueriesException::class)
    fun flagForDeletion(transferId: Long?, flag: Boolean) {
        val stmt = "update " + dummy.tableName + " set " + dummy.deleted.k() + "=? where " + dummy.id.k() + "=?"
        sqlQueries.execute(stmt, ISQLQueries.args(flag, transferId))
    }

    @Throws(SqlQueriesException::class)
    fun updateAvailableByHashSet(certId: Long?, serviceUUID: String, hashes: Collection<String>) {
        var statement = ("update " + dummy.tableName + " set " + dummy.available.k() + "=? where " + dummy.certId.k() + "=? and " + dummy.serviceUuid.k() + "=? and "
                + dummy.hash.k() + " in ")
        statement += ISQLQueries.buildPartIn(hashes)
        val whereArgs = ISQLQueries.args(true, certId, serviceUUID)
        whereArgs.addAll(hashes)
        sqlQueries.execute(statement, whereArgs)
    }

    @Throws(SqlQueriesException::class)
    fun getNotStartedNotAvailableTransfers(certId: Long?): ISQLResource<DbTransferDetails> {
        val f = FsFile()
        val attributes = ArrayList<Pair<*>>()
        attributes.add(dummy.hash)
        val query = "select t." + dummy.hash.k() + " from " + dummy.tableName + " t left join " + f.tableName + " f on f." + f.contentHash.k() + " = t." + dummy.hash.k() + " " +
                "where t." + dummy.state.k() + "=? and t." + dummy.available.k() + "=? and t." + dummy.certId.k() + "=? group by t." + dummy.hash.k()
        return sqlQueries.loadQueryResource(query, attributes, DbTransferDetails::class.java, ISQLQueries.args(false, false, certId))
    }


    @Synchronized
    @Throws(SqlQueriesException::class)
    fun getAvailableTransfersFromHashes(iterator: Iterator<AvailHashEntry>): ISQLResource<FsFile> {
        /**
         * this shovels all hashes into a temporary table and joins it with the fs table.
         */
        val m = MissingHash()
        val f = FsFile()
        sqlQueries.delete(m, null, null)
        N.forEach(iterator) { availHashEntry -> this.insertHashTmp(availHashEntry.hash) }
        val attributes = ArrayList<Pair<*>>()
        attributes.add(f.contentHash)
        val query = ("select f." + f.contentHash.k() + " from " + m.tableName + " m left join " + f.tableName + " f on m." + m.hash.k() + "=" + f.contentHash.k()
                + " where f." + f.synced.k() + "=? and f." + f.isDirectory.k() + "=?")
        return sqlQueries.loadQueryResource(query, attributes, FsFile::class.java, ISQLQueries.args(true, false))
    }

    /**
     * insert hashes to temporary table
     *
     * @param hash
     */
    @Throws(SqlQueriesException::class)
    private fun insertHashTmp(hash: String) {
        sqlQueries.insert(MissingHash(hash))
    }

    @Throws(SqlQueriesException::class)
    fun flagNotStartedHashAvailable(hash: String) {
        val stmt = "update " + dummy.tableName + " set " + dummy.available.k() + "=? where " + dummy.state.k() + "=? and " + dummy.hash.k() + "=?"
        sqlQueries.execute(stmt, ISQLQueries.args(true, false, hash))
    }

    @Throws(SqlQueriesException::class)
    fun updateStateByHash(hash: String, state: TransferState) {
        val stmt = ("update " + dummy.tableName
                + " set " + dummy.state.k() + "=? where " + dummy.hash.k() + "=?")
        sqlQueries.execute(stmt, ISQLQueries.args(state, hash))
    }

    //    public void deleteDone(@NotNull Long partnerCertificateId, @NotNull String partnerServiceUuid) throws SqlQueriesException {
    //        String stmt = "delete from " + dummy.getTableName() + " where "
    //                + dummy.getCertId().k() + "=? and "
    //                + dummy.getServiceUuid().k() + "=? and "
    //                + dummy.getState().k() + "=?";
    //        sqlQueries.execute(stmt, ISQLQueries.whereArgs(partnerCertificateId, partnerServiceUuid, TransferState.DONE));
    //    }

    @Throws(SqlQueriesException::class)
    fun flagStateForRemainingTransfers(certId: Long, serviceuuid: String, state: TransferState) {
        val stmt = ("update " + dummy.tableName + " set " + dummy.state.k() + "=?"
                + " where " + dummy.certId.k() + "=? and " + dummy.serviceUuid.k() + "=? and " + dummy.state.k() + "!=? and " + dummy.state.k() + "!=?")
        sqlQueries.execute(stmt, ISQLQueries.args(state, certId, serviceuuid, TransferState.DONE, TransferState.RUNNING))
    }


    @Throws(SqlQueriesException::class)
    fun hasHash(hash: String): Boolean {
        val query = "select count(1) from " + dummy.tableName + " where " + dummy.hash.k() + "=?"
        val count = sqlQueries.queryValue(query, Long::class.java, ISQLQueries.args(hash))
        return count > 0L
    }

    @Throws(SqlQueriesException::class)
    fun deleteFlaggedForDeletion() {
        val stmt = "delete from " + dummy.tableName + " where " + dummy.deleted.k() + "=?"
        sqlQueries.execute(stmt, ISQLQueries.args(true))
    }

    fun flagForDeletionByHash(hash: String) {
        val stmt = "update ${dummy.tableName} set ${dummy.deleted.k()}=?, ${dummy.state.k()}=? where ${dummy.hash.k()}=?"
        sqlQueries.execute(stmt, ISQLQueries.args(true, TransferState.DONE, hash))
    }
}
