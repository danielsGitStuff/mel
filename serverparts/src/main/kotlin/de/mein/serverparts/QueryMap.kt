package de.mein.serverparts

import com.sun.net.httpserver.HttpExchange
import de.mein.Lok
import java.net.URLDecoder
import java.util.HashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * parses URL query arguments
 */
class QueryMap {

    fun fillFromGet(httpExchange: HttpExchange): QueryMap {
        val string = httpExchange.requestURI.toString()
        val pat: Pattern = Pattern.compile("([^&=]+)=([^&]*)")
        val matcher: Matcher = pat.matcher(string)
        while (matcher.find()) {
            map.put(matcher.group(1), matcher.group(2))
        }
        Lok.debug()
        return this
    }

    val map = mutableMapOf<String, String>()


    fun fillFomPost(ex: HttpExchange): QueryMap {
        val text = ex.requestBody.reader().readText()
        text.split("&").map { URLDecoder.decode(it) }.forEach {
            val splitIndex = it.indexOf("=")
            val k = it.substring(0, splitIndex)
            val v = it.substring(splitIndex + 1, it.length)
            map[k] = v
        }
        return this
    }

    fun valueOf(key: String, value: String): Boolean {
        return map[key] == value
    }

    operator fun get(key: String): String? {
        return map[key]
    }

}