package de.mein.serverparts

open class Replacer(val test: (String) -> Boolean, val replace: (String) -> String?) {
    constructor(matchString: String, replace: (String) -> String?) : this({ s: String -> matchString == s }, replace)
    constructor(matchString: String, replace: String?) : this({ s: String -> matchString == s }, { replace })
}