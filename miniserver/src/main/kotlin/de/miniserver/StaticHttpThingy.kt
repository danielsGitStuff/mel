package de.miniserver

import com.sun.net.httpserver.HttpServer
import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.MeinThread
import de.miniserver.MiniServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore


class StaticHttpThingy(private val port: Int) : DeferredRunnable() {
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


    fun start() {
        Lok.info("binding http to           : $port")
        server = HttpServer.create(InetSocketAddress(port), 0)
        Lok.info("successfully bound http to: $port")
        // create the index page context
        server.createContext("/") {
            val enteredHost = it.requestHeaders.getFirst("Host")
            it.responseHeaders.add("Location", "https://$enteredHost")
            with(it) {
                Lok.info("sending https redirect to $remoteAddress")
//                val content = "???"
                sendResponseHeaders(301, -1)
//                responseBody.write(content.toByteArray())
                responseBody.close()
            }
        }

        server.executor = executor
        server.start()
        Lok.info("http is up")
    }

    override fun stop() {
        server.stop(0)
        super.stop()
    }

}