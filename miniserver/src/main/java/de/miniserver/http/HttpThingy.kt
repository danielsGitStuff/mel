package de.miniserver.http

import com.sun.net.httpserver.HttpServer
import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.MeinThread
import de.mein.auth.tools.N
import de.miniserver.MiniServer
import de.miniserver.data.FileRepository
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore


class HttpThingy(private val port: Int, private val miniServer: MiniServer, private val fileRepository: FileRepository) : DeferredRunnable() {
    override fun onShutDown() {

    }

    override fun runImpl() {
        start()
    }

    override fun getRunnableName(): String = "HTTP"


    private lateinit var serverSocket: ServerSocket
    private val threadSemaphore = Semaphore(1, true)
    private val threadQueue = LinkedList<MeinThread>()

    private lateinit var server: HttpServer
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
        val url = javaClass.getResource("/de/mein/miniserver/index.html")
        val uuu = File(url.file)
        if (uuu.exists()) {
            val html = uuu.readText()
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
        throw Exception("did not find index file")
    }

    fun start() {
        val indexBytes = parseIndexHtml()
        server = HttpServer.create(InetSocketAddress(8080), 0)
        // create the index page context
        server.createContext("/") {
            with(it) {
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
                val file = miniServer.fileRepository.getFile(hash)
                with(it) {
                    sendResponseHeaders(200, file.length())
                    val bytes = file.inputStream().readBytes()
                    responseBody.write(bytes)
                    responseBody.close()
                }
            } catch (e: Exception) {
                /**
                 * does not work yet
                 */
                with(it){
                    val response = "file not found".toByteArray()
                    sendResponseHeaders(404,response.size.toLong())
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