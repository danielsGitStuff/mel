package de.mel.web.serverparts

import com.sun.net.httpserver.HttpServer
import de.mel.DeferredRunnable
import de.mel.Lok
import de.mel.MelThread
import org.jdeferred.Promise
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore


class HttpThingy(private val port: Int, val redirectPort: Int?) : DeferredRunnable() {
    override fun onShutDown(): Promise<Void, Void, Void>? = null

    override fun runImpl() {
        start()
    }

    override fun getRunnableName(): String = "HTTP"


    private lateinit var serverSocket: ServerSocket
    private val threadSemaphore = Semaphore(1, true)
    private val threadQueue = LinkedList<MelThread>()

    private lateinit var server: HttpServer
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
    private val executor: Executor = Executors.newFixedThreadPool(2)

    private lateinit var pageBytes: ByteArray


    fun start() {
        Lok.debug("binding http to           : $port")
        server = HttpServer.create(InetSocketAddress(port), 0)
        Lok.debug("successfully bound http to: $port")
        // create the index page context
        server.createContext("/") {
            var enteredHost = it.requestHeaders.getFirst("Host")
            if (enteredHost.contains(':'))
                enteredHost = enteredHost.split(':')[0]
            val redirectUrl = "https://$enteredHost" + (if (redirectPort == null) "" else ":$redirectPort")
            it.responseHeaders.add("Location", redirectUrl)
            with(it) {
                Lok.debug("sending https redirect to $remoteAddress")
//                val content = "???"
                sendResponseHeaders(302, 0)
//                responseBody.write(content.toByteArray())
                responseBody.close()
            }
        }
        server.executor = executor
        server.start()
        Lok.debug("http is up")
    }

    override fun stop() {
        server.stop(0)
        super.stop()
    }

}