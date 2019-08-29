package de.miniserver.blog

import com.sun.net.httpserver.HttpsServer
import de.mein.Lok
import de.mein.serverparts.AbstractHttpsThingy
import de.mein.serverparts.Page
import de.mein.serverparts.Replacer
import de.miniserver.MiniServer
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.net.ssl.SSLContext

/**
 * this is more an extension of the regular HttpsThingy
 */
class BlogThingy(val miniServer: MiniServer) : AbstractHttpsThingy(0, miniServer.httpCertificateManager.sslContext) {
    val blogDir = File(miniServer.workingDirectory, "blog")
    val blogDatabaseManager: BlogDatabaseManager
    val blogDao: BlogDao
    var defaultPage: Page? = null

    init {
        blogDir.mkdirs()
        blogDatabaseManager = BlogDatabaseManager(blogDir)
        blogDao = blogDatabaseManager.blogDao
    }

    override fun configureContext(server: HttpsServer) {
        server.createContext("/blog.html") {
            respondPage(it, defaultPage())
//            respondText(it, "/de/miniserver/blog/blog.html")
        }
        Lok.warn("test address: https://localhost:8443/blog.html")
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
            "blog.html?id=${entry.id.v()}"
        })

        val str = String(page.bytes)
        return str
    }

    private fun defaultPage(): Page {
        if (defaultPage == null)
        //load template page, with head line and so on
            defaultPage = Page("/de/miniserver/blog/blog.html"
                    , Replacer("entryDiv") {
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
            })
        return defaultPage!!
    }

    private fun embedDate(entry: BlogEntry): String {
        val ldt = entry.localDateTime
        val formatter = DateTimeFormatter.ofPattern("EEE LLL d yyyy")
        val formatted = ldt.format(formatter)
        return "<h2>$formatted</h2>"
    }

    override fun getRunnableName() = "I am BlogThingy and I shall not run in my own thread!"
}