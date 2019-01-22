package de.miniserver

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import de.mein.Lok
import de.miniserver.http.Page
import de.miniserver.http.Replacer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext

class StaticHttpsThingy(private val port: Int, private val staticServer: StaticServer) {
    private lateinit var server: HttpsServer
    private val executor = Executors.newFixedThreadPool(2)

    private fun createServer(): HttpsServer {
        val server = HttpsServer.create(InetSocketAddress(port), 0)
        val configurator = object : HttpsConfigurator(staticServer.httpCertificateManager.sslContext) {
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

    private fun respondPage(ex: HttpExchange, page: StaticPage?) {
        with(ex) {
            Lok.debug("sending '${page?.path}' to $remoteAddress")
            responseHeaders.add("Content-Type", page?.contentType)
            sendResponseHeaders(200, page?.bytes?.size?.toLong() ?: "404".toByteArray().size.toLong())
            responseBody.write(page?.bytes ?: "404".toByteArray())
            responseBody.close()
            responseHeaders
        }
    }

    fun start() {


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



        Lok.debug("binding https to           : $port")
        server = createServer()
        Lok.debug("successfully bound https to: $port")
        server.createContext("/") {
        }

        val root = File(staticServer.config.staticPath)
        val rootLength = root.absolutePath.length
        Lok.debug(root.absolutePath)
        val pathList = mutableListOf<File>()
//        root.listFiles({ dir, name ->  true}).forEach { Lok.debug(it.absolutePath) }
        root.walkTopDown().filter { it.isFile && it.extension.isNotEmpty() }.forEach { file ->
            val path = file.absolutePath.substring(rootLength, file.absolutePath.length)
            val staticPage = StaticPage(file.absolutePath)
            server.createContext(path) {
                respondPage(it, staticPage)
            }

            if (path == "/index.html")
                server.createContext("/"){
                    respondPage(it,staticPage)
                }
            if(path == "/config.json"){
                Lok.error("duashdashdosa")
                server.createContext("/config.localhost.json"){
                    respondPage(it,staticPage)
                }
            }
        }


        server.executor = executor
        server.start()
        Lok.debug("https is up")
    }
}