package de.mel.sql

import de.mel.sql.SqlQueriesException
import java.util.*

/**
 * Created by xor on 2/6/17.
 */
interface ISQLResource<T : SQLTableObject?> : AutoCloseable {
    @get:Throws(SqlQueriesException::class)
    val next: T?

    fun next() = next

    @Throws(SqlQueriesException::class)
    override fun close()

    @get:Throws(SqlQueriesException::class)
    val isClosed: Boolean

    @Throws(SqlQueriesException::class)
    fun toList(): List<T> {
        val list: MutableList<T> = ArrayList()
        var item: T? = next
        while (item != null) {
            list.add(item)
            item = next
        }
        return list
    }
}