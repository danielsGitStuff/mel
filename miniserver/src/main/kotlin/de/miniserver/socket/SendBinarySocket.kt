package de.miniserver.socket

import de.mein.Lok
import de.mein.auth.MeinStrings
import de.mein.auth.socket.MeinSocket
import de.mein.auth.tools.N
import de.mein.update.SimpleSocket
import de.miniserver.data.FileRepository
import java.io.FileInputStream
import java.io.IOException
import java.net.Socket

/**
 * delivers a binary file
 */
class SendBinarySocket @Throws(IOException::class)
constructor(socket: Socket, private val fileRepository: FileRepository) : SimpleSocket(socket) {

    override fun runImpl() {
        var fin: FileInputStream? = null
        try {
            val s = `in`.readUTF()
            if (s.startsWith(MeinStrings.update.QUERY_FILE)) {
                val hash = s.substring(MeinStrings.update.QUERY_FILE.length, s.length)
                val f = fileRepository[hash].file
                Lok.info("reading file: " + f.absolutePath)
                fin = FileInputStream(f)
                val bytes = ByteArray(MeinSocket.BLOCK_SIZE)
                var read: Int
                do {
                    read = fin.read(bytes)
                    if (read > 0) {
                        Lok.debug("sending block")
                        out.write(bytes, 0, read)
                    }
                } while (read > 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            N.s { fin?.close() }
            N.s { out.close() }
        }
    }


}
