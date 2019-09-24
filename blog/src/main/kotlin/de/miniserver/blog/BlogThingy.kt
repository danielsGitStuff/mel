package de.miniserver.blog

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsServer
import de.mein.Lok
import de.mein.auth.tools.N
import de.mein.serverparts.*
import de.mein.serverparts.visits.Visitors
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.net.ssl.SSLContext

/**
 * this is more an extension of the regular HttpsThingy.
 * currently this mixes both POST and GET simultaneously to deal with authentication and parameters the user sends you.
 * I wanted to plant the authentication info in the response header but the browser somehow does not send it back.
 */
class BlogThingy(val blogSettings: BlogSettings, sslContext: SSLContext) : AbstractHttpsThingy(blogSettings.port!!, sslContext) {
    val blogDatabaseManager: BlogDatabaseManager
    val blogDao: BlogDao
    val blogAuthenticator = BlogAuthenticator(this)
    val subUrl = blogSettings.subUrl
    var visitors: Visitors? = null
    val pageCache = UrlPageCache(this, 100)

    init {
        blogDatabaseManager = BlogDatabaseManager(blogSettings.blogDir!!)
        blogDao = blogDatabaseManager.blogDao
        blogSettings.save()
        if (blogSettings.countVisitors!!)
            visitors = Visitors.fromDbFile(File(blogSettings.blogDir, "visitors.blog.db"))
        Lok.debug("blog loaded")
    }

    companion object {
        const val ACTION_SAVE = "save"
        const val ACTION_DELETE = "delete"

        const val PARAM_ACTION = "action"
        const val PARAM_ID = "id"
        const val PARAM_USER = "user"
        const val PARAM_PW = "pw"
        const val PARAM_TITLE = "title"
        const val PARAM_TEXT = "text"
        const val PARAM_PUBLISH = "publish"

        fun embedDate(entry: BlogEntry): String {
            val ldt = entry.localDateTime
            val formatter = DateTimeFormatter.ofPattern("EEE LLL d yyyy")
            val formatted = ldt.format(formatter)
            return "<h2>$formatted</h2>"
        }

        private fun embedEntry(entry: BlogEntry): String {
            val page = Page("/de/miniserver/blog/entry.template.html"
                    , Replacer("title") {
                if (entry.title.isNull)
                    ""
                else
                    "<span class=\"title\">${entry.title.v()}</span><br>"
            }
                    , Replacer("text") {
                if (entry.title.isNull)
                    return@Replacer entry.text.v()
                else
                    return@Replacer "<div class=\"entrytext\">${entry.text.v()}</div>"
            }
                    , Replacer("link") {
                "index.html?id=${entry.id.v()}"
            })

            val str = String(page.bytes)
            return str
        }
    }

    override fun respondPage(ex: HttpExchange, page: Page?, contentType: String?) {
        super.respondPage(ex, page, contentType)
        visitors?.count(ex)
    }

    override fun configureContext(server: HttpsServer) {
        this.server = server
        val httpContextCreator = HttpContextCreator(server)
        createServerContext("/$subUrl") {
            redirect(it, "/$subUrl/index.html")
        }
        createServerContext("/$subUrl/") {
            Lok.error("redirect")
            redirect(it, "/$subUrl/index.html")
        }
        createServerContext("/$subUrl/index.html") {

            val query = it.requestURI.query
            if (query != null) {
                val queryMap = readGetQuery(query)
                // a certain entry is requested
                val id = queryMap["id"]
                val m = queryMap["m"]
                if (id != null) {
                    respondPage(it, pageEntry(it, id.toLong()))
                } else if (m != null) {
                    // a certain month requested
                    var year = 0
                    var month = 0
                    run {
                        val split = m.split(".")
                        year = split[0].toInt()
                        month = split[1].toInt()
                    }
                    val startDate = LocalDateTime.of(year, month, 1, 0, 0)
                    val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withSecond(59)
                    val entries = blogDao.pubGetByDateRange(startDate.toEpochSecond(ZoneOffset.UTC), endDate.toEpochSecond(ZoneOffset.UTC))
                    respondPage(it, pageWithEntries(it.requestURI.toString(), entries))
                }
            } else {
                respondPage(it, pageDefault(it.requestURI.toString()))
//                // this is the default page: count it
//                visitors?.count(it)
            }
        }
        createServerContext("/$subUrl/login.html") {
            respondPage(it, pageLogin(it))
        }

        httpContextCreator.createContext("/$subUrl/write.html")
                .withPOST()
                .expect(PARAM_ACTION, ACTION_SAVE).and(PARAM_ID) { a -> a != null }
                .handle { httpExchange, queryMap ->
                    val user = queryMap[PARAM_USER]
                    val pw = queryMap[PARAM_PW]
                    blogAuthenticator.check(httpExchange, user, pw, N.INoTryRunnable {
                        val id = N.result({ queryMap[PARAM_ID]!!.toLong() }, null)
                        val publish = queryMap[PARAM_PUBLISH] == "on"
                        //edit
                        if (id != null) {
                            val entry = blogDao.getById(id)
                            if (entry != null) {
                                entry.title.v(queryMap[PARAM_TITLE])
                                entry.text.v(queryMap[PARAM_TEXT])
                                entry.published.v(publish)
                                blogDao.update(entry)
                                respondPage(httpExchange, pageWrite(user, pw, id))
                            }
                        } else {
                            //create
                            val entry = BlogEntry()
                            entry.title.v(queryMap[PARAM_TITLE])
                            entry.text.v(queryMap[PARAM_TEXT])
                            entry.timestamp.v(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                            entry.published.v(publish)
                            blogDao.insert(entry)
                            respondPage(httpExchange, pageWrite(user, pw, entry.id.v()))
                        }
                        //show what is new
                        pageCache.clear()
                    }, null)
                }
                .withPOST().expect(PARAM_ACTION, ACTION_DELETE).and(PARAM_ID) { it != null }
                .handle { httpExchange, queryMap ->
                    val id = queryMap[PARAM_ID]!!.toLong()
                    blogDao.deleteById(id)
                    pageCache.clear()
                    respondPage(httpExchange, pageLogin(httpExchange))
                }
                .withPOST().expect(PARAM_USER) { it != null }.and(PARAM_PW) { it != null }
                .handle { httpExchange, queryMap ->
                    val user = queryMap[PARAM_USER]
                    val pw = queryMap[PARAM_PW]
                    val id = N.result({ queryMap[PARAM_ID]?.toLong() }, null)
                    blogAuthenticator.check(httpExchange, user, pw, N.INoTryRunnable {
                        respondPage(httpExchange, pageWrite(user, pw, id))
                    }, N.INoTryRunnable {
                        respondPage(httpExchange, pageLogin(httpExchange, id))
                    })
                }
                .withGET().expect(PARAM_ID) { it != null }
                .handle { httpExchange, queryMap ->
                    Lok.debug("AAAA")
                    val idString = queryMap[PARAM_ID]
                    if (idString != null) {
                        val id = idString.toLong()
                        respondPage(httpExchange, pageLogin(httpExchange, id))
                    }
                }
                .withGET().handle { httpExchange, queryMap ->
                    respondPage(httpExchange, pageLogin(httpExchange, null))

                }
                .onError { httpExchange, exception -> respondError(httpExchange, "Ebola?!") }
                .onNoMatch { httpExchange, queryMap -> respondError(httpExchange, "I just don't know what to do<br> with myself! DADADA!") }

        createServerContext("/$subUrl/blog.css") {
            respondPage(it, Page("/de/miniserver/blog/blog.css"), contentType = null)
//            respondText(it, "/de/miniserver/blog/blog.css")
        }
    }


    private fun readHeader(requestHeaders: Headers): MutableMap<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        requestHeaders.forEach { map[it.key] = it.value }
        return map
    }

