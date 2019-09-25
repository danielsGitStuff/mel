package de.mel.serverparts

open class Replacer(val test: (String) -> Boolean, val replace: (String) -> String?, var escapeQuotes: Boolean = false) {
    constructor(matchString: String, replace: (String) -> String?) : this({ s: String -> matchString == s }, replace)
    constructor(matchString: String, replace: String?) : this({ s: String -> matchString == s }, { replace })

    fun escapeQuotes(): Replacer {
        escapeQuotes = true
        return this
    }
}