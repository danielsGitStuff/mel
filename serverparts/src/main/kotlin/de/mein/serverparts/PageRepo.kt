package de.mein.serverparts

class PageRepo(val size: Int) {
    @Synchronized
    operator fun set(path: String, page: Page) {
        // page is new
        if (!pageRepo.containsKey(path)) {
            cleanUp()
            getCounts[path] = 0
        }
        pageRepo[path] = page
    }

    /**
     * free half of the size
     */
    private fun cleanUp() {
        if (pageRepo.size > size) {
            val counts = getCounts.values.sorted()
            val threshold = counts[size / 2]
            val mapCopy = mutableMapOf<String, Int>()
            mapCopy.putAll(getCounts)
            mapCopy.forEach { (path, count) ->
                if (count < threshold) {
                    pageRepo.remove(path)
                    getCounts.remove(path)
                }
            }
        }
    }

    operator fun get(path: String): Page? {
        if (pageRepo.containsKey(path)) {
            getCounts[path] = getCounts[path]!! + 1
            return pageRepo[path]
        }
        return null
    }

    private val pageRepo = mutableMapOf<String, Page>()
    private val getCounts = mutableMapOf<String, Int>()

}