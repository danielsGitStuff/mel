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

    fun getById(id: Long): BlogEntry {
        val where = "${dummy.id.k()}=?"
        return sqlQueries.loadFirstRow(dummy.allAttributes, dummy, where, ISQLQueries.args(id), BlogEntry::class.java)
    }

    fun getLastNentries(n: Int): MutableList<BlogEntry>? {
        val ldt = LocalDateTime.now()
        val epochNow = ldt.toEpochSecond(ZoneOffset.UTC)
        val where = "${dummy.published.k()}=?"
        val whatElse = "order by ${dummy.timestamp.k()} desc limit ?"
        return sqlQueries.load(dummy.allAttributes, dummy, where, ISQLQueries.args(true, n), whatElse)
    }

}