package de.miniserver.http

import com.sun.net.httpserver.HttpsServer
import de.mein.Lok
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mein.serverparts.AbstractHttpsThingy
import de.mein.serverparts.Page
import de.mein.serverparts.Replacer
import de.miniserver.Deploy
import de.miniserver.MiniServer
import de.miniserver.blog.BlogSettings
import de.miniserver.blog.BlogThingy
import de.miniserver.data.FileRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

object ContentType {
    const val SVG = "image/svg+xml"
    const val WEBP = "image/webp"
    const val JPG = "image/jpeg"
}

class HttpsThingy(private val port: Int, private val miniServer: MiniServer, private val fileRepository: FileRepository) : AbstractHttpsThingy(port, miniServer.httpCertificateManager.sslContext) {
    var blogThingy: BlogThingy

    init {
        val blogDir = File(miniServer.workingDirectory, "blog")
        val blogSettings = BlogSettings.loadBlogSettings(blogDir)
        blogThingy = BlogThingy(blogSettings, miniServer.httpCertificateManager.sslContext)
    }

    fun pageHello(): Page {
        return Page("/de/miniserver/index.html",
                Replacer("files") {
                    val s = StringBuilder()
                    miniServer.fileRepository.hashFileMap.values.forEach { fileEntry ->
                        s.append("<tr>")
                        s.append("<td><a href=\"files/${fileEntry.hash}\" download=\"${fileEntry.file.name}\">${fileEntry.file.name}</a></td>") //name
                        s.append("<td>${fileEntry.variant}</td>") //variant
                        s.append("<td>${String.format("%.2f", fileEntry.size.toFloat() / 1024 / 1024)} mb</td>") //size
                        s.append("<td>${Date(fileEntry.version)})</td>") //build date
                        s.append("<td>${fileEntry.hash}</td>")//hash
                        s.append("</tr>")
                    }
                    return@Replacer s.toString()
                })
    }

    fun pageBuild(pw: String): Page {
        return Page("/de/miniserver/private/build.html",
                Replacer("pw", pw),
                Replacer("time", miniServer.startTime.toString()),
                Replacer("lok") {
                    val sb = StringBuilder().append("<p>")
                    Lok.getLines().forEach {
                        sb.append(it).append("<br>")
                    }
                    sb.append("</p>")
                    sb.toString()
                },
                Replacer("keep", if (miniServer.config.keepBinaries) "checked" else ""))
    }

    override fun configureContext(server: HttpsServer) {


        server.createContext("/") {
            if (it.requestURI?.toString() == "/")
                respondPage(it, pageHello())
            else {
                it.close()
            }
        }
        server.createContext("/licences.html") {
            respondText(it, "/de/mein/auth/licences.html")
        }
        server.createContext("/robots.txt") {
            respondText(it, "/de/miniserver/robots.txt", contentType = "text/plain; charset=utf-8")
        }
        server.createContext("/private/loginz.html") {
            respondText(it, "/de/miniserver/private/loginz.html")
        }
        server.createContext("/logandroid.html") {
            respondText(it, "/de/miniserver/logandroid.html")
        }
        server.createContext("/logpc.html") {
            respondText(it, "/de/miniserver/logpc.html")
        }
        server.createContext("/impressum.html") {
            respondText(it, "/de/miniserver/impressum.html")
        }
        server.createContext("/svg/") {
            val uri = it.requestURI
            val fileName = uri.path.substring("/svg/".length, uri.path.length)
            if (fileName.endsWith(".svg"))
                respondText(it, "/de/miniserver/svg/$fileName", contentType = ContentType.SVG)
            else
                respondPage(it, pageHello())
        }
        server.createContext("/webp/") {
            val uri = it.requestURI
            val fileName = uri.path.substring("/webp/".length, uri.path.length)
            if (fileName.endsWith(".webp"))
                respondBinary(it, "/de/miniserver/webp/$fileName", contentType = ContentType.WEBP)
            else
                respondPage(it, pageHello())
        }
        server.createContext("/favicon.png") {
            respondBinary(it, "/de/miniserver/favicon.png")
        }
        server.createContext("/api/") {
            val json = String(it.requestBody.readBytes())
            val buildRequest = SerializableEntityDeserializer.deserialize(json) as BuildRequest
            Lok.info("launching build")
            if (buildRequest.pw == miniServer.secretProperties["buildPassword"] && buildRequest.valid)
                GlobalScope.launch {
                    val deploy = Deploy(miniServer, File(miniServer.secretPropFile.absolutePath), buildRequest)
                    deploy.run()
                }
            else
                Lok.info("build denied")
            it.close()
        }
        server.createContext("/css.css") {

            respondText(it, "/de/miniserver/css.css")
        }
        server.createContext("/private/loggedIn") {
            if (it.requestMethod == "POST") {
                val values = readPostValues(it)
                val pw = values["pw"]
//                Lok.info("##  pw: '$pw', pw is '${miniServer.secretProperties["buildPassword"]}'")
//                val arguments = it.requestURI.path.substring("/private/loggedIn".length).split(" ")
//                val targetPage = values["target"]
                when (pw) {
                    null -> {
//                        Lok.info("## no password")
                        respondPage(it, pageHello())
                    }
                    miniServer.secretProperties["buildPassword"] -> {
//                        Lok.info("## build password OK!")
                        respondPage(it, pageBuild(pw))
                    }
                    else -> {
                        //todo debug
                        Lok.info("## password did not match")
                        respondText(it, "/de/miniserver/private/loginz.html")
//                        respondPage(it, pageHello())
                    }
                }
            } else
                respondText(it, "/de/miniserver/private/loginz.html")
        }
        server.createContext("/build.html") {
            val pw = readPostValues(it)["pw"]
            if (pw != null && pw == miniServer.secretProperties["buildPassword"]) {
                val uri = it.requestURI
                val command = uri.path.substring("/build.html".length, uri.path.length).trim()
                when (command) {
                    "shutDown" -> {
                        respondText(it, "/de/miniserver/farewell.html")
                        miniServer.shutdown()
                    }
                    "reboot" -> {
                        respondText(it, "/de/miniserver/farewell.html")
                        val jarFile = File(miniServer.workingDirectory, "miniserver.jar")
                        miniServer.reboot(miniServer.workingDirectory, jarFile)
                    }
                }
                respondPage(it, pageBuild(pw!!))
            } else {
                respondText(it, "/de/miniserver/private/loginz.html")
            }
        }

        // add files context
        server.createContext("/files/") {
            val uri = it.requestURI
            val hash = uri.path.substring("/files/".length, uri.path.length)
            Lok.info("serving file: $hash")
            try {
                val bytes = miniServer.fileRepository[hash].bytes
                with(it) {
                    sendResponseHeaders(200, bytes!!.size.toLong())
                    responseBody.write(bytes)
                    responseBody.close()
                }
            } catch (e: Exception) {
                Lok.error("did not find a file for ${hash}")
                /**
                 * does not work yet
                 */
                with(it) {
                    val response = "file not found".toByteArray()
                    sendResponseHeaders(404, response.size.toLong())
                    responseBody.write(response)
                    responseBody.close()
                }
            } finally {
                it.close()
            }
        }
        blogThingy.configureContext(server)
    }


    override fun getRunnableName(): String = "HTTP"

}