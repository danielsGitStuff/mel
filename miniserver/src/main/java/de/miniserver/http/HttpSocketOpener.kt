package de.miniserver.http

import de.mein.DeferredRunnable
import de.mein.auth.tools.N
import de.miniserver.MiniServer
import de.miniserver.data.FileRepository
import java.net.InetSocketAddress
import java.net.ServerSocket

class HttpSocketOpener(private val port: Int, private val miniServer: MiniServer, private val fileRepository: FileRepository) : DeferredRunnable() {
    override fun onShutDown() {
        serverSocket.close()
    }

    private lateinit var serverSocket: ServerSocket

    override fun runImpl() {
        N.r {
            serverSocket = ServerSocket()
            serverSocket.bind(InetSocketAddress(port))
            while (!Thread.currentThread().isInterrupted) {
                val socket = serverSocket.accept()
                val sendBinarySocket = SendHttpSocket(socket, fileRepository)
                miniServer.execute(sendBinarySocket)
            }
        }
        onShutDown()
    }

    override fun getRunnableName(): String = this.javaClass.simpleName


}