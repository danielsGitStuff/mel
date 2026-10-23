package de.mel.web.serverparts

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object MimeTypeReader {
    private val byExtension = mapOf(
            "html" to ContentType.TEXT,
            "js" to "application/javascript",
            "css" to "text/css",
            "ttf" to "application/font-sfnt",
            "svg" to ContentType.SVG,
            "wasm" to "application/wasm",
            "ogg" to "audio/ogg",
            "json" to "application/json",
            "ico" to "image/vnd.microsoft.icon",
            "gif" to "image/gif",
            "png" to ContentType.PNG,
            "webp" to ContentType.WEBP,
            "scss" to "text/x-sass",
            "txt" to ContentType.PLAIN,
            "xml" to "application/xml",
            "mp3" to "audio/mp3",
            "map" to "application/octet-stream",
            "jpg" to ContentType.JPG,
            "jpeg" to ContentType.JPG)

    /**
     * by the extension of [name] only, null if it is not a known one
     */
    fun forName(name: String): String? = byExtension[name.substringAfterLast('.', "").lowercase()]

    @Throws(IOException::class)
    fun readMimeType(file: File): String? = forName(file.name) ?: Files.probeContentType(Path.of(file.toURI()))
}
