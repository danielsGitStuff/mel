package de.miniserver

import java.text.SimpleDateFormat
import java.util.*

class MDate {
    val date = Date()
    override fun toString(): String {
        return SimpleDateFormat.getDateTimeInstance().format(date)
//        return SimpleDateFormat("yyyy/mm/dd hh:mm ss").toString()
    }
}