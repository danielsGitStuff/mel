package de.mel.web.miniserver.http

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsServer
import de.mel.Lok
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mel.web.serverparts.visits.Visitors
import de.mel.web.miniserver.Deploy
import de.mel.web.miniserver.MiniServer
import de.mel.web.blog.BlogSettings
import de.mel.web.blog.BlogThingy
import de.mel.web.miniserver.data.FileRepository
import de.mel.web.serverparts.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


class HttpsThingy(private val port: Int, private val miniServer: MiniServer, private val fileRepository: FileRepository) : AbstractHttpsThingy(port, miniServer.httpCertificateManager.sslContext) {
    var blogThingy: BlogThingy
    var visitors: Visitors
    private val sessions = Sessions("session", secure = sslContext != null)

    init {
        val blogDir = File(miniServer.workingDirectory, "blog")
        val blogSettings = BlogSettings.loadBlogSettings(blogDir)
        blogThingy = BlogThingy(blogSettings, miniServer.httpCertificateManager.sslContext)
        visitors = Visitors.fromDbFile(File(miniServer.workingDirectory, "visitors.db"))
    }

    companion object {
        const val PARAM_PW = "pw"
        const val LOGIN = "/private/loginz.html"
    }

    private val buildPassword: String?
        get() = miniServer.secretProperties["buildPassword"] as String?

    fun pageHello(): Page {
        return Page("/de/mel/web/miniserver/index.html",
                Replacer("files") {
                    val s = StringBuilder()
                    miniServer.fileRepository.sortedFileEntries.forEach { fileEntry ->
                        val name = Html.escape(fileEntry.file.name)
                        s.append("<tr>")
                        s.append("<td><a href=\"files/${Html.escape(fileEntry.hash)}\" download=\"$name\">$name</a></td>") //name
                        if (fileEntry.mirrors.isNotEmpty()) {
                            s.append("<td><a href=\"${Html.escape(fileEntry.mirrors[0])}\" download=\"$name\">Download</a></td>")
                        } else {
                            s.append("<td></td>")
                        }
                        s.append("<td>${Html.escape(fileEntry.variant)}</td>") //variant
                        s.append("<td>${String.format("%.2f", fileEntry.size.toFloat() / 1024 / 1024)} mb</td>") //size
                        s.append("<td>${Html.escape(fileEntry.version)}</td>") //build date
                        s.append("<td>${Html.escape(fileEntry.commit)}</td>")
                        s.append("<td>${Html.escape(fileEntry.hash)}</td>")//hash
                        s.append("</tr>")
                    }
                    return@Replacer s.toString()
                })
    }

    fun pageBuild(): Page {
        return Page("/de/mel/web/miniserver/private/build.html",
                Replacer("time", miniServer.startTime.toString()),
                Replacer("lok") {
                    val sb = StringBuilder().append("<p>")
                    // log lines contain request URLs: anyone can put markup in there
                    Lok.getLines().forEach {
                        sb.append(Html.escape(it)).append("<br>")
                    }
                    sb.append("</p>")
                    sb.toString()
                },
                Replacer("keep", if (miniServer.config.keepBinaries) "checked" else ""))
    }

