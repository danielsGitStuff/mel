package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.tools.N;
import fun.with.promise.Promise;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by xor on 04.09.2016.
 */
public class MelAuthSocketOpener extends DeferredRunnable {

    private ServerSocket serverSocket;
    private final MelAuthServiceImpl melAuthService;
    private final int port;

    public MelAuthSocketOpener(MelAuthServiceImpl melAuthService, int port) {
        this.melAuthService = melAuthService;
        this.port = port;
    }

    @Override
    public Promise<Void, Void> onShutDown() {
        // with runImpl's bind: either the port was bound and is closed here, or it is never bound
        synchronized (this) {
            N.r(() -> serverSocket.close());
        }
        return DeferredRunnable.ResolvedDeferredObject();
    }

    @Override
    public void runImpl() {
        try {
            synchronized (this) {
                if (isStopped())
                    return;
                serverSocket = melAuthService.getCertificateManager().createServerSocket();
                Lok.debug("binding to " + port);
                serverSocket.bind(new InetSocketAddress(port));
            }
            Lok.debug("binding to " + port + " successful");
            resolveStarted();
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = this.serverSocket.accept();
                MelSocket melSocket = new MelAuthSocket(melAuthService, socket);
                melSocket.start();
            }
        } catch (Exception e) {
            if (!isStopped()) {
                Lok.error("MelAuthSocketOpener.runTry.FAAAAAIL!");
                e.printStackTrace();
                // a bind that failed is the end of the start (after the start this does nothing)
                rejectStarted(new IOException("port " + port + " (connections): " + e.getMessage(), e));
            }
        } finally {
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        Lok.debug("MelAuthService.runTry.end");
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + melAuthService.getName();
    }

}
