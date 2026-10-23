package de.mel.web.serverparts

object Html {
    /**
     * makes text safe to put between tags and into quoted attribute values.
     * use it for everything that did not come out of a template: file names, log lines, titles someone typed...
     */
    fun escape(text: String?): String {
        if (text == null)
            return ""
        val b = StringBuilder(text.length)
        text.forEach {
            when (it) {
                '&' -> b.append("&amp;")
                '<' -> b.append("&lt;")
                '>' -> b.append("&gt;")
                '"' -> b.append("&quot;")
                '\'' -> b.append("&#39;")
                else -> b.append(it)
            }
        }
        return b.toString()
    }
}
