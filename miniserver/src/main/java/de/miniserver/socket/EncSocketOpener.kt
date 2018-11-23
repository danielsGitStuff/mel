package de.miniserver.socket

import de.mein.DeferredRunnable
import de.mein.MeinRunnable
import de.mein.auth.data.access.CertificateManager
import de.mein.auth.tools.N
import de.mein.update.VersionAnswer
import de.miniserver.MiniServer

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class EncSocketOpener(private val certificateManager: CertificateManager, private val port: Int, private val miniServer: MiniServer, private val versionAnswer: VersionAnswer) : DeferredRunnable() {
    private var serverSocket: ServerSocket? = null

    override fun getRunnableName(): String {
        return javaClass.simpleName
    }

    override fun onShutDown() {
        N.s { serverSocket!!.close() }
    }


    override fun runImpl() {
        N.r {
            serverSocket = certificateManager.createServerSocket()
            serverSocket!!.bind(InetSocketAddress(port))
            while (!Thread.currentThread().isInterrupted) {
                val socket = serverSocket!!.accept()
                val encSocket = EncSocket(socket, versionAnswer)
                miniServer.execute(encSocket)
            }
        }
        onShutDown()
    }
}