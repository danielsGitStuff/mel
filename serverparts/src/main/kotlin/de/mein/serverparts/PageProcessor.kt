package de.mein.serverparts

import de.mein.Lok
import de.mein.auth.tools.N


class Page {
    companion object {
        val pageRepo = hashMapOf<String, Page>()
    }

    var path: String
        private set
    var bytes: ByteArray
        private set

    private val replaceTagRegex = "<§=[a-zA-Z]+[\\w]*\\/>".toRegex()

    constructor(path: String, bytes: ByteArray, cache: Boolean = false) {
        this.path = path
        this.bytes = bytes
        if (cache)
            pageRepo[path] = this
    }


    constructor(path: String, vararg replacers: Replacer) {
        if (pageRepo.containsKey(path) && replacers.isEmpty()) {
            this.path = path
            this.bytes = pageRepo[path]!!.bytes
            return
        }
        Lok.debug("loading $path")
        val resourceBytes = javaClass.getResourceAsStream(path).readBytes()
        var html = String(resourceBytes)
        val tags = replaceTagRegex.findAll(html).toList()
        N.forEach(tags) { matchResult ->
            val stripped = matchResult.value.substring(3, matchResult.value.length - 2)
            replacers.firstOrNull { replacer -> replacer.test(stripped) }?.let {
                val replacementRegex = "<§=$stripped\\/>".toRegex()
//                Lok.debug("$stripped ... $replacementRegex")
                val replacement = it.replace("")
                if (replacement == null)
                    html = html.replace(replacementRegex, "")
                else
                    html = html.replace(replacementRegex, replacement)
            }
        }
        this.path = path
        this.bytes = html.toByteArray()
        if (!pageRepo.containsKey(path) && replacers.isEmpty()) {
            pageRepo[path] = this
        }
    }
}

class Replacer(val test: (String) -> Boolean, val replace: (String) -> String?) {
    constructor(matchString: String, replace: (String) -> String?) : this({ s: String -> matchString == s }, replace)
    constructor(matchString: String, replace: String?) : this({ s: String -> matchString == s }, { replace })
}