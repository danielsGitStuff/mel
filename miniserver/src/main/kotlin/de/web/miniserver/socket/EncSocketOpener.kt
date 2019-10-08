package de.web.miniserver.socket

import de.mel.DeferredRunnable
import de.mel.Lok
import de.mel.auth.data.access.CertificateManager
import de.mel.auth.tools.N
import de.mel.update.VersionAnswer
import de.web.miniserver.MiniServer
import org.jdeferred.Promise

import java.net.InetSocketAddress
import java.net.ServerSocket

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
