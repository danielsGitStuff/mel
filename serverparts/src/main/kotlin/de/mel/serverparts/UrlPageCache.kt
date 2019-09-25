package de.mel.serverparts

import com.sun.net.httpserver.HttpExchange

class UrlPageCache(val abstractHttpsThingy: AbstractHttpsThingy, val size: Int) {
    @Synchronized
    operator fun set(url: String, page: Page) {
        // page is new
        if (!pageCache.containsKey(url)) {
            cleanUp()
            getCounts[url] = 0
        }
        pageCache[url] = page
    }

    /**
     * free half of the size
     */
    private fun cleanUp() {
        if (pageCache.size > size) {
            val counts = getCounts.values.sorted()
            val threshold = counts[size / 2]
            val mapCopy = mutableMapOf<String, Int>()
            mapCopy.putAll(getCounts)
            mapCopy.forEach { (path, count) ->
                if (count < threshold) {
                    pageCache.remove(path)
                    getCounts.remove(path)
                }
            }
        }
    }

    operator fun get(path: String): Page? {
        if (pageCache.containsKey(path)) {
            getCounts[path] = getCounts[path]!! + 1
            return pageCache[path]
        }
        return null
    }

    private val pageCache = mutableMapOf<String, Page>()
    private val getCounts = mutableMapOf<String, Int>()

    fun respondPage(ex: HttpExchange, resource: String, vararg replacers: Replacer) {
        val url = ex.requestURI.toURL().toString()
        var page = get(url)
        if (page == null)
            page = Page(resource, *replacers)
        abstractHttpsThingy.respondPage(ex, page)
    }

    fun clear() {
        pageCache.clear()
        getCounts.clear()
    }

    fun constructOrGet(url: String, path: String, vararg replacers: Replacer): Page? {
        if (pageCache.containsKey(url))
            return get(url)
        val page = Page(path, *replacers)
        this[url] = page
        return this[url]
    }

}