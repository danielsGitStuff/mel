package de.mel.location.http

import de.mel.Lok
import de.mel.location.data.LocationSettings
import de.mel.location.data.db.LocationPoint
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class OwnTracksHttpServer(
    private val settings: LocationSettings,
    private val onLocation: (LocationPoint) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val addr = InetAddress.getByName(settings.bindAddress.ifBlank { "0.0.0.0" })
        val ss = ServerSocket(settings.port, 50, addr)
        serverSocket = ss
        Lok.info("OwnTracks HTTP listening on ${addr.hostAddress}:${ss.localPort}")
        Thread({
            while (running.get() && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    pool.submit { handleClient(client) }
                } catch (e: Exception) {
                    if (running.get()) Lok.warn("OwnTracks accept error: ${e.message}")
                }
            }
        }, "owntracks-http-accept").apply { isDaemon = true }.start()
    }

    fun actualPort(): Int = serverSocket?.localPort ?: settings.port

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        pool.shutdownNow()
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 10_000
            val input = BufferedInputStream(s.getInputStream())
            val headerBytes = readUntilHeadersEnd(input) ?: return
            val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1.name())
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty()
            val method = requestLine.substringBefore(' ', "")
            if (method != "POST") {
                sendResponse(s, 405, "Method Not Allowed", "[]")
                return
            }

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val idx = lines[i].indexOf(':')
                if (idx > 0) {
                    headers[lines[i].substring(0, idx).trim().lowercase()] = lines[i].substring(idx + 1).trim()
                }
            }

            if (settings.hasAuth() && !checkBasicAuth(headers["authorization"])) {
                sendResponse(s, 401, "Unauthorized", "[]", extraHeader = "WWW-Authenticate: Basic realm=\"OwnTracks\"\r\n")
                return
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength <= 0 || contentLength > 1_000_000) {
                sendResponse(s, 400, "Bad Request", "[]")
                return
            }

            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(body, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            val jsonStr = String(body, 0, read, StandardCharsets.UTF_8)
            val status = processOwnTracksJson(jsonStr)
            if (status) {
                sendResponse(s, 200, "OK", "[]")
            } else {
                sendResponse(s, 400, "Bad Request", "[]")
            }
        }
    }

    internal fun processOwnTracksJson(jsonStr: String): Boolean {
        return try {
            val obj = JSONObject(jsonStr)
            val type = obj.optString("_type", "")
            if (type == "location") {
                if (!obj.has("lat") || !obj.has("lon") || !obj.has("tst")) return false
                val point = LocationPoint()
                point.tid.v(obj.optString("tid", obj.optString("topic", "unknown")))
                point.tst.v(obj.getLong("tst"))
                point.lat.v(obj.getDouble("lat"))
                point.lon.v(obj.getDouble("lon"))
                if (obj.has("alt")) point.alt.v(obj.getInt("alt"))
                if (obj.has("acc")) point.acc.v(obj.getInt("acc"))
                if (obj.has("vel")) point.vel.v(obj.getInt("vel"))
                if (obj.has("batt")) point.batt.v(obj.getInt("batt"))
                point.rawJson.v(jsonStr)
                onLocation(point)
            }
            true
        } catch (e: Exception) {
            Lok.warn("Invalid OwnTracks JSON: ${e.message}")
            false
        }
    }

    private fun checkBasicAuth(authHeader: String?): Boolean {
        if (authHeader == null || !authHeader.startsWith("Basic ", ignoreCase = true)) return false
        return try {
            val decoded = String(Base64.getDecoder().decode(authHeader.substring(6).trim()), StandardCharsets.UTF_8)
            val expected = "${settings.username}:${settings.password}"
            decoded == expected
        } catch (_: Exception) {
            false
        }
    }

    private fun readUntilHeadersEnd(input: BufferedInputStream): ByteArrayOutputStream? {
        val buf = ByteArrayOutputStream()
        var matched = 0
        while (buf.size() < 16_384) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            matched = when {
                (matched == 0 || matched == 2) && b == '\r'.code -> matched + 1
                matched == 1 && b == '\n'.code -> 2
                matched == 3 && b == '\n'.code -> return buf
                b == '\r'.code -> 1
                else -> 0
            }
        }
        return null
    }

    private fun sendResponse(
        socket: Socket,
        code: Int,
        reason: String,
        body: String,
        extraHeader: String = ""
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            extraHeader +
            "\r\n"
        val out = socket.getOutputStream()
        out.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }
}
