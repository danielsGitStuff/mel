package de.mein.auth.tools.lock;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class DEV_BIND {
    public static final Map<ServerSocket,Integer> serverSockets = new HashMap<>();
    public static final Map<Socket,Integer> sockets = new HashMap<>();

    public static void add(ServerSocket socket) {
        serverSockets.put(socket,socket.getLocalPort());
    }

    public static void addSocket(Socket socket) {
        sockets.put(socket,socket.getLocalPort());
    }
}
