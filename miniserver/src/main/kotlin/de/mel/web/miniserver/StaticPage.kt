package de.mel.web.miniserver

import java.io.File


class StaticPage {
    companion object {
        val pageRepo = hashMapOf<String, StaticPage>()
    }

    val path: String
    val bytes: ByteArray
    val contentType: String

    constructor(path: String) {
        if (pageRepo.containsKey(path)) {
            this.path = path
            this.bytes = pageRepo[path]!!.bytes
            this.contentType = pageRepo[path]!!.contentType
            return
        }
        val f = File(path)
        val resourceBytes = f.inputStream().readBytes()
        var html = String(resourceBytes)
        this.path = path
        this.bytes = html.toByteArray()
        this.contentType = when (f.extension) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "ttf" -> "application/font-sfnt"
            "svg" -> "image/svg+xml"
            "wasm" -> "application/wasm"
            "ogg" -> "audio/ogg"
            "json" -> "application/json"
            "ico" -> "image/vnd.microsoft.icon"
            "gif" -> "image/gif"
            "png" -> "image/png"
            "scss" -> "text/x-sass"
            "txt" -> "text/plain"
            "xml" -> "application/xml"
            "mp3" -> "audio/mp3"
            "map" -> "application/octet-stream"
            "jpg" -> "image/jpeg"
            "jpeg" -> "image/jpeg"
            else -> throw Exception("unknown content type for extension: ${f.extension}")
        }
        if (!pageRepo.containsKey(path)) {
            pageRepo[path] = this
        }
    }
}
