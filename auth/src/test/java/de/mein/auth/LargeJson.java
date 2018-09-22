package de.mein.auth;

import org.junit.Test;
import org.junit.experimental.max.MaxCore;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import de.mein.Lok;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinAuthSocketOpener;
import de.mein.auth.socket.MeinSocket;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import static org.junit.Assert.*;

/**
 * Created by xor on 10/11/17.
 */

public class LargeJson {

    public static class StringList implements SerializableEntity {
        private List<String> items = new ArrayList<>();
    }

    public static class Entity implements SerializableEntity {
        private List<StringList> stringList = new ArrayList<>();
    }

//    @Test
//    public void json() throws Exception {
//        final String largeJson = createLargeJson();
//        MeinAuthService ma1 = MeinAuthService.createDevInstance();
//        MeinSocket meinSocketListen = new MeinSocket(ma1);
//
//        ServerSocket socketListen = new ServerSocket();
//        socketListen.bind(new InetSocketAddress(6666));
//        WaitLock waitLock = new WaitLock();
//        Thread thread = new Thread(() -> N.r(() -> {
//            Socket acc = socketListen.accept();
//            meinSocketListen.setSocket(acc);
//            meinSocketListen.setListener(new MeinSocket.MeinSocketListener() {
//                @Override
//                public void onIsolated() {
//
//                }
//
//                @Override
//                public void onMessage(MeinSocket meinSocket, String msg) {
//                    Lok.debug("LargeJson.onMessage: " + msg);
//                    assertEquals(largeJson, msg);
//                    waitLock.unlock().unlock();
//                }
//
//                @Override
//                public void onOpen() {
//                    Lok.debug("LargeJson.onOpen");
//                }
//
//                @Override
//                public void onError(Exception ex) {
//
//                }
//
//                @Override
//                public void onClose(int code, String reason, boolean remote) {
//
//                }
//
//                @Override
//                public void onBlockReceived(byte[] block) {
//
//                }
//            });
//            meinSocketListen.start();
//            waitLock.unlock();
//        }));
//        waitLock.lock();
//        thread.start();
//        MeinSocket meinSocketSend = new MeinSocket(MeinAuthService.createDevInstance());
//        InetSocketAddress addr = new InetSocketAddress("localhost", 6666);
//        Socket socketSend = new Socket();
//        socketSend.connect(addr);
//        meinSocketSend.setSocket(socketSend);
//
//
//        String json = largeJson;
//        meinSocketSend.send(json);
//        Lok.debug("LargeJson.json." + json.length());
//        new WaitLock().lock().lock();
//    }

    private void bla() {
        String json = "abcdefghij";
        int MAX_CHARS = 3;
        String s = json.substring(0, MAX_CHARS);
        json = json.substring(MAX_CHARS, json.length());
        Lok.debug("LargeJson.bla");
    }

    private String createLargeJson() throws JsonSerializationException, IllegalAccessException {
        Entity entity = new Entity();
        for (Integer i = 0; i < 1024; i++) {
            StringList stringList = new StringList();
            stringList.items.add(i.toString());
            entity.stringList.add(stringList);
        }
        return SerializableEntitySerializer.serialize(entity);
    }
}
