import de.mein.Lok;
import de.mein.sql.Hash;
import de.miniserver.MiniServer;
import de.miniserver.socket.BinarySocket;
import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class Test {
    private static final File TEST_DIR = new File("miniserver.w.test");

    private MiniServer miniServer;
    private String hash;
    private File receivedTestFile;
    private File filesDir;

    @Before
    public void before() throws Exception {
        filesDir = new File(TEST_DIR.getAbsolutePath() + File.separator + MiniServer.DIR_FILES_NAME);
        filesDir.mkdirs();
        File apkFile = new File(TEST_DIR.getAbsolutePath() + File.separator + MiniServer.DIR_FILES_NAME + File.separator + MiniServer.LATEST_APK);
        File apkInfo = new File(TEST_DIR.getAbsolutePath() + File.separator + MiniServer.DIR_FILES_NAME + File.separator + MiniServer.APK_INFO);
        Files.write(Paths.get(apkInfo.toURI()), "555".getBytes());
        Files.write(Paths.get(apkFile.toURI()), "89089890".getBytes());
        hash = Hash.sha256(new FileInputStream(apkFile));
        miniServer = new MiniServer(TEST_DIR);
        miniServer.start();
        receivedTestFile = new File(TEST_DIR + File.separator + "test.file.apk");
        receivedTestFile.delete();
    }

    @After
    public void after() {
        miniServer.shutdown();
        Arrays.stream(filesDir.listFiles()).forEach(File::delete);
        filesDir.delete();
        Arrays.stream(TEST_DIR.listFiles()).forEach(File::delete);
        TEST_DIR.delete();
    }

    @org.junit.Test
    public void binary() throws Exception {
        Lok.debug(hash);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", MiniServer.DEFAULT_BINARY_PORT));
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        FileOutputStream fos = new FileOutputStream(receivedTestFile);
        out.writeUTF(BinarySocket.QUERY_FILE + hash);
        while (!socket.isClosed()) {
            byte[] bytes = new byte[ 2];
            in.readFully(bytes);
            fos.write(bytes);
        }
        String receivedHash = Hash.sha256(new FileInputStream(receivedTestFile));
        assertEquals(hash, receivedHash);
//        Lok.debug("done");
//        while (true) {
//        }
    }
}
