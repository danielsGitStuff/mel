package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelBoot;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.N;
import de.mel.sql.RWLock;
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
        MelAuthSettings json1 = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory((MelBoot.Companion.getDefaultWorkingDir1())).setName("MA1").setVariant(MelStrings.update.VARIANT_JAR);
        MelAuthSettings json2 = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory((MelBoot.Companion.getDefaultWorkingDir2())).setName("MA2").setVariant(MelStrings.update.VARIANT_JAR);

        MelBoot melBoot1 = new MelBoot(json1, new PowerManager(json1));
        MelBoot melBoot2 = new MelBoot(json2, new PowerManager(json2));
        melBoot1.boot().done(ma1 -> N.r(() -> {
            melBoot2.boot().done(ma2 -> N.r(() -> {
                // init 2
                Socket s1 = new Socket();
                ServerSocket serverSocket = new ServerSocket(8555);
                s1.connect(new InetSocketAddress("localhost", 8555));
                Socket s2 = serverSocket.accept();
                MelSocket m1 = new MelSocket(ma1, s1);
                MelSocket m2 = new MelSocket(ma2, s2);
                DeferredObject onUTFsent = new DeferredObject();
                m2.setListener(new MelSocket.MelSocketListener() {
                    @Override
                    public void onIsolated() {

                    }

                    @Override
                    public void onMessage(MelSocket melSocket, String msg) {
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
//        MelAuthSocket melAuthSocket = new MelAuthSocket(new MelAuthService(new MelAuthSettings()),s1);
//        melAuthSocket.
        lock.lockWrite();
        lock.unlockWrite();
        Lok.debug("SocketTest.sendFile.END");
    }
}
