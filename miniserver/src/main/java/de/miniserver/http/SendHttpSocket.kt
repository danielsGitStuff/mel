package de.miniserver.http

import com.sun.deploy.net.HttpRequest
import com.sun.deploy.net.HttpUtils
import de.mein.update.SimpleSocket
import de.miniserver.data.FileRepository
import kotlinx.io.core.toByteArray
import java.io.*
import java.net.Socket

/**
 * delivers a binary file
 */
class SendHttpSocket @Throws(IOException::class)
constructor(socket: Socket, private val fileRepository: FileRepository) : SimpleSocket(socket) {

    override fun runImpl() {
        val reader = BufferedReader(InputStreamReader(`in`))
        val writer = BufferedWriter(OutputStreamWriter(out))
        reader.lines().forEach { println(it) }
        writer.write("HTTP/1.0 200 OK\r\n");
        writer.write("Date: Fri, 31 Dec 1999 23:59:59 GMT\r\n");
        writer.write("Server: Apache/0.8.4\r\n");
        writer.write("Content-Type: text/html\r\n");
        writer.write("Content-Length: 59\r\n");
        writer.write("Expires: Sat, 01 Jan 2000 00:59:59 GMT\r\n");
        writer.write("Last-modified: Fri, 09 Aug 1996 14:21:40 GMT\r\n");
        writer.write("\r\n");
        writer.write("<TITLE>Exemple</TITLE>");
        writer.write("<P>Ceci est une page d'exemple.</P>")
        writer.flush()
        out.flush()
//        `in`.close()
//        out.close()
//        socket.close()
    }


}
