package de.miniserver.socket

import de.mein.DeferredRunnable
import de.mein.Lok
import de.mein.auth.tools.N
import de.miniserver.MiniServer
import de.miniserver.data.FileRepository
import java.net.InetSocketAddress
import java.net.ServerSocket

class BinarySocketOpener(private val port: Int, private val miniServer: MiniServer, private val fileRepository: FileRepository) : DeferredRunnable() {
    private var serverSocket: ServerSocket? = null


    override fun getRunnableName(): String {
        return javaClass.simpleName
    }

    override fun onShutDown() {
        serverSocket?.close()
    }


    override fun runImpl() {
        N.r {
            Lok.info("binding binary socket opener to    : $port")
            serverSocket = ServerSocket()
            serverSocket!!.bind(InetSocketAddress(port))
            Lok.info("successfully bound binary socket to: $port")
            while (!Thread.currentThread().isInterrupted) {
                val socket = serverSocket!!.accept()
                val sendBinarySocket = SendBinarySocket(socket, fileRepository)
                miniServer.execute(sendBinarySocket)
            }
        }
        onShutDown()
    }
}
