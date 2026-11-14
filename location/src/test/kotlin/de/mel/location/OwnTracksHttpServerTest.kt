package de.mel.location

import de.mel.location.data.LocationSettings
import de.mel.location.data.db.LocationDao
import de.mel.location.data.db.LocationPoint
import de.mel.location.http.OwnTracksHttpServer
import de.mel.sql.ISQLQueries
import de.mel.sql.ISQLResource
import de.mel.sql.Pair
import de.mel.sql.SQLTableObject
import de.mel.sql.conn.SQLConnection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

class OwnTracksHttpServerTest {

    @Test
    fun httpEndpointAuthAndOwnTracksParsingAndDedup() {
        val dao = LocationDao(InMemoryLocationSqlQueries())
        val settings = LocationSettings().apply {
            port = 0
            bindAddress = "127.0.0.1"
            username = "ot"
            password = "pw"
        }
        val server = OwnTracksHttpServer(settings) { pt -> dao.insertIfAbsent(pt) }
        server.start()
        try {
            val url = URL("http://127.0.0.1:${server.actualPort()}/pub")
            val locJson = """{"_type":"location","tid":"ph","tst":1700000100,"lat":52.52,"lon":13.405,"alt":35,"acc":12,"vel":3,"batt":88}"""

            // 1. Missing auth -> 401
            val unauth = postJson(url, locJson, null)
            assertEquals(401, unauth.first)

            // 2. Valid auth -> 200 []
            val authHeader = "Basic " + Base64.getEncoder().encodeToString("ot:pw".toByteArray(StandardCharsets.UTF_8))
            val ok = postJson(url, locJson, authHeader)
            assertEquals(200, ok.first)
            assertEquals("[]", ok.second)

            // 3. Duplicate retry -> still 200 [], but only 1 row stored
            val retry = postJson(url, locJson, authHeader)
            assertEquals(200, retry.first)
            val stored = dao.loadRecent()
            assertEquals(1, stored.size)
            assertEquals("ph", stored[0].tid.v())
            assertEquals(52.52, stored[0].lat.v(), 0.0001)
            assertEquals(13.405, stored[0].lon.v(), 0.0001)

            // 4. Non-location _type -> 200 [], not stored
            val transition = postJson(url, """{"_type":"transition","event":"enter","tst":1700000200}""", authHeader)
            assertEquals(200, transition.first)
            assertEquals(1, dao.loadRecent().size)
        } finally {
            server.stop()
        }
    }

    private fun postJson(url: URL, body: String, auth: String?): kotlin.Pair<Int, String> {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        if (auth != null) conn.setRequestProperty("Authorization", auth)
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.readText().orEmpty()
        return kotlin.Pair(code, resp)
    }

    private class InMemoryLocationSqlQueries : ISQLQueries() {
        private val rows = mutableListOf<LocationPoint>()
        private var nextId = 1L

        override fun insert(sqlTableObject: SQLTableObject): Long {
            val row = sqlTableObject as LocationPoint
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
                where != null && where.contains("tid = ? AND tst = ?") -> {
                    val tid = args?.get(0) as String
                    val tst = (args[1] as Number).toLong()
                    rows.filter { it.tid.v() == tid && it.tst.v() == tst }
                }
                else -> rows.sortedByDescending { it.tst.v() }
            }
            return filtered.toMutableList() as MutableList<T>
        }

        override fun <T : SQLTableObject> load(columns: MutableList<Pair<*>>, tableObject: T, where: String?, args: MutableList<Any>?, orderBy: String?): MutableList<T> =
            load(columns, tableObject, where, args)

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
