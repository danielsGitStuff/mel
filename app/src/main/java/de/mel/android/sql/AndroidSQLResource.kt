package de.mel.android.sql

import android.database.sqlite.SQLiteCursor
import de.mel.sql.ISQLResource
import de.mel.sql.SQLTableObject
import de.mel.sql.SqlQueriesException

/**
 * Created by xor on 3/22/17.
 */
class AndroidSQLResource<T : SQLTableObject?>(private val cursor: SQLiteCursor, private val clazz: Class<T>) : ISQLResource<T> {
    private var countdown = 500
    private var count = 0
    @get:Throws(SqlQueriesException::class)
    override val next: T?
        get() {
            var sqlTable: T? = null
            try {
                count++
                if (cursor.window != null) {
                    countdown--
                    cursor.window.freeLastRow()
                    if (countdown == 0) {
                        cursor.window.clear()
                        countdown = 500
                    }
                }
                if (cursor.moveToNext()) {
                    sqlTable = clazz.newInstance()
                    val attributes = sqlTable!!.allAttributes
                    for (pair in attributes) {
                        try {
                            AndroidSQLQueries.readCursorToPair(cursor, pair)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                throw SqlQueriesException(e)
            }
            return sqlTable
        }

    @Throws(SqlQueriesException::class)
    override fun close() {
        cursor.close()
    }

    @get:Throws(SqlQueriesException::class)
    override val isClosed: Boolean
        get() = cursor.isClosed

}