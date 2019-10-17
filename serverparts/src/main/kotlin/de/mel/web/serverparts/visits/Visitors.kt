package de.mel.web.serverparts.visits

import com.sun.net.httpserver.HttpExchange
import de.mel.Lok
import de.mel.auth.tools.N
import de.mel.execute.SqliteExecutor
import de.mel.sql.RWLock
import de.mel.sql.SQLQueries
import de.mel.sql.conn.SQLConnector
import de.mel.sql.transform.SqlResultTransformer
import java.io.File
import java.time.LocalDateTime

class Visitors private constructor(private val dao: VisitsDao) {

    fun count(ex: HttpExchange) {
        if (ex.requestMethod == "GET") {
            N.oneLine {
                val now = LocalDateTime.now()
                // day = 20190324
                val day = "${now.year}${if (now.monthValue > 9) "${now.monthValue}" else "0${now.monthValue}"}${if (now.dayOfMonth > 9) "${now.dayOfMonth}" else "0${now.dayOfMonth}"}".toInt()
                val src = ex.remoteAddress.address.toString()
                val target = ex.requestURI.toString()
                Lok.debug("visit from ${ex.remoteAddress.hostString}")
                dao.increase(day, src, target)
            }
        }
    }

    companion object {
        fun fromDbFile(file: File): Visitors {
            val sqlQueries = SQLQueries(SQLConnector.createSqliteConnection(file), true, RWLock(), SqlResultTransformer.sqliteResultSetTransformer())
            val dao = VisitsDao(sqlQueries)

            val sqliteExecutor = SqliteExecutor(sqlQueries.sqlConnection)
            if (!sqliteExecutor.checkTablesExist("visits")) {
                dao.createTable()
            }
            return Visitors(dao)
        }
    }

}