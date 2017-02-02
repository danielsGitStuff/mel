package de.mein.auth.socket;


import de.mein.MeinRunnable;
import de.mein.auth.service.MeinAuthService;
import de.mein.sql.RWLock;
import org.jdeferred.impl.DeferredObject;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Created by xor on 09.08.2016.
 */
public abstract class MeinAuthServer extends MeinRunnable {
    private boolean started = false;
    protected MeinSocket.MeinSocketListener listener;
    protected ServerSocket serverSocket;
    protected Thread thread;
    private ServerSocketFactory serverSocketFactory;
    protected Integer port;


    public MeinAuthServer() throws IOException {

    }

    public MeinAuthServer setServerSocketFactory(ServerSocketFactory serverSocketFactory) {
        this.serverSocketFactory = serverSocketFactory;
        return this;
    }
/*
    public MeinAuthServer setPort(int listenerPort) {
        this.listenerPort = listenerPort;
        return this;
    }*/


    public synchronized DeferredObject<MeinRunnable, Exception, Void> start() {
        try {
            if (!started) {
                serverSocket = serverSocketFactory.createServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(port));
                return super.start();
                //started = true;
                //thread = new Thread(this);
                //thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return startedPromise;
    }

    public synchronized void stop() {
        try {
            if (thread != null) {
                thread.interrupt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public MeinAuthServer setMeinSocketListener(MeinSocket.MeinSocketListener listener) {
        this.listener = listener;
        return this;
    }

}
