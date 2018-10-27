package de.mein.update;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.auth.tools.N;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public abstract class SimpleSocket extends DeferredRunnable {
    protected final Socket socket;
    protected final DataOutputStream out;
    protected final DataInputStream in;
    public SimpleSocket(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void run() {
        runImpl();
        onShutDown();
    }


    @Override
    public void onShutDown() {
        N.s(() -> socket.close());
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }


}
