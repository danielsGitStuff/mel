package de.mein.serverparts

import com.sun.net.httpserver.HttpExchange
import de.mein.Lok
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * parses URL query arguments
 */
class QueryMap(val contextInit: HttpContextCreator.ContextInit) {

    fun fillFromGet(httpExchange: HttpExchange) {
        parseGet(httpExchange.requestURI.toString())
    }

    val map = mutableMapOf<String, String>()


    private fun parseGet(string: String): QueryMap {
        val pat: Pattern = Pattern.compile("([^&=]+)=([^&]*)")
        val matcher: Matcher = pat.matcher(string)
        while (matcher.find()) {
            map.put(matcher.group(1), matcher.group(2))
        }
        Lok.debug()
        return this
    }

    fun valueOf(key: String, value: String): Boolean {
        return map[key] == value
    }

    operator fun get(key: String): String? {
        return map[key]
    }

    val expectations = mutableListOf<Expectation>()

    fun expect(key: String, expectedValue: String?): Expectation {
        val expectation = Expectation(contextInit, this, key, expectedValue)
        expectations += expectation
        return expectation
    }

}