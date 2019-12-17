package de.mel.sql

import de.mel.sql.SqlQueriesException
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Created by xor on 12/16/16.
 */
class SQLResource<T : SQLTableObject?>(private val preparedStatement: PreparedStatement, private val clazz: Class<T>, columns: List<Pair<*>>) : ISQLResource<T> {
    private val columns: List<Pair<*>> = columns
    private val resultSet: ResultSet = preparedStatement.resultSet

    @get:Throws(SqlQueriesException::class)
    override val next: T?
        get() {
            var sqlTable: T? = null
            try {
                if (resultSet.next()) {
                    sqlTable = clazz.newInstance()
                    for (pair in columns) {
                        try {
                            val res = resultSet.getObject(pair.k())
                            sqlTable!!.getPair(pair.k()).setValueUnsecure(res)
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
        try {
            resultSet.close()
            preparedStatement.close()
        } catch (e: SQLException) {
            e.printStackTrace()
            throw SqlQueriesException(e)
        }
    }

    @get:Throws(SqlQueriesException::class)
    override val isClosed: Boolean
        get() = try {
            resultSet.isClosed
        } catch (e: SQLException) {
            e.printStackTrace()
            throw SqlQueriesException(e)
        }

}