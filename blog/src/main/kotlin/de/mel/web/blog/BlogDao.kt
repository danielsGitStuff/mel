package de.mel.web.blog

import de.mel.sql.*
import java.time.LocalDateTime
import java.time.ZoneOffset


class BlogDao(sqlQueries: SQLQueries) : Dao(sqlQueries) {
    private val dummy = BlogEntry()
    fun insert(blogEntry: BlogEntry) {
        val id = sqlQueries.insert(blogEntry)
        blogEntry.timestamp.v(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
        blogEntry.id.v(id)
    }

    fun pubGetById(id: Long): BlogEntry? {
        val where = "${dummy.id.k()}=? and ${dummy.published.k()}=?"
        return sqlQueries.loadFirstRow(dummy.allAttributes, dummy, where, ISQLQueries.args(id, true), BlogEntry::class.java)
    }

    fun pubGetLastNentries(n: Int): MutableList<BlogEntry> {
        val ldt = LocalDateTime.now()
        val where = "${dummy.published.k()}=?"
        val whatElse = "order by ${dummy.timestamp.k()} desc limit ?"
        return sqlQueries.load(dummy.allAttributes, dummy, where, ISQLQueries.args(true, n), whatElse)
    }

    fun update(entry: BlogEntry) {
        val where = "${entry.id.k()}=?"
        sqlQueries.update(entry, where, ISQLQueries.args(entry.id.v()))
    }

    fun deleteById(id: Long) {
        val where = "${dummy.id.k()}=?"
        sqlQueries.delete(dummy, where, ISQLQueries.args(id))
    }

    fun pubGetByDateRange(start: Long, end: Long): List<BlogEntry> {
        val where = "${dummy.timestamp.k()}>? and ${dummy.timestamp.k()}<=? and ${dummy.published.k()}=? order by ${dummy.timestamp.k()} desc"
        return sqlQueries.load(dummy.allAttributes, dummy, where, ISQLQueries.args(start, end, true))
    }

    fun getById(id: Long): BlogEntry? {
        val where = "${dummy.id.k()}=?"
        return sqlQueries.loadFirstRow(dummy.allAttributes, dummy, where, ISQLQueries.args(id), BlogEntry::class.java)
    }

}