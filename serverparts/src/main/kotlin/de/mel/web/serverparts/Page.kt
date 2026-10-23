package de.mel.web.serverparts

import de.mel.auth.tools.N
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap


class Page {
    companion object {
        // the server answers on many threads at once
        val staticPagesCache = ConcurrentHashMap<String, Page>()
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
        this.contentType = MimeTypeReader.readMimeType(file) ?: ContentType.TEXT
        staticPagesCache.putIfAbsent(path, this)
    }


    /**
     * loads the resource at [path] and fills in its <§=tag/>s.
     * @throws FileNotFoundException if there is no such resource
     */
    constructor(path: String, vararg replacers: Replacer) {
        this.path = path
        this.contentType = MimeTypeReader.forName(path) ?: ContentType.TEXT
        val cached = staticPagesCache[path]
        if (cached != null && replacers.isEmpty()) {
            this.bytes = cached.bytes
            return
        }
//        Lok.debug("loading $path")
        val resourceBytes = javaClass.getResourceAsStream(path)?.use { it.readBytes() }
                ?: throw FileNotFoundException("no resource at $path")
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
                    if (it.escapeHtml)
                        replacement = Html.escape(replacement)
                    //escape double quotes
                    else if (it.escapeQuotes)
                        replacement = replacement.replace("\"", "&quot;")
                    html = html.replace(replacementRegex, Regex.escapeReplacement(replacement))
                }
            }
        }
        this.bytes = html.toByteArray()
        if (replacers.isEmpty())
            staticPagesCache.putIfAbsent(path, this)
    }
}

