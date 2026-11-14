package de.mel.location.data.db

import de.mel.sql.Pair
import de.mel.sql.SQLTableObject

class LocationPoint : SQLTableObject() {
    val id = Pair(Long::class.java, "id")
    val tid = Pair(String::class.java, "tid")
    val tst = Pair(Long::class.java, "tst")
    val lat = Pair(Double::class.java, "lat")
    val lon = Pair(Double::class.java, "lon")
    val alt = Pair(Int::class.java, "alt")
    val acc = Pair(Int::class.java, "acc")
    val vel = Pair(Int::class.java, "vel")
    val batt = Pair(Int::class.java, "batt")
    val rawJson = Pair(String::class.java, "raw_json")

    init {
        init()
    }

    override fun getTableName(): String = "locations"

    override fun init() {
        populateInsert(tid, tst, lat, lon, alt, acc, vel, batt, rawJson)
        populateAll(id)
    }
}
