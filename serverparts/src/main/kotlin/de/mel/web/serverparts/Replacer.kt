package de.mel.web.serverparts

open class Replacer(val test: (String) -> Boolean, val replace: (String) -> String?, var escapeQuotes: Boolean = false) {
    constructor(matchString: String, replace: (String) -> String?) : this({ s: String -> matchString == s }, replace)
    constructor(matchString: String, replace: String?) : this({ s: String -> matchString == s }, { replace })

    var escapeHtml: Boolean = false
        private set

    fun escapeQuotes(): Replacer {
        escapeQuotes = true
        return this
    }

    /**
     * the replacement is text, not markup: see [Html.escape]
     */
    fun escapeHtml(): Replacer {
        escapeHtml = true
        return this
    }
}
