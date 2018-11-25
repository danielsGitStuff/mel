package de.miniserver.http

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.MeinThread
import de.mein.auth.tools.N
import de.miniserver.MiniServer
import de.miniserver.data.FileRepository
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLContext
import org.slf4j.LoggerFactory
import org.bouncycastle.asn1.ua.DSTU4145NamedCurves.params




class HttpsThingy(private val port: Int, private val miniServer: MiniServer, private val fileRepository: FileRepository) : DeferredRunnable() {
    override fun onShutDown() {

    }

    override fun runImpl() {
        start()
    }

    override fun getRunnableName(): String = "HTTP"


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
    private val executor: Executor = Executors.newFixedThreadPool(2)

    private lateinit var pageBytes: ByteArray

    private fun parseIndexHtml(): ByteArray? {
        val regex = "<\\\$=\\D+[\\w]*\\/>".toRegex()
        val resourceBytes = javaClass.getResourceAsStream("/de/miniserver/index.html").readBytes()
        val html = String(resourceBytes)
        if (!html.contains(regex)) {
            throw Exception("did not find '<$=files/>' tag to replace with file content")
        }
        val s = StringBuilder()
        miniServer.fileRepository.hashFileMap.entries.forEach {
            s.append("<p><a href=\"files/${it.key}\" download=\"${it.value.name}\">${it.value.name}</a> ${it.key}</p>")
        }
        val filesHtml = html.replace(regex, s.toString())
        return filesHtml.toByteArray()
    }

    fun start() {
        val indexBytes = parseIndexHtml()
        Lok.debug("binding http to           : $port")
        server = HttpsServer.create(InetSocketAddress(port), 0)
        val configurator = object: HttpsConfigurator(miniServer.certificateManager.sslContext){
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
        Lok.debug("successfully bound http to: $port")
        // create the index page context
        server.createContext("/") {
            with(it) {
                Lok.debug("sending index to ${remoteAddress}")
                sendResponseHeaders(200, indexBytes!!.size.toLong())
                responseBody.write(indexBytes)
                responseBody.close()
            }
        }
        // add files context
        server.createContext("/files/") {
            val uri = it.requestURI
            val hash = uri.path.substring("/files/".length, uri.path.length)
            Lok.debug("serving file: ${hash}")
            try {
                val bytes = miniServer.fileRepository.getBytes(hash)
                with(it) {
                    sendResponseHeaders(200, bytes.size.toLong())
                    responseBody.write(bytes)
                    responseBody.close()
                }
            } catch (e: Exception) {
                Lok.debug("did not find a file for ${hash}")
                /**
                 * does not work yet
                 */
                with(it) {
                    val response = "file not found".toByteArray()
                    sendResponseHeaders(404, response.size.toLong())
                    responseBody.write(response)
                    responseBody.close()
                }
            }

        }
        server.executor = executor
        server.start()
        Lok.debug("http is up")
        N.r {


        }
    }


}