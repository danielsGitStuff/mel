package de.mel.web.serverparts

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * every request gets an answer, also when a handler fails. Before, these requests hung until the client gave up.
 */
@Timeout(value = 1, unit = TimeUnit.MINUTES)
class ServerPartsTest {
    companion object {
        const val PORT = 35100
    }

    private val sessions = Sessions("testsession", secure = false)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private lateinit var thingy: AbstractHttpsThingy

    @BeforeEach
    fun before() {
        thingy = object : AbstractHttpsThingy(PORT, null) {
            override fun getRunnableName() = "test server"

            override fun configureContext(server: HttpServer) {
                val contextCreator = HttpContextCreator(server)
                contextCreator.createContext("/boom")
                        .withGET()
                        .handle { _, _ -> throw RuntimeException("boom") }
                contextCreator.createContext("/boomExpected")
                        .withGET()
                        .expect("a") { true }
                        .handle { _, _ -> throw RuntimeException("boom") }
                contextCreator.createContext("/postOnly")
                        .withPOST()
                        .handle { ex, _ -> respond(ex, 200, "post".toByteArray()) }
                contextCreator.createContext("/missing")
                        .withGET()
                        .handle { ex, _ -> respondText(ex, "/no/such/page.html") }
                contextCreator.createContext("/nullPage")
                        .withGET()
                        .handle { ex, _ -> respondPage(ex, null) }
                contextCreator.createContext("/echo")
                        .withGET()
                        .handle { ex, q -> respond(ex, 200, (q["q"] ?: "null").toByteArray(), ContentType.PLAIN) }
                        .withPOST()
                        .handle { ex, q -> respond(ex, 200, q.map.toSortedMap().toString().toByteArray(), ContentType.PLAIN) }
                contextCreator.createContext("/login")
                        .withPOST()
                        .expect("pw") { Sessions.passwordMatches(it, "secret") }
                        .handle { ex, _ ->
                            sessions.start(ex)
                            respond(ex, 200, "in".toByteArray())
                        }
                        .withPOST()
                        .handle { ex, _ -> respond(ex, 403, "wrong".toByteArray()) }
                contextCreator.createContext("/private")
                        .withGET()
                        .handle { ex, _ ->
                            if (sessions.isValid(ex)) respond(ex, 200, "secret stuff".toByteArray())
                            else respond(ex, 403, "no".toByteArray())
                        }
            }
        }
        thingy.start()
    }

    @AfterEach
    fun after() {
        thingy.stop()
    }

    private fun get(path: String, cookie: String? = null): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$PORT$path")).timeout(Duration.ofSeconds(10))
        if (cookie != null)
            b.header("Cookie", cookie)
        return client.send(b.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun post(path: String, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$PORT$path")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun aHandlerThatThrowsGets500() {
        assertEquals(500, get("/boom").statusCode())
        assertEquals(500, get("/boomExpected?a=1").statusCode())
    }

    @Test
    fun noMatchingHandlerGets404() {
        assertEquals(404, get("/postOnly").statusCode())
        assertEquals(200, post("/postOnly", "").statusCode())
    }

    @Test
    fun missingResourceAndNullPageGet404() {
        assertEquals(404, get("/missing").statusCode())
        assertEquals(404, get("/nullPage").statusCode())
    }

    @Test
    fun getAndPostValuesAreDecoded() {
        assertEquals("ä b=c", get("/echo?q=%C3%A4+b%3Dc").body())
        // a field without '=' used to throw
        assertEquals("{a=1 2, flag=, x=&}", post("/echo", "a=1+2&flag&x=%26").body())
    }

    @Test
    fun parse() {
        assertEquals(mapOf("a" to "1", "b" to "", "c" to "x=y"), QueryMap.parse("a=1&b&c=x%3Dy&"))
        assertTrue(QueryMap.parse(null).isEmpty())
        assertTrue(QueryMap.parse("").isEmpty())
    }

    @Test
    fun aSessionIsOnlyStartedWithTheRightPassword() {
        val wrong = post("/login", "pw=guess")
        assertEquals(403, wrong.statusCode())
        assertTrue(wrong.headers().firstValue("Set-Cookie").isEmpty)

        val right = post("/login", "pw=secret")
        assertEquals(200, right.statusCode())
        val setCookie = right.headers().firstValue("Set-Cookie").get()
        assertTrue(setCookie.contains("HttpOnly"))
        assertTrue(setCookie.contains("SameSite=Strict"))
        val cookie = setCookie.substringBefore(';')

        assertEquals(200, get("/private", cookie).statusCode())
        assertEquals(403, get("/private").statusCode())
        assertEquals(403, get("/private", "testsession=madeup").statusCode())
        // other cookies in the same header do not confuse it
        assertEquals(200, get("/private", "other=1; $cookie").statusCode())
    }

    @Test
    fun passwordMatches() {
        assertTrue(Sessions.passwordMatches("secret", "secret"))
        assertFalse(Sessions.passwordMatches("secret", "secreT"))
        assertFalse(Sessions.passwordMatches("", "secret"))
        assertFalse(Sessions.passwordMatches(null, "secret"))
        assertFalse(Sessions.passwordMatches("secret", null))
    }

    @Test
    fun escape() {
        assertEquals("&lt;a href=&quot;x&quot; title=&#39;y&#39;&gt;&amp;&lt;/a&gt;", Html.escape("<a href=\"x\" title='y'>&</a>"))
        assertEquals("", Html.escape(null))
    }

    @Test
    fun pageEscapesOnlyWhenAsked() {
        val page = Page("/de/mel/web/serverparts/test.template.html",
                Replacer("a", "<b>markup</b>"),
                Replacer("b", "<b>text</b>").escapeHtml(),
                Replacer("gone", null))
        assertEquals("<p><b>markup</b></p><p>&lt;b&gt;text&lt;/b&gt;</p><p></p>\n", String(page.bytes))
        assertEquals(ContentType.TEXT, page.contentType)
    }

    @Test
    fun mimeTypes() {
        assertEquals("text/css", MimeTypeReader.forName("/a/b.CSS"))
        assertEquals(ContentType.TEXT, MimeTypeReader.forName("index.html"))
        assertNull(MimeTypeReader.forName("noextension"))
    }
}
