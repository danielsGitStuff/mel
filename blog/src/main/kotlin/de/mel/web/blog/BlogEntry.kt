package de.mel.web.blog

import de.mel.sql.IPairSetListener
import de.mel.sql.Pair
import de.mel.sql.SQLTableObject
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
            if (str == null || str.trim().isEmpty()) {
                null
            } else
                str
        })
        populateInsert(title, text, timestamp, published)
        populateAll(id)
    }
}