package de.miniserver.socket;

import de.mein.DeferredRunnable;
import de.mein.MeinRunnable;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.tools.N;
import de.mein.update.VersionAnswer;
import de.miniserver.MiniServer;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class EncSocketOpener extends DeferredRunnable {
    private final CertificateManager certificateManager;
    private final int port;
    private final MiniServer miniServer;
    private final VersionAnswer versionAnswer;
    private ServerSocket serverSocket;

    public EncSocketOpener(CertificateManager certificateManager, int port, MiniServer miniServer, VersionAnswer versionAnswer) {
        this.certificateManager = certificateManager;
        this.port = port;
        this.miniServer = miniServer;
        this.versionAnswer = versionAnswer;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    @Override
    public void onShutDown() {
        N.s(() -> serverSocket.close());
    }


    @Override
    public void runImpl() {
        N.r(() -> {
            serverSocket = certificateManager.createServerSocket();
            serverSocket.bind(new InetSocketAddress(port));
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                EncSocket encSocket = new EncSocket(socket, versionAnswer);
                miniServer.execute(encSocket);
            }
        });
        onShutDown();
    }
}
