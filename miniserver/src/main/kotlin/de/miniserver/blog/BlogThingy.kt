package de.miniserver.blog

import com.sun.net.httpserver.HttpsServer
import de.mein.Lok
import de.mein.serverparts.AbstractHttpsThingy
import de.mein.serverparts.Page
import de.mein.serverparts.Replacer
import de.miniserver.MiniServer
import java.io.File
import java.lang.Exception
import java.time.format.DateTimeFormatter

/**
 * this is more an extension of the regular HttpsThingy
 */
class BlogThingy(val miniServer: MiniServer) : AbstractHttpsThingy(0, miniServer.httpCertificateManager.sslContext) {
    val blogDir = File(miniServer.workingDirectory, "blog")
    val blogDatabaseManager: BlogDatabaseManager
    val blogDao: BlogDao
    var defaultPage: Page? = null
    val blogSettings: BlogSettings

    init {
        blogDir.mkdirs()
        blogDatabaseManager = BlogDatabaseManager(blogDir)
        blogDao = blogDatabaseManager.blogDao
        blogSettings = BlogSettings.loadBlogSettings(blogDir)
        blogSettings.save()
        Lok.debug("blog loaded")
    }

    override fun configureContext(server: HttpsServer) {
        server.createContext("/blog/index.html") {
            respondPage(it, defaultPage())
//            respondText(it, "/de/miniserver/blog/blog.html")
        }
        server.createContext("/blog/login.html") {
            respondPage(it, loginPage())
        }
        server.createContext("/blog/write.html") {
            Lok.debug("write")
            if (it.requestMethod == "POST") {
                val attr = readPostValues(it)
                val pw = attr["pw"]
                val id = attr["id"]?.toLong()
                when (pw) {
                    null -> {
                        respondPage(it, loginPage(id))
                    }
                    blogSettings.password -> {
                        respondPage(it, writePage(pw, id))
                    }
                    else -> {
                        //todo debug
                        Lok.info("## password did not match")
                        respondPage(it, loginPage())
                    }
                }
            } else {
                val query = it.requestURI.query
                if (query.startsWith("id=")) {
                    try {
                        val idString = query.substring("id=".length, query.length)
                        val id = idString.toLong()
                        val entry = blogDao.getById(id)
                        respondPage(it, loginPage(id))
                    } catch (e: Exception) {
                        it.close()
                    }
                } else
                    it.close()
            }
        }
        Lok.warn("test address: https://localhost:8443/blog/index.html")
    }

    private fun writePage(pw: String, id: Long?): Page {
        var entry: BlogEntry? = null
        if (id != null)
            entry = blogDao.getById(id)

        return Page("/de/miniserver/blog/write.html", Replacer("pw", pw),
                Replacer("id", id?.toString()),
                Replacer("title", entry?.title?.v()),
                Replacer("text", entry?.text?.v())
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
        return defaultPage!!
    }

    private fun loginPage(id: Long? = null): Page {
        if (id == null)
            return Page("/de/miniserver/blog/login.html")
        return Page("/de/miniserver/blog/login.html", Replacer("id", id.toString()))
    }

    private fun embedDate(entry: BlogEntry): String {
        val ldt = entry.localDateTime
        val formatter = DateTimeFormatter.ofPattern("EEE LLL d yyyy")
        val formatted = ldt.format(formatter)
        return "<h2>$formatted</h2>"
    }

    override fun getRunnableName() = "I am BlogThingy and I shall not run in my own thread!"
}