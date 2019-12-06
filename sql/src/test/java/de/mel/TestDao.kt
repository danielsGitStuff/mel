package de.mel

import de.mel.sql.*
import de.mel.sql.conn.SQLConnector
import de.mel.sql.transform.SqlResultTransformer
import java.io.File

abstract class TestDao<T : SQLTableObject>( sqlQueries: ISQLQueries) : Dao(sqlQueries) {
    val nameMap = mutableMapOf<String, T>()

    fun insert(obj: T): TestDao<T> {
        sqlQueries.insert(obj)
        nameMap[createName(obj)] = obj
        return this
    }

    abstract fun createName(obj: T): String

    operator fun get(name: String) = nameMap[name]

    companion object {
        fun createSqlQueries(dbFile: File): SQLQueries {
            return SQLQueries(SQLConnector.createSqliteConnection(dbFile), true, RWLock(), SqlResultTransformer.sqliteResultSetTransformer())
        }
    }
}