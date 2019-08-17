package de.miniserver.socket

import de.mein.Lok
import de.mein.auth.MeinStrings
import de.mein.auth.tools.N
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer
import de.mein.update.SimpleSocket
import de.mein.update.VersionAnswer

import java.io.IOException
import java.net.Socket

class EncSocket @Throws(IOException::class)
constructor(socket: Socket, private val versionAnswer: VersionAnswer) : SimpleSocket(socket) {

    override fun getRunnableName(): String {
        return javaClass.simpleName
    }

    override fun runImpl() {
        try {
            val jsonAnswer = SerializableEntitySerializer.serialize(versionAnswer)
            while (!Thread.currentThread().isInterrupted) {
                val s = `in`.readUTF()
                if (s == MeinStrings.update.QUERY_VERSION) {
                    out.writeUTF(jsonAnswer)
                    Lok.debug("version sent to ${socket.inetAddress.hostAddress}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            N.s { out.close() }
            N.s { `in`.close() }
        }

    }
}
