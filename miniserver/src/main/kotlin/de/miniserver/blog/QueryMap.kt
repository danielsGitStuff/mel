package de.miniserver.blog

import de.mein.Lok
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * parses URL query arguments
 */
class QueryMap {
    val map = mutableMapOf<String, String>()

    fun parse(string: String): QueryMap {
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
}