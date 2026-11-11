package de.mel.auth.broadcast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.tools.N;
import fun.with.promise.Promise;

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
    public Promise<Void, Void> onShutDown() {
        // with runImpl's bind: either the port was bound and is closed here, or it is never bound
        synchronized (this) {
            N.s(() -> socket.close());
            socket = null;
        }
        return null;
    }

    @Override
    public void stop() {
        super.stop();
        synchronized (this) {
            N.s(() -> socket.close());
            socket = null;
        }
    }

    public void resume() {
        try {
            if (socket != null) {
                socket = new MulticastSocket(listenerPort);
                try {
                    socket.setBroadcast(true);
                } catch (Exception ignored) {
                }
                joinGroup(socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<NetworkInterface> multicastInterfaces() {
        List<NetworkInterface> result = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                try {
                    if (nif.isUp() && nif.supportsMulticast())
                        result.add(nif);
                } catch (SocketException ignored) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private void joinGroup(MulticastSocket socket) throws IOException {
        boolean joined = false;
        if (group != null) {
            for (NetworkInterface nif : multicastInterfaces()) {
                try {
                    socket.joinGroup(new InetSocketAddress(group, 0), nif);
                    joined = true;
                } catch (IOException e) {
                    Lok.debug("could not join " + group + " on " + nif.getName() + ": " + e);
                }
            }
            if (!joined)
                socket.joinGroup(group);
        }
    }

    private void leaveGroup(MulticastSocket socket) {
        if (group != null) {
            for (NetworkInterface nif : multicastInterfaces()) {
                N.s(() -> socket.leaveGroup(new InetSocketAddress(group, 0), nif));
            }
            N.s(() -> socket.leaveGroup(group));
        }
    }

    @Override
    public void runImpl() {
        if (listenerPort != null) {
            try {

                synchronized (this) {
                    if (isStopped())
                        return;
                    if (socket == null) {
                        socket = new MulticastSocket(listenerPort);
                        try {
                            socket.setBroadcast(true);
                        } catch (Exception ignored) {
                        }
                        joinGroup(socket);
                    }
                }
                resolveStarted();
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] buf = new byte[300];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    this.handleMessage(packet, buf);
                }
                if (socket != null)
                    leaveGroup(socket);
            } catch (SocketException e) {
                // network seems to be down
                if (!isStopped()) {
                    e.printStackTrace();
                    resolveStarted();
                }
            } catch (Exception e) {
                if (!isStopped()) {
                    e.printStackTrace();
                    rejectStarted(new IOException("port " + listenerPort + " (discovery): " + e.getMessage(), e));
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
            resolveStarted();
    }


    protected abstract void handleMessage(DatagramPacket packet, byte[] buf);


    public synchronized void brotcast(int port, String msg) throws IOException {
        Lok.debug("I'm going to brotcast now!");
        byte[] buf = msg.getBytes();
        MulticastSocket s = socket();
        try {
            s.setBroadcast(true);
        } catch (Exception ignored) {
        }
        boolean sent = false;
        // 1. Localhost for tests and local instances
        try {
            DatagramPacket localPacket = new DatagramPacket(buf, buf.length, InetAddress.getByName("127.0.0.1"), port);
            s.send(localPacket);
            sent = true;
        } catch (Exception ignored) {
        }
        // 2. Multicast group on all multicast interfaces
        if (group != null) {
            DatagramPacket mcastPacket = new DatagramPacket(buf, buf.length, group, port);
            for (NetworkInterface nif : multicastInterfaces()) {
                try {
                    s.setNetworkInterface(nif);
                    s.send(mcastPacket);
                    sent = true;
                } catch (IOException e) {
                    Lok.debug("could not multicast on " + nif.getName() + ": " + e);
                }
            }
        }
        // 3. Subnet directed broadcast on all active interfaces
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                try {
                    if (!nif.isUp()) continue;
                    for (InterfaceAddress ifAddr : nif.getInterfaceAddresses()) {
                        InetAddress bcast = ifAddr.getBroadcast();
                        if (bcast != null) {
                            try {
                                try {
                                    s.setNetworkInterface(nif);
                                } catch (Exception ignored) {
                                }
                                DatagramPacket p = new DatagramPacket(buf, buf.length, bcast, port);
                                s.send(p);
                                sent = true;
                            } catch (IOException ignored) {
                            }
                        }
                    }
                } catch (SocketException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        // 4. Fallback
        if (!sent && group != null) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, port);
            s.send(packet);
        }
    }

    private MulticastSocket socket() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new MulticastSocket(listenerPort);
            try {
                socket.setBroadcast(true);
            } catch (Exception ignored) {
            }
        }
        return socket;
    }


}
