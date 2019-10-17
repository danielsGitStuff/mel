package de.mel.web.serverparts

import de.mel.auth.tools.F
import de.mel.auth.tools.N
import java.io.File


class Page {
    companion object {
        val staticPagesCache = hashMapOf<String, Page>()
        val replaceTagRegex = "<§=[a-zA-Z]+[\\w]*\\/>".toRegex()

    }

    var path: String
        private set
    var bytes: ByteArray
        private set
    var contentType: String = ContentType.TEXT
        private set


    constructor(path: String, bytes: ByteArray, cache: Boolean = false) {
        this.path = path
        this.bytes = bytes
        if (cache)
            staticPagesCache[path] = this
    }

    constructor(path: String, file: File) {
        if (staticPagesCache.containsKey(path)) {
            val olde = staticPagesCache[path]!!
            this.path = path
            this.bytes = olde.bytes
            this.contentType = olde.contentType
            return
        }
        this.bytes = file.readBytes()
        this.path = path
        this.contentType = F.readMimeType(file) ?: ContentType.TEXT
        if (!staticPagesCache.containsKey(path)) {
            staticPagesCache[path] = this
        }
    }


    constructor(path: String, vararg replacers: Replacer) {
        if (staticPagesCache.containsKey(path) && replacers.isEmpty()) {
            this.path = path
            this.bytes = staticPagesCache[path]!!.bytes
            return
        }
//        Lok.debug("loading $path")
        val resourceBytes = javaClass.getResourceAsStream(path).readBytes()
        var html = String(resourceBytes)
        val tags = replaceTagRegex.findAll(html).toList()
        N.forEach(tags) { matchResult ->
            val stripped = matchResult.value.substring(3, matchResult.value.length - 2)
            replacers.firstOrNull { replacer -> replacer.test(stripped) }?.let {
                val replacementRegex = "<§=$stripped\\/>".toRegex()
//                Lok.debug("$stripped ... $replacementRegex")
                var replacement = it.replace("")
                if (replacement == null)
                    html = html.replace(replacementRegex, "")
                else {
                    //escape double quotes
                    if (it.escapeQuotes)
                        replacement = replacement.replace("\"", "&quot;")
                    html = html.replace(replacementRegex, Regex.escapeReplacement(replacement))
                }
            }
        }
        this.path = path
        this.bytes = html.toByteArray()
        if (!staticPagesCache.containsKey(path) && replacers.isEmpty()) {
            staticPagesCache[path] = this
        }
    }
}

