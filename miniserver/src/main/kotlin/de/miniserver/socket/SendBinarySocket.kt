package de.miniserver.socket

import de.mel.Lok
import de.mel.auth.MelStrings
import de.mel.auth.socket.MelSocket
import de.mel.auth.tools.N
import de.mel.update.SimpleSocket
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
            if (s.startsWith(MelStrings.update.QUERY_FILE)) {
                val hash = s.substring(MelStrings.update.QUERY_FILE.length, s.length)
                val f = fileRepository[hash].file
                Lok.info("reading file: " + f.absolutePath)
                fin = FileInputStream(f)
                val bytes = ByteArray(MelSocket.BLOCK_SIZE)
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
