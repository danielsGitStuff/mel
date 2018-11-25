import de.mein.Lok;
import de.mein.Versioner;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.MeinSocket;
import de.mein.auth.tools.F;
import de.mein.sql.Hash;
import de.mein.sql.RWLock;
import de.miniserver.MiniServer;
import de.miniserver.ServerConfig;
import org.junit.After;
import org.junit.Before;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@SuppressWarnings("Duplicates")
public class Test {

//    @Rule
//    public Timeout globalTimeout= Timeout.builder().withTimeout(15, TimeUnit.SECONDS).build();

    private static final File TEST_DIR = new File("miniserver.w.test");

    private MiniServer miniServer;
    private String hash;
    private File receivedTestFile;
    private File filesDir;
    private File authDir;

    @Before
    public void before() throws Exception {
        F.rmRf(TEST_DIR);
        filesDir = new File(TEST_DIR.getAbsolutePath() + File.separator + MiniServer.DIR_FILES_NAME);
        filesDir.mkdirs();
        authDir = new File(TEST_DIR.getAbsolutePath() + File.separator + "meinauth.test");
        File binaryFile = new File(TEST_DIR.getAbsolutePath() + File.separator + MiniServer.DIR_FILES_NAME + File.separator + MeinStrings.update.VARIANT_JAR + ".jar");
        File propertiesFile = new File(TEST_DIR.getAbsolutePath() + File.separator + MiniServer.DIR_FILES_NAME + File.separator + MeinStrings.update.VARIANT_JAR + ".jar" + MeinStrings.update.INFO_APPENDIX);
        byte[] manyBytes = new byte[10 * 1024 * 1024];
        new Random().nextBytes(manyBytes);
        Properties properties = new Properties();
        properties.setProperty("version", "666");
        properties.setProperty("variant", MeinStrings.update.VARIANT_JAR);
        properties.store(new FileWriter(propertiesFile), "");
//        Files.write(Paths.get(propertiesFile.toURI()), properties..getBytes());
        Files.write(Paths.get(binaryFile.toURI()), manyBytes);
        hash = Hash.sha256(new FileInputStream(binaryFile));
        ServerConfig config = new ServerConfig();
        config.setWorkingDirectory(TEST_DIR.getAbsolutePath());
        miniServer = new MiniServer(config);
        miniServer.start();
        receivedTestFile = new File(TEST_DIR + File.separator + "test.file.apk");
        receivedTestFile.delete();

        Versioner.configure(new Versioner.BuildReader() {
            @Override
            public void readProperties() throws IOException {
                properties.setProperty("version", "22");
                properties.setProperty("variant", "konsole");
                variant = "konsole";
                version = 22L;
            }
        });
    }

    @After
    public void after() {
        miniServer.shutdown();
        F.rmRf(TEST_DIR);
        F.rmRf(authDir);

    }

    @org.junit.Test
    public void retrieveFile() throws Exception {
        Lok.debug(hash);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", MeinAuthSettings.UPDATE_BINARY_PORT));
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        FileOutputStream fos = new FileOutputStream(receivedTestFile);
        out.writeUTF(MeinStrings.update.QUERY_FILE + hash);
        byte[] bytes;
        try {
            bytes = new byte[MeinSocket.BLOCK_SIZE];
            int read;
            do {
                read = in.read(bytes);
                if (read > 0)
                    fos.write(bytes, 0, read);
            } while (read > 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String receivedHash = Hash.sha256(new FileInputStream(receivedTestFile));
        assertEquals(hash, receivedHash);
        Lok.debug("done");
    }

    //todo disabled: no UpdateHandler for pc available now
    //@org.junit.Test
    public void queryAndRetrieve() throws Exception {
        RWLock lock = new RWLock();
        Lok.devOnLineMatches("Success. I got Update!!!1!", lock::unlockWrite);
        MeinAuthSettings meinAuthSettings = MeinAuthSettings.createDefaultSettings().setPort(8913).setBrotcastPort(8914).setDeliveryPort(8915).setBrotcastListenerPort(8916).setUpdateUrl("localhost");
        meinAuthSettings.setWorkingDirectory(authDir);
        MeinBoot meinBoot = new MeinBoot(meinAuthSettings, new PowerManager(meinAuthSettings));
        meinBoot.boot().done(meinAuthService -> {
            try {
                meinAuthService.getCertificateManager().dev_SetUpdateCertificate(miniServer.getCertificate());
                meinAuthService.updateProgram();
                Lok.debug("");
            } catch (UnrecoverableKeyException | KeyManagementException | NoSuchAlgorithmException | IOException | KeyStoreException | CertificateException e) {
                e.printStackTrace();
            }
        });
        lock.lockWrite().lockWrite();
    }

    @org.junit.Test
    public void retrieveFileFail() throws Exception {
        Lok.debug(hash);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", MeinAuthSettings.UPDATE_BINARY_PORT));
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        FileOutputStream fos = new FileOutputStream(receivedTestFile);
        out.writeUTF(MeinStrings.update.QUERY_FILE + "rubbish");
        byte[] bytes;
        try {
            bytes = new byte[MeinSocket.BLOCK_SIZE];
            int read;
            do {
                read = in.read(bytes);
                if (read > 0)
                    fos.write(bytes, 0, read);
            } while (read > 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String receivedHash = Hash.sha256(new FileInputStream(receivedTestFile));
        assertNotEquals(hash, receivedHash);
        assertEquals(0L, receivedTestFile.length());
        Lok.debug("done");
    }
}
