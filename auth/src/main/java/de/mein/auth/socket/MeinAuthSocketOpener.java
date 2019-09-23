package de.mein.auth.socket;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import org.jdeferred.Promise;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by xor on 04.09.2016.
 */
public class MeinAuthSocketOpener extends DeferredRunnable {

    private ServerSocket serverSocket;
    private final MeinAuthService meinAuthService;
    private final int port;

    public MeinAuthSocketOpener(MeinAuthService meinAuthService, int port) {
        this.meinAuthService = meinAuthService;
        this.port = port;
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        N.r(() -> serverSocket.close());
        return DeferredRunnable.ResolvedDeferredObject();
    }

    @Override
    public void runImpl() {
        try {
            serverSocket = meinAuthService.getCertificateManager().createServerSocket();
            Lok.debug("binding to " + port);
            serverSocket.bind(new InetSocketAddress(port));
            Lok.debug("binding to " + port + " successful");
            startedPromise.resolve(this);
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = this.serverSocket.accept();
                MeinSocket meinSocket = new MeinAuthSocket(meinAuthService, socket);
                meinSocket.start();
            }
        } catch (Exception e) {
            if (!isStopped()) {
                Lok.error("MeinAuthSocketOpener.runTry.FAAAAAIL!");
                e.printStackTrace();
            }
        } finally {
            try {
                serverSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        Lok.debug("MeinAuthService.runTry.end");
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName();
    }

}
