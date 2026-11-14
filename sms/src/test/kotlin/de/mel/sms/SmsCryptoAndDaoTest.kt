package de.mel.sms

import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer
import de.mel.sms.crypto.SmsCrypto
import de.mel.sms.data.EncryptedSmsRecord
import de.mel.sms.data.SmsPlaintext
import de.mel.sms.data.SmsSettings
import de.mel.sms.data.db.SmsDao
import de.mel.sms.data.db.SmsRecordRow
import de.mel.sql.ISQLQueries
import de.mel.sql.ISQLResource
import de.mel.sql.Pair
import de.mel.sql.SQLTableObject
import de.mel.sql.conn.SQLConnection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.crypto.AEADBadTagException

class SmsCryptoAndDaoTest {

    @Test
    fun encryptDecryptRoundTripAndWrongKeyFails() {
        val key = SmsCrypto.resolveKey("my-passphrase")
        val msg = SmsPlaintext("+49123456", 1700000000000L, 1, "Hello secret SMS", 42L)
        val json = SerializableEntitySerializer.serialize(msg)

        val (iv, cipher) = SmsCrypto.encrypt(key, json)
        val decryptedJson = SmsCrypto.decrypt(key, iv, cipher)
        val decoded = SerializableEntityDeserializer.deserialize(decryptedJson) as SmsPlaintext

        assertEquals(msg.address, decoded.address)
        assertEquals(msg.dateMs, decoded.dateMs)
        assertEquals(msg.type, decoded.type)
        assertEquals(msg.body, decoded.body)
        assertEquals(msg.threadId, decoded.threadId)

        val wrongKey = SmsCrypto.resolveKey(SmsCrypto.generateRandomSecret())
        assertThrows(AEADBadTagException::class.java) {
            SmsCrypto.decrypt(wrongKey, iv, cipher)
        }
    }

    @Test
    fun settingsSecretIsNullByDefault(@TempDir tmpDir: File) {
        val file = File(tmpDir, "sms.settings.json")
        val loaded = SmsSettings.load(file, SmsSettings())
        assertNull(loaded.secret)
        assertFalse(loaded.shareSecretWithServer)
    }

    @Test
    fun daoDeduplicatesAndPagesSinceId() {
        val dao = SmsDao(InMemorySmsSqlQueries())
        val key = SmsCrypto.resolveKey("secret")
        val m1 = SmsPlaintext("+111", 1000L, 1, "first")
        val m2 = SmsPlaintext("+222", 2000L, 2, "second")

        val h1 = SmsCrypto.hmacHash(key, m1.canonicalKey())
        val (iv1, c1) = SmsCrypto.encrypt(key, SerializableEntitySerializer.serialize(m1))
        val h2 = SmsCrypto.hmacHash(key, m2.canonicalKey())
        val (iv2, c2) = SmsCrypto.encrypt(key, SerializableEntitySerializer.serialize(m2))

        assertTrue(dao.insertIfAbsent(EncryptedSmsRecord(null, h1, iv1, c1, m1.dateMs)))
        assertFalse(dao.insertIfAbsent(EncryptedSmsRecord(null, h1, iv1, c1, m1.dateMs)), "Duplicate hash must be ignored")
        assertTrue(dao.insertIfAbsent(EncryptedSmsRecord(null, h2, iv2, c2, m2.dateMs)))

        val all = dao.loadSince(0L)
        assertEquals(2, all.size)
        val afterFirst = dao.loadSince(all[0].serverId!!)
        assertEquals(1, afterFirst.size)
        assertEquals(h2, afterFirst[0].msgHash)
    }

    private class InMemorySmsSqlQueries : ISQLQueries() {
        private val rows = mutableListOf<SmsRecordRow>()
        private var nextId = 1L

        override fun insert(sqlTableObject: SQLTableObject): Long {
            val row = sqlTableObject as SmsRecordRow
            row.id.v(nextId++)
            rows.add(row)
            return row.id.v()
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : SQLTableObject> load(
            columns: MutableList<Pair<*>>,
            tableObject: T,
            where: String?,
            args: MutableList<Any>?
        ): MutableList<T> {
            val filtered = when {
                where != null && where.contains("msg_hash = ?") -> {
                    val hash = args?.get(0) as String
                    rows.filter { it.msgHash.v() == hash }
                }
                where != null && where.contains("id > ?") -> {
                    val sinceId = (args?.get(0) as Number).toLong()
                    rows.filter { it.id.v() > sinceId }
                }
                else -> rows.toList()
            }
            return filtered.toMutableList() as MutableList<T>
        }

        override fun <T : SQLTableObject> load(
            columns: MutableList<Pair<*>>,
            tableObject: T,
            where: String?,
            args: MutableList<Any>?,
            orderBy: String?
        ): MutableList<T> = load(columns, tableObject, where, args)

        override fun getSQLConnection(): SQLConnection = throw UnsupportedOperationException()
        override fun beginTransaction() {}
        override fun commit() {}
        override fun rollback() {}
        override fun lockWrite() {}
        override fun unlockWrite() {}
        override fun lockRead() {}
        override fun unlockRead() {}
        override fun onShutDown() {}
        override fun close() {}
        override fun insertWithAttributes(sqlTableObject: SQLTableObject, attributes: MutableList<Pair<*>>) = throw UnsupportedOperationException()
        override fun update(sqlTableObject: SQLTableObject, where: String?, args: MutableList<Any>?) = throw UnsupportedOperationException()
        override fun delete(sqlTableObject: SQLTableObject, where: String?, args: MutableList<Any>?) = throw UnsupportedOperationException()
        override fun <T : SQLTableObject> loadFirstRow(columns: MutableList<Pair<*>>, tableObject: T, where: String?, args: MutableList<Any>?, clazz: Class<T>): T? = throw UnsupportedOperationException()
        override fun <T : SQLTableObject> loadResource(columns: MutableList<Pair<*>>, clazz: Class<T>, where: String?, args: MutableList<Any>?): ISQLResource<T> = throw UnsupportedOperationException()
        override fun <T : SQLTableObject> loadQueryResource(query: String, columns: MutableList<Pair<*>>, clazz: Class<T>, args: MutableList<Any>?): ISQLResource<T> = throw UnsupportedOperationException()
        override fun <T : Any?> queryValue(query: String, clazz: Class<T>, args: MutableList<Any>?): T = throw UnsupportedOperationException()
        override fun <T : Any?> queryValue(query: String, clazz: Class<T>): T = throw UnsupportedOperationException()
        override fun <T : Any?> loadColumn(column: Pair<T>, clazz: Class<T>, tableObject: SQLTableObject, join: String?, where: String?, args: MutableList<Any>?, orderBy: String?): MutableList<T> = throw UnsupportedOperationException()
        override fun <T : Any?> loadColumn(column: Pair<T>, clazz: Class<T>, where: String?, args: MutableList<Any>?): MutableList<T> = throw UnsupportedOperationException()
        override fun <T : SQLTableObject> loadString(columns: MutableList<Pair<*>>, tableObject: T, query: String, args: MutableList<Any>?): MutableList<T> = throw UnsupportedOperationException()
        override fun execute(query: String, args: MutableList<Any>?) {}
    }
}
