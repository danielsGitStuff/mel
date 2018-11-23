package de.miniserver.http

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress


fun main(args: Array<String>) {
    val server = HttpServer.create(InetSocketAddress(8081), 0)
    val context = server.createContext("/")
    context.setHandler {
        val response = "Hi there!"
        it.sendResponseHeaders(200, response.toByteArray().size.toLong())//response code and length
        val os = it.getResponseBody()
        os.write(response.toByteArray())
        os.close()
    }
    server.start()
}