package de.miniserver.blog

import de.mein.sql.*

class VisitsDao(sqlQueries: SQLQueries) : Dao(sqlQueries) {
    companion object {
        const val DAY_ID = "dayid"
        const val COUNT = "visits"
        const val SRC = "src"
        const val TARGET = "target"
    }

    class VisitEntry : SQLTableObject() {
        override fun getTableName(): String = "visits"
        val day = Pair(Int::class.java, DAY_ID)
        val count = Pair(Int::class.java, COUNT)
        val src = Pair(String::class.java, SRC)
        val target = Pair(String::class.java, TARGET)
        override fun init() {
            populateInsert(day, count, src, target)
            populateAll()
        }
    }

    val dummy = VisitEntry()

    @Synchronized
    fun increase(day: Int, src: String, target: String): Unit {
        //insert into visits (dayid,src,visits) values(123,"aaa",13) on CONFLICT(dayid,src) do update set visits=visits+excluded.visits;
        val stmt = "insert into ${dummy.tableName} (${dummy.day.k()},${dummy.src.k()},${dummy.target.k()},${dummy.count.k()} values(?,?,?,?) " +
                "on conflict do update set ${dummy.count.k()}=${dummy.count.k()}+?)"
        sqlQueries.execute(stmt, ISQLQueries.args(day, src, target, 1, 1))
    }
}