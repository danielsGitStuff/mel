package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import org.jdeferred.Promise;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by xor on 04.09.2016.
 */
public class MelAuthSocketOpener extends DeferredRunnable {

    private ServerSocket serverSocket;
    private final MelAuthService melAuthService;
    private final int port;

    public MelAuthSocketOpener(MelAuthService melAuthService, int port) {
        this.melAuthService = melAuthService;
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
            serverSocket = melAuthService.getCertificateManager().createServerSocket();
            Lok.debug("binding to " + port);
            serverSocket.bind(new InetSocketAddress(port));
            Lok.debug("binding to " + port + " successful");
            startedPromise.resolve(this);
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = this.serverSocket.accept();
                MelSocket melSocket = new MelAuthSocket(melAuthService, socket);
                melSocket.start();
            }
        } catch (Exception e) {
            if (!isStopped()) {
                Lok.error("MelAuthSocketOpener.runTry.FAAAAAIL!");
                e.printStackTrace();
            }
        } finally {
            try {
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
