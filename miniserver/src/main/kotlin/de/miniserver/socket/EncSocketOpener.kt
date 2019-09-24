package de.miniserver.socket

import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.MeinRunnable
import de.mein.auth.data.access.CertificateManager
import de.mein.auth.tools.N
import de.mein.update.VersionAnswer
import de.miniserver.MiniServer
import org.jdeferred.Promise

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class EncSocketOpener(private val certificateManager: CertificateManager, private val port: Int, private val miniServer: MiniServer, private val versionAnswer: VersionAnswer) : DeferredRunnable() {
    private var serverSocket: ServerSocket? = null

    override fun getRunnableName(): String {
        return javaClass.simpleName
    }

    override fun onShutDown(): Promise<Void, Void, Void>? {
        N.s { serverSocket?.close() }
        return null
    }


    override fun runImpl() {
        N.r {
            Lok.info("binding auth socket opener to    : $port")
            serverSocket = ServerSocket()
            serverSocket = certificateManager.createServerSocket()
            serverSocket!!.bind(InetSocketAddress(port))
            Lok.info("successfully bound auth socket to: $port")
            while (!Thread.currentThread().isInterrupted) {
                val socket = serverSocket!!.accept()
                Lok.info("socket from ${socket.inetAddress.hostAddress} accepted")
                val encSocket = EncSocket(socket, versionAnswer)
                miniServer.execute(encSocket)
            }
        }
        onShutDown()
    }
}
