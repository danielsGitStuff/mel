package de.miniserver.blog

import com.sun.net.httpserver.HttpExchange
import java.time.LocalDateTime

class Visitors(val dao: VisitsDao) {
    fun count(ex: HttpExchange) {
        if (ex.requestMethod == "GET") {
            val now = LocalDateTime.now()
            // day = 20190324
            val day = "${now.year}${if (now.monthValue > 0) now.monthValue else "0${now.monthValue}"}${if (now.dayOfMonth > 9) now.dayOfMonth else "0${now.dayOfMonth}"}".toInt()
            val src = ex.remoteAddress.hostName.toString()
            val target = ex.requestURI.toString()
            dao.increase(day, src, target)
        }
    }

}