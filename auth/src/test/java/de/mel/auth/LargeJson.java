package de.mel.auth;

import java.util.ArrayList;
import java.util.List;

import de.mel.Lok;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

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
//        MelAuthService ma1 = MelAuthService.createDevInstance();
//        MelSocket melSocketListen = new MelSocket(ma1);
//
//        ServerSocket socketListen = new ServerSocket();
//        socketListen.bind(new InetSocketAddress(6666));
//        WaitLock waitLock = new WaitLock();
//        Thread thread = new Thread(() -> N.r(() -> {
//            Socket acc = socketListen.accept();
//            melSocketListen.setSocket(acc);
//            melSocketListen.setListener(new MelSocket.MelSocketListener() {
//                @Override
//                public void onIsolated() {
//
//                }
//
//                @Override
//                public void onMessage(MelSocket melSocket, String msg) {
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
//            melSocketListen.start();
//            waitLock.unlock();
//        }));
//        waitLock.lock();
//        thread.start();
//        MelSocket melSocketSend = new MelSocket(MelAuthService.createDevInstance());
//        InetSocketAddress addr = new InetSocketAddress("localhost", 6666);
//        Socket socketSend = new Socket();
//        socketSend.connect(addr);
//        melSocketSend.setSocket(socketSend);
//
//
//        String json = largeJson;
//        melSocketSend.send(json);
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
