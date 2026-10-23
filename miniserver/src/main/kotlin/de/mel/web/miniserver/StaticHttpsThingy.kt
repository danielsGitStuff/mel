package de.mel.web.miniserver

import com.sun.net.httpserver.HttpServer
import de.mel.Lok
import de.mel.web.serverparts.AbstractHttpsThingy
import de.mel.web.serverparts.Page
import java.io.File

/**
 * publishes every file below [StaticConfig.staticPath] at its path relative to it
 */
class StaticHttpsThingy(port: Int, private val staticServer: StaticServer) : AbstractHttpsThingy(port, staticServer.httpCertificateManager.sslContext) {

    override fun configureContext(server: HttpServer) {
        val root = File(staticServer.config.staticPath)
        val rootLength = root.absolutePath.length
        Lok.info(root.absolutePath)
        root.walkTopDown().filter { it.isFile && it.extension.isNotEmpty() }.forEach { file ->
            val path = file.absolutePath.substring(rootLength, file.absolutePath.length)
            val page = Page(path, file)
            server.createContext(path) {
                respondPage(it, page)
            }
            if (path == "/config.json")
                server.createContext("/config.localhost.json") {
                    respondPage(it, page)
                }
        }
        // "/" gets every request no other context takes, so it may only answer with the index for "/" itself
        val index = File(root, "index.html")
        server.createContext("/") {
            if (it.requestURI.path == "/" && index.isFile)
                respondPage(it, Page("/index.html", index))
            else
                respondNotFound(it)
        }
    }

    override fun getRunnableName(): String = "static HTTPS"
}
