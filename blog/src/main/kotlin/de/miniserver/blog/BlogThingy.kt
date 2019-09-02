package de.miniserver.blog

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpsServer
import de.mein.Lok
import de.mein.auth.tools.N
import de.mein.serverparts.AbstractHttpsThingy
import de.mein.serverparts.Page
import de.mein.serverparts.Replacer
import java.io.File
import java.lang.Exception
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLContext

/**
 * this is more an extension of the regular HttpsThingy.
 * currently this mixes both POST and GET simultaneously to deal with authentication and parameters the user sends you.
 * I wanted to plant the authentication info in the response header but the browser somehow does not send it back.
 */
class BlogThingy(val blogSettings: BlogSettings, sslContext: SSLContext) : AbstractHttpsThingy(blogSettings.port!!, sslContext) {
    val blogDatabaseManager: BlogDatabaseManager
    val blogDao: BlogDao
    var defaultPage: Page? = null
    val blogAuthenticator = BlogAuthenticator(this)
    val subUrl = blogSettings.subUrl

    init {
        blogDatabaseManager = BlogDatabaseManager(blogSettings.blogDir!!)
        blogDao = blogDatabaseManager.blogDao
        blogSettings.save()
        Lok.debug("blog loaded")
    }

    companion object {
        const val ACTION_SAVE = "save"
        const val ACTION_DELETE = "delete"

        const val PARAM_ID = "id"
        const val PARAM_USER = "user"
        const val PARAM_PW = "pw"
        const val PARAM_TITLE = "title"
        const val PARAM_TEXT = "text"
        const val PARAM_PUBLISH = "publish"
    }

    override fun configureContext(server: HttpsServer) {
        server.createContext("/$subUrl"){
            redirect(it,"/$subUrl/index.html")
        }
        server.createContext("/$subUrl/") {
            Lok.error("redirect")
            redirect(it, "/$subUrl/index.html")
        }
        server.createContext("/$subUrl/index.html") {
            respondPage(it, defaultPage())
        }
        server.createContext("/$subUrl/login.html") {
            respondPage(it, loginPage())
        }
        server.createContext("/$subUrl/write.html") {
            Lok.debug("write")

            val uri = it.requestURI
            val query = uri.query
            if (it.requestMethod == "POST") {
                if (query != null) {
                    //update entry here
                    val queryMap = QueryMap().parse(query)
                    if (queryMap["a"] == ACTION_SAVE && queryMap[PARAM_ID] != null) {
                        val attr = readPostValues(it)
                        val user = attr[PARAM_USER]
                        val pw = attr[PARAM_PW]
                        blogAuthenticator.check(it, user, pw, N.INoTryRunnable {
                            val id = N.result({ queryMap[PARAM_ID]!!.toLong() }, null)
                            //edit
                            if (id != null) {
                                val entry = blogDao.getById(id)
                                entry.title.v(attr[PARAM_TITLE])
                                entry.text.v(attr[PARAM_TEXT])
                                blogDao.update(entry)
                                respondPage(it, writePage(user, pw, id))
                            } else {
                                //create
                                val publish = attr[PARAM_PUBLISH] == "on"
                                val entry = BlogEntry()
                                entry.title.v(attr[PARAM_TITLE])
                                entry.text.v(attr[PARAM_TEXT])
                                entry.timestamp.v(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                                entry.published.v(publish)
                                blogDao.insert(entry)
                                respondPage(it, writePage(user, pw, entry.id.v()))
                            }
                            //show what is new
                            rebuildDefaultPage()
                        }, null)
                    } else if (queryMap["a"] == ACTION_DELETE) {
                        val id = N.result({ queryMap[PARAM_ID]!!.toLong() }, null)
                        if (id == null)
                            respondPage(it, loginPage(null))
                        else {
                            blogDao.deleteById(id)
                            respondPage(it, loginPage(null))
                        }
                    }
                } else {
                    //login here, show write page
                    val attr = readPostValues(it)
                    val user = attr["user"]
                    val pw = attr["pw"]

                    val id = N.result(N.ResultExceptionRunnable { attr[PARAM_ID]?.toLong() }, null)
                    blogAuthenticator.authenticate(it, user, pw, N.INoTryRunnable {
                        respondPage(it, writePage(user, pw, id))
                    }, N.INoTryRunnable {
                        respondPage(it, loginPage())
                    })
                }
            } else {
                val query = it.requestURI.query
                if (query != null && query.startsWith("id=")) {
                    try {
                        val idString = query.substring("id=".length, query.length)
                        val id = idString.toLong()
                        val entry = blogDao.getById(id)
                        respondPage(it, loginPage(id))
//                        blogAuthenticator.check(it, N.INoTryRunnable { respondPage(it, writePage(null, null, id)) }, N.INoTryRunnable { respondPage(it, loginPage(id)) })
                    } catch (e: Exception) {
                        it.close()
                    }
                } else
                    respondPage(it, loginPage(null))
            }

        }

        server.createContext("/$subUrl/blog.css") {
            respondText(it, "/de/miniserver/blog/blog.css")
        }
    }

    private fun rebuildDefaultPage() {
        //load template page, with head line and so on
        defaultPage = Page("/de/miniserver/blog/index.html", Replacer("entryDiv") {
            // fill blog entries here
            val b = StringBuilder()
            var dateString: String? = null
            val entries = blogDao.getLastNentries(5)
            entries?.forEach { entry ->
                val entryDateString = entry.dateString
                // if another day: display date
                if (dateString == null || dateString != entryDateString)
                    b.append(embedDate(entry))
                dateString = entryDateString
                b.append("${embedEntry(entry)}\n")
            }
            b.toString()
        }, Replacer("name", blogSettings.name!!),
                Replacer("motto", blogSettings.motto!!)

        )
    }

    private fun readHeader(requestHeaders: Headers): MutableMap<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        requestHeaders.forEach { map[it.key] = it.value }
        return map
    }

    private fun writePage(user: String?, pw: String?, id: Long?): Page {
        var entry: BlogEntry? = null
        if (id != null)
            entry = blogDao.getById(id)
        val mode = if (id == null) "Write new Entry" else "Edit Entry"
        return Page("/de/miniserver/blog/write.html", Replacer("pw", pw),
                Replacer("user", user),
                Replacer("id", id?.toString()),
                Replacer("title", entry?.title?.v()),
                Replacer("text", entry?.text?.v()),
                Replacer("mode", mode)
        )
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
                return@Replacer "<p class=\"entrytext\">${entry.text.v()}</p>"
        }
                , Replacer("link") {
            "index.html?id=${entry.id.v()}"
        })

        val str = String(page.bytes)
        return str
    }

    private fun defaultPage(): Page {
        if (defaultPage == null)
            rebuildDefaultPage()
        return defaultPage!!
    }

    private fun loginPage(id: Long? = null): Page {
        if (id == null)
            return Page("/de/miniserver/blog/login.html", Replacer(PARAM_ID, null))
        return Page("/de/miniserver/blog/login.html", Replacer(PARAM_ID, id.toString()))
    }

    private fun embedDate(entry: BlogEntry): String {
        val ldt = entry.localDateTime
        val formatter = DateTimeFormatter.ofPattern("EEE LLL d yyyy")
        val formatted = ldt.format(formatter)
        return "<h2>$formatted</h2>"
    }

    override fun getRunnableName() = "I am BlogThingy and I shall not run in my own thread!"
}