package de.mel.web.serverparts

import com.sun.net.httpserver.HttpExchange
import java.net.URLDecoder

/**
 * parses URL query arguments
 */
class QueryMap {

    companion object {
        /**
         * parses "a=1&b=x%20y" (a GET query or a form POST body). Keys and values are URL-decoded,
         * a pair without '=' has an empty value.
         */
        fun parse(text: String?): MutableMap<String, String> {
            val map = mutableMapOf<String, String>()
            if (text == null)
                return map
            text.split("&").filter { it.isNotEmpty() }.forEach {
                val splitIndex = it.indexOf("=")
                if (splitIndex < 0)
                    map[decode(it)] = ""
                else
                    map[decode(it.substring(0, splitIndex))] = decode(it.substring(splitIndex + 1))
            }
            return map
        }

        private fun decode(s: String): String = URLDecoder.decode(s, Charsets.UTF_8)
    }

    fun fillFromGet(httpExchange: HttpExchange): QueryMap {
        map.putAll(parse(httpExchange.requestURI.rawQuery))
        return this
    }

    val map = mutableMapOf<String, String>()

    /**
     * the original request body comes here as a String. This is because you can only read it once.
     */
    var requestBody: String? = null
        get


    fun fillFomPost(ex: HttpExchange): QueryMap {
        requestBody = ex.requestBody.reader().readText()
        map.putAll(parse(requestBody))
        return this
    }

    fun valueOf(key: String, value: String): Boolean {
        return map[key] == value
    }

    operator fun get(key: String): String? {
        return map[key]
    }

    override fun toString(): String = map.toString()

}