    override fun configureContext(server: HttpServer) {
        val contextCreator = HttpContextCreator(server)
        contextCreator.createContext("/")
                .withGET()
                .handle { it, queryMap ->
                    visitors.count(it)
                    respondPage(it, pageHello())
                }
        contextCreator.createContext("/licences.html")
                .withGET()
                .handle { it, queryMap ->
                    respondText(it, "/de/mel/auth/licences.html")
                }
        contextCreator.createContext("/robots.txt")
                .withGET()
                .handle { it, queryMap ->
                    respondText(it, "/de/mel/web/miniserver/robots.txt", contentType = "text/plain; charset=utf-8")
                }
        contextCreator.createContext("/private/loginz.html")
                .withGET()
                .handle { it, queryMap ->
                    respondText(it, "/de/mel/web/miniserver/private/loginz.html")
                }
        contextCreator.createContext("/logandroid.html")
                .withGET()
                .handle { it, queryMap ->
                    respondText(it, "/de/mel/web/miniserver/logandroid.html")
                }
        contextCreator.createContext("/logpc.html")
                .withGET()
                .handle { it, queryMap ->
                    respondText(it, "/de/mel/web/miniserver/logpc.html")
                }
        contextCreator.createContext("/impressum.html")
                .withGET()
                .handle { it, queryMap ->
                    respondText(it, "/de/mel/web/miniserver/impressum.html")
                }
        contextCreator.createContext("/svg/")
                .withGET()
                .handle { it, queryMap ->
                    val uri = it.requestURI
                    val fileName = uri.path.substring("/svg/".length, uri.path.length)
                    if (fileName.endsWith(".svg"))
                        respondText(it, "/de/mel/web/miniserver/svg/$fileName", contentType = ContentType.SVG)
                    else
                        respondPage(it, pageHello())
                }
        contextCreator.createContext("/webp/")
                .withGET()
                .handle { it, queryMap ->
                    val uri = it.requestURI
                    val fileName = uri.path.substring("/webp/".length, uri.path.length)
                    if (fileName.endsWith(".webp"))
                        respondBinary(it, "/de/mel/web/miniserver/webp/$fileName", contentType = ContentType.WEBP)
                    else
                        respondPage(it, pageHello())
                }
        contextCreator.createContext("/favicon.png")
                .withGET()
                .handle { it, queryMap ->
                    respondBinary(it, "/de/mel/web/miniserver/favicon.png")
                }
        contextCreator.createContext("/api/")
                .withPOST()
                .handle { it, queryMap ->
                    if (!sessions.isValid(it)) {
                        Lok.info("build denied")
                        respond(it, 403, "log in first".toByteArray(), ContentType.PLAIN)
                        return@handle
                    }
                    val json = queryMap.requestBody
                    val buildRequest = SerializableEntityDeserializer.deserialize(json) as BuildRequest
                    if (!buildRequest.valid) {
                        respond(it, 400, "incomplete build request".toByteArray(), ContentType.PLAIN)
                        return@handle
                    }
                    Lok.info("launching build")
                    GlobalScope.launch {
                        val deploy = Deploy(miniServer, File(miniServer.secretPropFile.absolutePath), buildRequest)
                        deploy.run()
                    }
                    respond(it, 202, "build started".toByteArray(), ContentType.PLAIN)
                }
        contextCreator.createContext("/css.css")
                .withGET()
                .handle { it, queryMap ->
                    respondText(it, "/de/mel/web/miniserver/css.css")
                }
        contextCreator.createContext("/private/loggedIn")
                .withPOST()
                .expect(PARAM_PW) { pw -> Sessions.passwordMatches(pw, buildPassword) }
                .handle { it, queryMap ->
                    sessions.start(it)
                    redirect(it, "/build.html")
                }
                .withPOST()
                .expect(PARAM_PW) { pw -> pw != null }
                .handle { it, queryMap ->
                    Lok.info("## password did not match")
                    redirect(it, LOGIN)
                }
                .withPOST()
                .handle { it, queryMap -> respondPage(it, pageHello()) }
                .withGET()
                .handle { it, queryMap ->
                    redirect(it, if (sessions.isValid(it)) "/build.html" else LOGIN)
                }
        contextCreator.createContext("/build.html")
                .withGET()
                .handle { it, queryMap ->
                    if (sessions.isValid(it))
                        respondPage(it, pageBuild())
                    else
                        redirect(it, LOGIN)
                }
                .withPOST()
                .handle { it, queryMap ->
                    if (!sessions.isValid(it)) {
                        redirect(it, LOGIN)
                        return@handle
                    }
                    val uri = it.requestURI
                    val command = uri.path.substring("/build.html".length, uri.path.length).trim()
                    when (command) {
                        "shutDown" -> {
                            respondText(it, "/de/mel/web/miniserver/farewell.html")
                            miniServer.shutdown()
                        }
                        "reboot" -> {
                            respondText(it, "/de/mel/web/miniserver/farewell.html")
                            val jarFile = File(miniServer.workingDirectory, "miniserver.jar")
                            miniServer.reboot(miniServer.workingDirectory, jarFile)
                        }
                        else -> redirect(it, "/build.html")
                    }
                }
        // add files context
        contextCreator.createContext("/files/")
                .withGET()
                .dontCloseExchange()
                .handle { it, queryMap ->
                    val uri = it.requestURI
                    val hash = uri.path.substring("/files/".length, uri.path.length)
                    Lok.info("serving file: $hash")
                    visitors.count(it)
                    GlobalScope.launch {
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
                }
        blogThingy.configureContext(server)
//        val buildRequest = BuildRequest()
//        buildRequest.apk = false
//        buildRequest.blog = false
//        buildRequest.jar = false
//        buildRequest.server = false
//        buildRequest.keepBinaries = true
//        val deploy = Deploy(miniServer, File(miniServer.secretPropFile.absolutePath), buildRequest)
//        deploy.run()
    }


    override fun getRunnableName(): String = "HTTP"

}