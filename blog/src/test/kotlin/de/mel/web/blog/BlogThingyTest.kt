package de.mel.web.blog

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * writing and deleting needs a login; the password never comes back in a page
 */
@Timeout(value = 1, unit = TimeUnit.MINUTES)
class BlogThingyTest {
    companion object {
        const val PORT = 35200
        const val PW = "the password"
    }

    @TempDir
    lateinit var dir: File
    private lateinit var blog: BlogThingy
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private var entryId: Long = 0

    @BeforeEach
    fun before() {
        val settings = BlogSettings.loadBlogSettings(dir)
        settings.port = PORT
        settings.user = "user"
        settings.password = PW
        blog = BlogThingy(settings, null)
        blog.start()
        val entry = BlogEntry()
        entry.title.v("\"><script>alert(1)</script>")
        entry.text.v("</textarea><b>text</b>")
        entry.published.v(true)
        entry.timestamp.v(1L)
        blog.blogDao.insert(entry)
        entryId = entry.id.v()
    }

    @AfterEach
    fun after() {
        blog.stop()
    }

    private fun post(body: String, cookie: String? = null): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$PORT/blog/write.html")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
        if (cookie != null)
            b.header("Cookie", cookie)
        return client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun login(): String {
        val response = post("user=user&pw=the+password&action=login&id=")
        assertEquals(200, response.statusCode())
        return response.headers().firstValue("Set-Cookie").get().substringBefore(';')
    }

    @Test
    fun deleteWithoutLoginIsRefused() {
        val response = post("action=delete&id=$entryId")
        assertEquals(403, response.statusCode())
        assertNotNull(blog.blogDao.getById(entryId))
    }

    @Test
    fun saveWithoutLoginIsRefused() {
        val response = post("action=save&id=$entryId&title=hacked&text=hacked")
        assertEquals(403, response.statusCode())
        assertEquals("\"><script>alert(1)</script>", blog.blogDao.getById(entryId)!!.title.v())
    }

    @Test
    fun wrongPasswordStartsNoSession() {
        val response = post("user=user&pw=guess&action=login&id=")
        assertEquals(200, response.statusCode())
        assertTrue(response.headers().firstValue("Set-Cookie").isEmpty)
        assertFalse(response.body().contains("Edit Entry") || response.body().contains("Write new Entry"))
    }

    @Test
    fun deleteWithLogin() {
        val cookie = login()
        assertTrue(cookie.startsWith("blogsession="))
        assertEquals(200, post("action=delete&id=$entryId", cookie).statusCode())
        assertNull(blog.blogDao.getById(entryId))
    }

    @Test
    fun theWritePageHasNoPasswordAndEscapesTheEntry() {
        val cookie = login()
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$PORT/blog/write.html?id=$entryId"))
                .header("Cookie", cookie).timeout(Duration.ofSeconds(10)).GET().build()
        val page = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        assertTrue(page.contains("Edit Entry"))
        assertFalse(page.contains(PW))
        assertFalse(page.contains("name=\"pw\""))
        assertTrue(page.contains("value=\"&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;\""))
        assertTrue(page.contains("<textarea id=\"txtText\" name=\"text\">&lt;/textarea&gt;&lt;b&gt;text&lt;/b&gt;</textarea>"))
        // the preview shows the title as text too
        assertFalse(page.contains("<script>"))
    }

    @Test
    fun noMatchGetsTheErrorPage() {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$PORT/blog/write.html"))
                .timeout(Duration.ofSeconds(10)).method("PUT", HttpRequest.BodyPublishers.noBody()).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(404, response.statusCode())
        assertTrue(response.body().contains("DADADA"))
        assertTrue(response.body().contains("<html"))
    }
}
