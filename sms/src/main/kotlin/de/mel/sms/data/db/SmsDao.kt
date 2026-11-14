package de.mel.sms.data.db

import de.mel.sms.data.EncryptedSmsRecord
import de.mel.sql.Dao
import de.mel.sql.ISQLQueries
import java.sql.SQLException

class SmsDao(sqlQueries: ISQLQueries) : Dao(sqlQueries) {

    /** Inserts if msg_hash is new; returns true if a row was inserted. */
    @Throws(SQLException::class)
    fun insertIfAbsent(rec: EncryptedSmsRecord): Boolean {
        val proto = SmsRecordRow()
        val existing = sqlQueries.load(
            proto.allAttributes,
            proto,
            "${proto.msgHash.k()} = ?",
            ISQLQueries.args(rec.msgHash)
        )
        if (existing.isNotEmpty()) return false
        val row = SmsRecordRow()
        row.msgHash.v(rec.msgHash)
        row.iv.v(rec.iv)
        row.ciphertext.v(rec.ciphertext)
        row.createdAt.v(rec.createdAt)
        sqlQueries.insert(row)
        return true
    }

    fun loadSince(sinceId: Long, limit: Int = 500): List<EncryptedSmsRecord> {
        val proto = SmsRecordRow()
        val rows = sqlQueries.load(
            proto.allAttributes,
            proto,
            "${proto.id.k()} > ? ORDER BY ${proto.id.k()} ASC LIMIT ?",
            ISQLQueries.args(sinceId, limit)
        )
        return rows.map { row ->
            EncryptedSmsRecord(
                row.id.v(),
                row.msgHash.v(),
                row.iv.v(),
                row.ciphertext.v(),
                row.createdAt.v()
            )
        }
    }

    fun loadAll(): List<EncryptedSmsRecord> {
        val proto = SmsRecordRow()
        val rows = sqlQueries.load(
            proto.allAttributes,
            proto,
            "1=1 ORDER BY ${proto.id.k()} ASC",
            null
        )
        return rows.map { row ->
            EncryptedSmsRecord(
                row.id.v(),
                row.msgHash.v(),
                row.iv.v(),
                row.ciphertext.v(),
                row.createdAt.v()
            )
        }
    }
}
