package de.mein.serverparts

import de.mein.Lok


class Page {
    companion object {
        val pageRepo = hashMapOf<String, Page>()
    }

    var path: String
        private set
    var bytes: ByteArray
        private set

    private val replaceTagRegex = "<\\\$=[a-zA-Z]+[\\w]*\\/>".toRegex()

    constructor(path: String, bytes: ByteArray, cache:Boolean = false) {
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
        val tags = replaceTagRegex.findAll(html)
        tags.forEach { matchResult ->
            val stripped = matchResult.value.substring(3, matchResult.value.length - 2)
            replacers.firstOrNull { replacer -> replacer.test(stripped) }?.let {
                val replacementRegex = "<\\\$=$stripped\\/>".toRegex()
                html = html.replace(replacementRegex, it.replace(""))
            }
        }
        this.path = path
        this.bytes = html.toByteArray()
        if (!pageRepo.containsKey(path) && replacers.isEmpty()) {
            pageRepo[path] = this
        }
    }
}

class Replacer(val test: (String) -> Boolean, val replace: (String) -> String) {
    constructor(matchString: String, replace: (String) -> String) : this({ s: String -> matchString == s }, replace)
    constructor(matchString: String, replace: String) : this({ s: String -> matchString == s }, { replace })
}