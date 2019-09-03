package de.miniserver.blog

import de.mein.sql.*
import java.time.LocalDateTime
import java.time.ZoneOffset

class BlogEntry : SQLTableObject() {
    val id = Pair(Long::class.java, "id")
    val title = Pair(String::class.java, "title")
    val text = Pair(String::class.java, "text")
    val timestamp = Pair(Long::class.java, "tstamp")
    val published = Pair(Boolean::class.java, "published")

    init {
        init()
    }

    val localDateTime: LocalDateTime
        get() {
            val ldt = LocalDateTime.ofEpochSecond(timestamp.v(), 0, ZoneOffset.UTC)
            return ldt
        }

    val dateString: String
        get() {
            val ldt = LocalDateTime.ofEpochSecond(timestamp.v(), 0, ZoneOffset.UTC)
            return "${ldt.year}/${ldt.month}/${ldt.dayOfMonth}"
        }

    override fun getTableName() = "blogentry"

    override fun init() {
        // set null if empty title
        title.setSetListener(IPairSetListener { str ->
            if (str != null) {
                if (str.trim().isEmpty())
                    null
                else
                    str
            } else
                null
        })
        populateInsert(title, text, timestamp, published)
        populateAll(id)
    }


}

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