    private fun pageWrite(user: String?, pw: String?, id: Long?): Page {
        var entry: BlogEntry? = null
        if (id != null)
            entry = blogDao.getById(id)
        val mode = if (id == null) "Write new Entry" else "Edit Entry"
        return Page("/de/miniserver/blog/write.html", Replacer("pw", pw),
                Replacer("user", user).escapeQuotes(),
                Replacer("id", id?.toString()).escapeQuotes(),
                Replacer("title", entry?.title?.v()).escapeQuotes(),
                Replacer("text", entry?.text?.v()),
                Replacer("publish", if (entry != null && entry.published.v()) "checked" else ""),
                Replacer("mode", mode),
                Replacer("preview", if (entry != null) embedEntry(entry) else null)
        )
    }


    private fun pageEntry(ex: HttpExchange, id: Long): Page? {
        val url = ex.requestURI.toString()
        //load template page, with head line and so on
        val entries: MutableList<BlogEntry> = mutableListOf()
        val entry = blogDao.pubGetById(id)
        if (entry != null)
            entries.add(entry)
        return pageWithEntries(url, entries)
    }

    class EntriesReplacer(entries: List<BlogEntry>) : Replacer("entryDiv", { s: String ->
        // fill blog entries here
        val b = StringBuilder()
        var dateString: String? = null
        entries.forEach { entry ->
            val entryDateString = entry.dateString
            // if another day: display date
            if (dateString == null || dateString != entryDateString)
                b.append(embedDate(entry))
            dateString = entryDateString
            b.append("${embedEntry(entry)}\n")
        }
        b.toString()
    })

    private fun pageWithEntries(url: String, entries: List<BlogEntry>): Page? {
        //load template page, with head line and so on
        val page = pageCache.constructOrGet(url, "/de/miniserver/blog/index.html"
                , EntriesReplacer(entries)
                , Replacer("name", blogSettings.name!!),
                Replacer("motto", blogSettings.motto!!),
                Replacer("month") {
                    var result: String? = null
                    if (entries?.size >= 1) {
                        val entry = entries[0]
                        val ldt = LocalDateTime.ofEpochSecond(entry.timestamp.v(), 0, ZoneOffset.UTC)
                        result = "?m=${ldt.year}.${ldt.monthValue}"
                    }
                    result
                }
        )
        return page
    }

    private fun pageDefault(url: String): Page? {
        val entries = blogDao.pubGetLastNentries(20)
        return pageWithEntries(url, entries)
    }

    private fun pageLogin(ex: HttpExchange, id: Long? = null): Page? {
        val url = ex.requestURI.toString()
        if (id == null)
            return pageCache.constructOrGet(url, "/de/miniserver/blog/login.html", Replacer(PARAM_ID, null))
        return pageCache.constructOrGet(url, "/de/miniserver/blog/login.html", Replacer(PARAM_ID, id.toString()))
    }


    override fun getRunnableName() = "I am BlogThingy and I shall not run in my own thread!"
}