package de.mel.web.serverparts

import com.sun.net.httpserver.*
import de.mel.DeferredRunnable
import de.mel.Lok
import de.mel.auth.data.access.CertificateManager
import `fun`.with.promise.Promise

import java.io.FileNotFoundException
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext

object ContentType {
    const val TEXT = "text/html; charset=UTF-8"
    const val PLAIN = "text/plain; charset=UTF-8"
    const val SVG = "image/svg+xml"
    const val WEBP = "image/webp"
    const val JPG = "image/jpeg"
    const val PNG = "image/png"
}

abstract class AbstractHttpsThingy(private val port: Int, val sslContext: SSLContext?) : DeferredRunnable() {
    override fun onShutDown(): Promise<Void, Void>? = null

    override fun runImpl() {
        start()
    }

    /**
     * a template with a <§=cause/> tag that [respondError] fills. Without one the cause is sent as plain text.
     */
    protected open val errorPagePath: String? = null

    fun readGetQuery(query: String?): Map<String, String> = QueryMap.parse(query)

    fun readPostValues(ex: HttpExchange): Map<String, String> = QueryMap.parse(ex.requestBody.reader().readText())

    /**
     * sends [bytes] and closes the exchange, whatever happens
     */
    fun respond(ex: HttpExchange, status: Int, bytes: ByteArray, contentType: String? = null) {
        try {
            if (contentType != null)
                ex.responseHeaders.set("Content-Type", contentType)
            // -1: there is no body
            ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty())
                ex.responseBody.write(bytes)
        } finally {
            ex.close()
        }
    }

    fun respondError(ex: HttpExchange, cause: String?, status: Int = 400) {
        val text = cause ?: "something went wrong"
        Lok.debug("sending error $status to ${ex.remoteAddress}")
        val page = errorPagePath?.let {
            try {
                Page(it, Replacer("cause", text))
            } catch (e: Exception) {
                Lok.error("error page $it failed: $e")
                null
            }
        }
        if (page == null)
            respond(ex, status, text.toByteArray(), ContentType.PLAIN)
        else
            respond(ex, status, page.bytes, page.contentType)
    }

    fun respondNotFound(ex: HttpExchange) {
        respond(ex, 404, "not found".toByteArray(), ContentType.PLAIN)
    }

    open val executor = Executors.newCachedThreadPool() {
        val thread = Thread(it)
        thread.name = "a HTTPS-Thread"
        thread
    }

    lateinit var server: HttpServer

    fun respondBinary(ex: HttpExchange, path: String, contentType: String? = null, cache: Boolean = false) {
        //            de.mel.Lok.debug("sending $path to $remoteAddress")
        val page = Page.staticPagesCache[path] ?: run {
            val bytes = javaClass.getResourceAsStream(path)?.use { it.readBytes() }
            if (bytes == null) {
                respondNotFound(ex)
                return
            }
            Page(path, bytes, cache = cache)
        }
        respond(ex, 200, page.bytes, contentType)
    }


    fun respondText(ex: HttpExchange, path: String, contentType: String? = null, vararg replacers: Replacer) {
        //            de.mel.Lok.debug("sending $path to $remoteAddress")
        val page = try {
            Page(path, *replacers)
        } catch (e: FileNotFoundException) {
            respondNotFound(ex)
            return
        }
        respond(ex, 200, page.bytes, contentType ?: page.contentType)
    }

    fun start() {
        if (sslContext == null) {
            Lok.info("binding http to           : $port")
            server = createServer()
            Lok.info("successfully bound http to: $port")
        } else {
            Lok.info("binding https to           : $port")
            server = createSecureServer(sslContext)
            Lok.info("successfully bound https to: $port")
        }
        configureContext(server)
        server.executor = executor
        server.start()
        if (sslContext == null)
            Lok.info("http is up on port $port")
        else
            Lok.info("https is up on port $port")
    }


    abstract fun configureContext(server: HttpServer)

    fun redirect(httpExchange: HttpExchange, targetUrl: String) {
        Lok.debug("redirect to $targetUrl")
        httpExchange.responseHeaders.set("Location", targetUrl)
        respond(httpExchange, 302, ByteArray(0))
    }

    open fun respondPage(ex: HttpExchange, page: Page?) {
        //                    Lok.debug("sending '${page?.path}' to $remoteAddress")
        if (page == null)
            respondNotFound(ex)
        else
            respond(ex, 200, page.bytes, page.contentType)
    }

    private fun createServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        return server
    }

    private fun createSecureServer(sslContext: SSLContext): HttpsServer {
        val server = HttpsServer.create(InetSocketAddress(port), 0)
        val configurator = object : HttpsConfigurator(sslContext) {
            override fun configure(params: HttpsParameters) {
                try {
                    val sslParameters = sslContext.supportedSSLParameters
                    sslParameters.cipherSuites = cipherSuites(sslParameters.cipherSuites)
                    sslParameters.protocols = sslParameters.protocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
                    params.setSSLParameters(sslParameters)
                } catch (ex: Exception) {
                    Lok.error(ex)
                    Lok.error("Failed to create HTTPS port")
                }
            }
        }
        server.httpsConfigurator = configurator
        return server
    }

    /**
     * the TLS 1.3 suites plus the ones Mel's own sockets use for TLS 1.2
     */
    private fun cipherSuites(supported: Array<String>): Array<String> {
        val tls13 = listOf("TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256", "TLS_AES_128_GCM_SHA256")
        return (supported.filter { it in tls13 } + CertificateManager.filterCipherSuites(supported)).toTypedArray()
    }

    override fun stop() {
        if (::server.isInitialized)
            server.stop(0)
        super.stop()
    }


}
