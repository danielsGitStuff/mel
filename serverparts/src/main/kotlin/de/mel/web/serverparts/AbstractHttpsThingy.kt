package de.mel.web.serverparts

import com.sun.net.httpserver.*
import de.mel.DeferredRunnable
import de.mel.Lok
import de.mel.MelThread
import de.mel.auth.data.access.CertificateManager
import org.jdeferred.Promise

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLContext

object ContentType {
    const val TEXT = "text/html; charset=UTF-8"
    const val SVG = "image/svg+xml"
    const val WEBP = "image/webp"
    const val JPG = "image/jpeg"
    const val PNG = "image/png"
}

abstract class AbstractHttpsThingy(private val port: Int, val sslContext: SSLContext?) : DeferredRunnable() {
    override fun onShutDown(): Promise<Void, Void, Void>? = null

    override fun runImpl() {
        start()
    }

    fun readGetQuery(query: String?): Map<String, String> {
        if (query == null)
            return mutableMapOf()
        val map = mutableMapOf<String, String>()
        var isKey = true
        var key: String? = null
        query.split("[=&]".toRegex()).forEach {
            if (isKey)
                key = it
            else {
                map[key!!] = it
            }
            isKey = !isKey
        }
        return map

    }

//    /**
//     * closes HttpExchange when failing or done
//     */
//    fun createServerContext(context: String, runnable: (HttpExchange) -> Unit) {
//        server.createContext(context, HttpHandler {
//            try {
//                runnable.invoke(it)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                respondError(it, e.cause?.toString())
//            } finally {
//                it.close()
//            }
//        })
//    }

    fun respondError(ex: HttpExchange, cause: String?) {
        try {
            with(ex) {
                val page = Page("/de/web/blog/error.html",
                        Replacer("cause", if (cause != null) cause else "something went wrong"))
                de.mel.Lok.debug("sending error to $remoteAddress")
                sendResponseHeaders(400, page.bytes.size.toLong())
                responseBody.write(page.bytes)
                responseBody.close()
            }
        } finally {
            ex.close()
        }
    }


    private lateinit var serverSocket: ServerSocket
    private val threadSemaphore = Semaphore(1, true)
    private val threadQueue = LinkedList<MelThread>()

    lateinit var server: HttpServer
    private val threadFactory = { r: Runnable ->
        var melThread: MelThread? = null

        try {
            threadSemaphore.acquire()
            melThread = threadQueue.poll()
            threadSemaphore.release()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        melThread
    }
    open val executor = Executors.newCachedThreadPool() {
        val thread = Thread(it)
        thread.name = "a HTTPS-Thread"
        thread
    }


    fun readPostValues(ex: HttpExchange, size: Int = 400): HashMap<String, String> {
        val map = hashMapOf<String, String>()
        val text = ex.requestBody.reader().readText()
        text.split("&").map { URLDecoder.decode(it) }.forEach {
            val splitIndex = it.indexOf("=")
            val k = it.substring(0, splitIndex)
            val v = it.substring(splitIndex + 1, it.length)
            map[k] = v
        }
        return map
    }

    fun respondBinary(ex: HttpExchange, path: String, contentType: String? = null, cache: Boolean = false) {
        with(ex) {
            //            de.mel.Lok.debug("sending $path to $remoteAddress")
            var page: Page? = null
            try {
                if (contentType != null)
                    responseHeaders.add("Content-Type", contentType)
                page = if (Page.staticPagesCache[path] == null) {
                    val bytes = this@AbstractHttpsThingy.javaClass.getResourceAsStream(path).readBytes()
                    Page(path, bytes, cache = cache)
                } else {
                    Page.staticPagesCache[path]!!
                }
            } catch (e: Exception) {
                Lok.error("KKKKK")
            } finally {
                if (page != null) {
                    sendResponseHeaders(200, page.bytes.size.toLong())
                    responseBody.write(page.bytes)
                }
                responseBody.close()
                close()
            }
        }
    }


    fun respondText(ex: HttpExchange, path: String, contentType: String? = null, vararg replacers: Replacer) {
        with(ex) {
            //            de.mel.Lok.debug("sending $path to $remoteAddress")
            var page: Page? = null
            try {
                if (contentType != null) {
                    responseHeaders.add("Content-Type", contentType)
                }
                page = Page(path, *replacers)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            } finally {
                if (page != null) {
                    sendResponseHeaders(200, page.bytes.size.toLong())
                    responseBody.write(page.bytes)
                }
                responseBody.close()
                ex.close()
            }
        }
    }

    fun start() {
        if (sslContext == null) {
            Lok.info("binding http to           : $port")
            server = createServer()
            Lok.info("successfully bound http to: $port")
        } else {
            Lok.info("binding https to           : $port")
            server = createSecureServer()
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

    fun redirect(httpExchange: HttpExchange, targetUrl: String): Unit {
        try {
            with(httpExchange) {
                Lok.debug("redirect to $targetUrl")
                responseHeaders.add("Location", targetUrl)
                sendResponseHeaders(302, 0)
            }
        } finally {
            httpExchange.close()
        }
    }

    open fun respondPage(ex: HttpExchange, page: Page?) {
        if (page != null)
            try {
                with(ex) {
                    //                    Lok.debug("sending '${page?.path}' to $remoteAddress")
                    responseHeaders.add("Content-Type", page.contentType)
                    sendResponseHeaders(200, page?.bytes?.size?.toLong() ?: "404".toByteArray().size.toLong())
                    responseBody.write(page?.bytes ?: "404".toByteArray())
                    responseBody.close()
                }
            } finally {
                ex.close()
            }
    }

    private fun createServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        return server
    }

    private fun createSecureServer(): HttpsServer {
        val server = HttpsServer.create(InetSocketAddress(port), 0)
        val configurator = object : HttpsConfigurator(sslContext) {
            override fun configure(params: HttpsParameters) {
                try {
                    // initialise the SSL context
                    val engine = sslContext.createSSLEngine()
                    params.needClientAuth = false
                    params.cipherSuites = engine.enabledCipherSuites
                    params.protocols = engine.enabledProtocols

                    // get the default parameters
                    val sslParameters = sslContext.supportedSSLParameters
                    sslParameters.cipherSuites = CertificateManager.filterCipherSuites(sslParameters.cipherSuites)
                    sslParameters.protocols = arrayOf("TLSv1.2")
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

    override fun stop() {
        server.stop(0)
        super.stop()
    }


}