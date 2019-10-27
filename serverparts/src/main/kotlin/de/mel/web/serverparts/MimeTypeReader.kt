package de.mel.web.serverparts

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object MimeTypeReader {
    @Throws(IOException::class)
    fun readMimeType(file: File): String? {
        val mime: String? = Files.probeContentType(Path.of(file.toURI()))
        if (mime == null) {
            val dotIndex = file.name.lastIndexOf('.')
            if (dotIndex > 0 && file.name.substring(dotIndex + 1).toLowerCase() == "wasm") return "application/wasm"
        }
        return mime
    }
}