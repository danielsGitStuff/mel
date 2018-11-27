package de.miniserver.http

import de.mein.Lok

class Page(val path: String, val bytes: ByteArray) {

}

class Replacer(val test: (String) -> Boolean, val replace: (String) -> String) {
    constructor(matchString: String, replace: (String) -> String) : this({ s: String -> matchString == s }, replace)
    constructor(matchString: String, replace: String) : this({ s: String -> matchString == s }, { replace })
}

class PageProcessor {
    val replaceTagRegex = "<\\\$=[a-zA-Z]+[\\w]*\\/>".toRegex()

    fun load(path: String, vararg replacers: Replacer): Page {
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
        return Page(path, html.toByteArray())
    }

//    private fun parseIndexHtml(): ByteArray? {
//        val regex = "<\\\$=\\D+[\\w]*\\/>".toRegex()
//        val resourceBytes = javaClass.getResourceAsStream("/de/miniserver/index.html").readBytes()
//        val html = String(resourceBytes)
//        if (!html.contains(regex)) {
//            throw Exception("did not find '<$=files/>' tag to replace with file content")
//        }
//        val s = StringBuilder()
//        miniServer.fileRepository.hashFileMap.entries.forEach {
//            s.append("<p><a href=\"files/${it.key}\" download=\"${it.value.name}\">${it.value.name}</a> ${it.key}</p>")
//        }
//        val filesHtml = html.replace(regex, s.toString())
//        return filesHtml.toByteArray()
//    }
}