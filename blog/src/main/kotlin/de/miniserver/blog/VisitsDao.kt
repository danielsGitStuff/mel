package de.miniserver.blog

import de.mein.sql.*

class VisitsDao(sqlQueries: SQLQueries) : Dao(sqlQueries) {
    class Visits : SQLTableObject() {
        override fun getTableName(): String = "visits"
        val day = Pair(Int::class.java, "dayid")
        val count = Pair(Int::class.java, "visits")
        val src = Pair(String::class.java, "src")
        override fun init() {
            populateInsert(day, count, src)
            populateAll()
        }
    }

    val dummy = Visits()

    @Synchronized
    fun increase(day: Int, src: String): Unit {
        //insert into visits (dayid,src,visits) values(123,"aaa",13) on CONFLICT(dayid,src) do update set visits=visits+excluded.visits;
        val stmt = "insert into ${dummy.tableName} (${dummy.day.k()},${dummy.src.k()},${dummy.count.k()} values(?,?,?) " +
                "on conflict do update set ${dummy.count.k()}=${dummy.count.k()}+?)"
        sqlQueries.execute(stmt, ISQLQueries.args(day, src, 1, 1))
    }
}