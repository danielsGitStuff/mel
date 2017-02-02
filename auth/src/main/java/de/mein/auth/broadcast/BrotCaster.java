package de.mein.auth.broadcast;

import de.mein.MeinRunnable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;

/**
 * Created by xor on 9/22/16.
 */
public abstract class BrotCaster extends MeinRunnable {
    protected final Integer listenerPort;
    private final Integer brotcastPort;
    protected MulticastSocket socket;

    public BrotCaster(Integer listenerPort, Integer brotcastPort) {
        this.listenerPort = listenerPort;
        this.brotcastPort = brotcastPort;
    }

    @Override
    public void run() {
        if (listenerPort != null) {
            try {
                socket = new MulticastSocket(listenerPort);
                InetAddress group = InetAddress.getByName("224.0.0.1");
//            InetAddress group = InetAddress.getByName("localhost");
                socket.joinGroup(group);
                startedPromise.resolve(this);
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] buf = new byte[300];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    this.handleMessage(packet, buf);
                }
                socket.leaveGroup(group);
            }catch (SocketException e){
                // network seems to be down
                e.printStackTrace();
                startedPromise.resolve(this);
            }catch (Exception e) {
                e.printStackTrace();
                startedPromise.reject(e);
            }finally {
                try {
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }else
            startedPromise.resolve(this);
    }

    protected abstract void handleMessage(DatagramPacket packet, byte[] buf);


    public void brotcast(int port, String msg) throws IOException {
        byte[] buf = msg.getBytes();
        InetAddress brotcastAddress = InetAddress.getByName("224.0.0.1"); //this is BrotCast!!!
        //InetAddress brotcastAddress = InetAddress.getByName("localhost"); //this is LocalCast!!!
        if (brotcastAddress != null) {
            System.out.println(brotcastAddress.getCanonicalHostName());
            DatagramPacket packet;
            packet = new DatagramPacket(buf, buf.length, brotcastAddress, port);
            socket().send(packet);
        } else System.out.println("fail");
    }

    private MulticastSocket socket() throws IOException {
        if (socket == null || socket.isClosed()){
            socket = new MulticastSocket(listenerPort);
        }
        return socket;
    }


}
