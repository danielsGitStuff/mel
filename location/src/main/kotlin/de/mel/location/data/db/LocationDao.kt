package de.mel.location.data.db

import de.mel.sql.Dao
import de.mel.sql.ISQLQueries

class LocationDao(sqlQueries: ISQLQueries) : Dao(sqlQueries) {

    fun insertIfAbsent(point: LocationPoint): Boolean {
        val proto = LocationPoint()
        val existing = sqlQueries.load(
            proto.allAttributes,
            proto,
            "${proto.tid.k()} = ? AND ${proto.tst.k()} = ?",
            ISQLQueries.args(point.tid.v(), point.tst.v())
        )
        if (existing.isNotEmpty()) return false
        sqlQueries.insert(point)
        return true
    }

    fun loadRecent(limit: Int = 200): List<LocationPoint> {
        val proto = LocationPoint()
        return sqlQueries.load(
            proto.allAttributes,
            proto,
            "1=1 ORDER BY ${proto.tst.k()} DESC LIMIT ?",
            ISQLQueries.args(limit)
        )
    }
}
