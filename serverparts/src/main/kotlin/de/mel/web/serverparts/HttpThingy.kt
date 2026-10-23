package de.mel.web.serverparts

import com.sun.net.httpserver.HttpServer
import de.mel.DeferredRunnable
import de.mel.Lok
import `fun`.with.promise.Promise
import java.net.InetSocketAddress
import java.util.concurrent.Executor
import java.util.concurrent.Executors


class HttpThingy(private val port: Int, val redirectPort: Int?) : DeferredRunnable() {
    override fun onShutDown(): Promise<Void, Void>? = null

    override fun runImpl() {
        start()
    }

    override fun getRunnableName(): String = "HTTP"


    private lateinit var server: HttpServer
    private val executor: Executor = Executors.newFixedThreadPool(2)


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
        if (::server.isInitialized)
            server.stop(0)
        super.stop()
    }

}