package de.mein.serverparts

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.MeinThread

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLContext

object ContentType {
    const val SVG = "image/svg+xml"
}

abstract class AbstractHttpsThingy(private val port: Int, val sslContext: SSLContext) : DeferredRunnable() {
    override fun onShutDown() {

    }

    override fun runImpl() {
        start()
    }


    private lateinit var serverSocket: ServerSocket
    private val threadSemaphore = Semaphore(1, true)
    private val threadQueue = LinkedList<MeinThread>()

    private lateinit var server: HttpsServer
    private val threadFactory = { r: Runnable ->
        var meinThread: MeinThread? = null

        try {
            threadSemaphore.acquire()
            meinThread = threadQueue.poll()
            threadSemaphore.release()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        meinThread
    }
    private val executor = Executors.newFixedThreadPool(2)

    fun readPostValues(ex: HttpExchange, size: Int = 400): HashMap<String, String> {
        val map = hashMapOf<String, String>()
        var bytes = ByteArray(size)
        val read = ex.requestBody.read(bytes)
        if (read < 0)
            return map
        bytes = ByteArray(read) { i -> bytes[i] }
        val string = String(bytes)
        var prev: String? = null
        string.split("&").map { URLDecoder.decode(it) }.forEach {
            val splitIndex = it.indexOf("=")
            val k = it.substring(0, splitIndex)
            val v = it.substring(splitIndex + 1, it.length)
            map[k] = v
        }

        return map
    }

    fun respondBinary(ex: HttpExchange, path: String, contentType: String? = null, cache: Boolean = false) {
        with(ex) {
            de.mein.Lok.debug("sending $path to $remoteAddress")
            if (contentType != null)
                responseHeaders.add("Content-Type", contentType)
            val page: Page
            page = if (Page.pageRepo[path] == null) {
                val bytes = javaClass.getResourceAsStream(path).readBytes()
                Page(path, bytes, cache = cache)
            } else {
                Page.pageRepo[path]!!
            }
            sendResponseHeaders(200, page.bytes.size.toLong())
            responseBody.write(page.bytes)
            responseBody.close()
            responseHeaders
        }
    }


    fun respondText(ex: HttpExchange, path: String, contentType: String? = null, vararg replacers: Replacer) {
        with(ex) {
            de.mein.Lok.debug("sending $path to $remoteAddress")
            if (contentType != null) {
                responseHeaders.add("Content-Type", contentType)
            }
            val page = Page(path, *replacers)
            sendResponseHeaders(200, page.bytes.size.toLong())
            responseBody.write(page.bytes)
            responseBody.close()
            responseHeaders
        }
    }

    fun start() {
        Lok.debug("binding https to           : $port")
        server = createServer()
        Lok.debug("successfully bound https to: $port")
        configureContext(server)
        server.executor = executor
        server.start()
        Lok.debug("http is up")
    }

    abstract fun configureContext(server: HttpsServer)

    fun respondPage(ex: HttpExchange, page: Page?) {
        with(ex) {
            Lok.debug("sending '${page?.path}' to $remoteAddress")
                responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
            sendResponseHeaders(200, page?.bytes?.size?.toLong() ?: "404".toByteArray().size.toLong())
            responseBody.write(page?.bytes ?: "404".toByteArray())
            responseBody.close()
            responseHeaders
        }
    }

    private fun createServer(): HttpsServer {
        val server = HttpsServer.create(InetSocketAddress(port), 0)
        val configurator = object : HttpsConfigurator(sslContext) {
            override fun configure(params: HttpsParameters) {
                try {
                    // initialise the SSL context
                    val c = SSLContext.getDefault()
                    val engine = c.createSSLEngine()
                    params.needClientAuth = false
                    params.cipherSuites = engine.enabledCipherSuites
                    params.protocols = engine.enabledProtocols

                    // get the default parameters
                    val defaultSSLParameters = c.defaultSSLParameters
                    params.setSSLParameters(defaultSSLParameters)
                } catch (ex: Exception) {
                    Lok.error(ex)
                    Lok.error("Failed to create HTTPS port")
                }

            }
        }
        server.httpsConfigurator = configurator
        return server
    }

    fun stop() {
        server.stop(0)
    }


}