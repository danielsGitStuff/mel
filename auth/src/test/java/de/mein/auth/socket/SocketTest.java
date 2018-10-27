package de.mein.auth.socket;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.N;
import de.mein.sql.RWLock;
import org.jdeferred.impl.DeferredObject;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by xor on 12/12/16.
 */
public class SocketTest {
    private static RWLock lock = new RWLock();

//    @Test
    public void sendFile() throws Exception {
        lock.lockWrite();
        //init
        MeinAuthSettings json1 = new MeinAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory((MeinBoot.defaultWorkingDir1)).setName("MA1").setGreeting("greeting1").setVariant(MeinStrings.update.VARIANT_JAR);
        MeinAuthSettings json2 = new MeinAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory((MeinBoot.defaultWorkingDir2)).setName("MA2").setGreeting("greeting2").setVariant(MeinStrings.update.VARIANT_JAR);

        MeinBoot meinBoot1 = new MeinBoot(json1, new PowerManager(json1));
        MeinBoot meinBoot2 = new MeinBoot(json2, new PowerManager(json2));
        meinBoot1.boot().done(ma1 -> N.r(() -> {
            meinBoot2.boot().done(ma2 -> N.r(() -> {
                // init 2
                Socket s1 = new Socket();
                ServerSocket serverSocket = new ServerSocket(8555);
                s1.connect(new InetSocketAddress("localhost", 8555));
                Socket s2 = serverSocket.accept();
                MeinSocket m1 = new MeinSocket(ma1, s1);
                MeinSocket m2 = new MeinSocket(ma2, s2);
                DeferredObject onUTFsent = new DeferredObject();
                m2.setListener(new MeinSocket.MeinSocketListener() {
                    @Override
                    public void onIsolated() {

                    }

                    @Override
                    public void onMessage(MeinSocket meinSocket, String msg) {
                        Lok.debug("SocketTest.onMessage: " + msg);
                        onUTFsent.resolve(null);
                        //lock.unlockWrite();
                    }

                    @Override
                    public void onOpen() {

                    }

                    @Override
                    public void onError(Exception ex) {

                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {

                    }

                    @Override
                    public void onBlockReceived(BlockReceivedJob block) {

                    }
                });
                String res = "aktion1.jpg";
                long size = new File(getClass().getClassLoader().getResource(res).getFile()).length();
                byte[] bytes = new byte[(int) size];
                InputStream in = getClass().getClassLoader().getResourceAsStream(res);
                DataInputStream dataInputStream = new DataInputStream(in);
                dataInputStream.readFully(bytes);

                m2.start();
                m1.send("purr");

                onUTFsent.done(result -> {
                    try {
                        Lok.debug("SocketTest.sendFile1");
                        Lok.debug("SocketTest.sendFile.reMode");
                        Lok.debug("SocketTest.sendFile.reMode.END");


                        DataOutputStream output = new DataOutputStream(s1.getOutputStream());
                        output.write(bytes);
                        output.writeUTF("aa");
                        m1.send("hurr");
                        m1.send("durr");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }));
        }));




        //output.flush();
//        output.write(bytes);
//        output.flush();
//        output.write(bytes);
//        output.flush();
//        output.write(bytes);
//        output.flush();

        Lok.debug("SocketTest.sendFile2");
//        MeinAuthSocket meinAuthSocket = new MeinAuthSocket(new MeinAuthService(new MeinAuthSettings()),s1);
//        meinAuthSocket.
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("SocketTest.sendFile.END");
    }
}
