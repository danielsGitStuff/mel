package de.mein.auth.broadcast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.tools.N;

/**
 * Sends and retrieves small messages from the broadcast network address
 */
public abstract class BrotCaster extends DeferredRunnable {
    protected final Integer listenerPort;
    private final Integer brotcastPort;
    protected MulticastSocket socket;
    private InetAddress group;

    public BrotCaster(Integer listenerPort, Integer brotcastPort) {
        this.listenerPort = listenerPort;
        this.brotcastPort = brotcastPort;
        try {
            group = InetAddress.getByName("224.0.0.1");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onShutDown() {
        N.s(()->socket.close());
        socket = null;
    }

    @Override
    public void suspend() {
        super.suspend();
        N.s(()->socket.close());
        socket = null;
    }

    public void resume() {
        try {
            if (socket != null) {
                socket = new MulticastSocket(listenerPort);
//            InetAddress group = InetAddress.getByName("localhost");
                socket.joinGroup(group);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void runImpl() {
        if (listenerPort != null) {
            try {

                if (socket == null) {
                    socket = new MulticastSocket(listenerPort);
//            InetAddress group = InetAddress.getByName("localhost");
                    socket.joinGroup(group);
                }
                startedPromise.resolve(this);
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] buf = new byte[300];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    this.handleMessage(packet, buf);
                }
                if (socket != null)
                    socket.leaveGroup(group);
            } catch (SocketException e) {
                // network seems to be down
                if (!isStopped()) {
                    e.printStackTrace();
                    startedPromise.resolve(this);
                }
            } catch (Exception e) {
                if (!isStopped()) {
                    e.printStackTrace();
                    startedPromise.reject(e);
                }
            } finally {
                try {
                    if (socket != null)
                        socket.close();
                    socket = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        } else
            startedPromise.resolve(this);
    }


    protected abstract void handleMessage(DatagramPacket packet, byte[] buf);


    public void brotcast(int port, String msg) throws IOException {
        byte[] buf = msg.getBytes();
        InetAddress brotcastAddress = InetAddress.getByName("224.0.0.1"); //this is BrotCast!!!
        //InetAddress brotcastAddress = InetAddress.getByName("localhost"); //this is LocalCast!!!
        if (brotcastAddress != null) {
            Lok.debug(brotcastAddress.getCanonicalHostName());
            DatagramPacket packet;
            packet = new DatagramPacket(buf, buf.length, brotcastAddress, port);
            socket().send(packet);
        } else Lok.debug("fail");
    }

    private MulticastSocket socket() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new MulticastSocket(listenerPort);
        }
        return socket;
    }


}
