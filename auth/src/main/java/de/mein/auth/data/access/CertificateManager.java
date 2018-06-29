package de.mein.auth.data.access;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.dao.CertificateDao;
import de.mein.auth.tools.Cryptor;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.*;

import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Takes care of all Certificates. That means: creating your own, storing foreign ones,
 * synchronizing between the database and the KeyStore.
 * Also creates SSLSockets (you must have the keystore to do so, so this is the proper place).
 */
public class CertificateManager extends FileRelatedManager {
    public static final String PASS = "pass";
    private final String PK_NAME = "private";
    private final String CERT_FILENAME = "cert.cert";
    private final String PK_FILENAME = "pk.key";
    private final String KS_FILENAME = "keystore.bks";
    private final String PUB_FILENAME = "pub.key";
    private KeyStore keyStore;
    private int keysize = 1024;
    private File keyStoreFile;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private X509Certificate certificate;
    private CertificateDao certificateDao;


    public CertificateManager(File workingDirectory, ISQLQueries ISQLQueries, int keysize) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, SQLException, ClassNotFoundException, SignatureException, InvalidKeyException, SqlQueriesException, OperatorCreationException {
        super(workingDirectory);
        System.out.println("CertificateManager.dir: " + workingDirectory.getAbsolutePath());
        this.keysize = keysize;
        //insertCertificate Bouncycastle Provider
        boolean providerSet = false;
        for (Provider p : Security.getProviders()) {
            if (p.getClass().equals(BouncyCastleProvider.class)) {
                providerSet = true;
                break;
            }
        }
        if (!providerSet)
            Security.addProvider(new BouncyCastleProvider());
        //load keystore
        keyStoreFile = new File(createWorkingPath() + KS_FILENAME);
        // we want to start with a clean keystore
        if (keyStoreFile.exists()) {
            boolean deleted = keyStoreFile.delete();
            if (!deleted)
                System.err.println("CertificateManager().KEYSTORE.NOT.DELETED");
        }
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, PASS.toCharArray());
        //init DB stuff
        certificateDao = new CertificateDao(ISQLQueries, false);
        //the actual loading
        if (!loadKeys())
            generateCertificate();
        saveKeysInKeystore();
        loadTrustedCertificates();
    }

    public static void deleteDirectory(File dir) {
        System.out.println("CertificateManager.deleteDirectory: " + dir.getAbsolutePath());
        File[] subs = dir.listFiles();
        if (subs != null)
            for (File f : subs) {
                deleteDirectoryP(f);
            }
        dir.delete();
    }

    private static void deleteDirectoryP(File dir) {
        File[] subs = dir.listFiles();
        if (subs != null)
            for (File f : subs) {
                deleteDirectoryP(f);
            }
        dir.delete();
    }

    private static int generateSecurePositiveRndInt() {
        int num = new SecureRandom().nextInt();
        return (num < 0) ? -num : num;
    }

    public static X509Certificate loadX509CertificateFromBytes(byte[] data) throws CertificateException {
        InputStream in = new ByteArrayInputStream(data);
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        X509Certificate result = (X509Certificate) certFactory.generateCertificate(in);
        return result;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public synchronized Certificate importCertificate(X509Certificate x509Certificate, String name, String answerUuid, String address, Integer port, Integer portCert, String greeting) throws CertificateException, SqlQueriesException, KeyStoreException, NoSuchAlgorithmException, IOException {
        certificateDao.lockWrite();
        Certificate certificate = new Certificate();
        String uuid = getNewUUID().toString();
        certificate.setUuid(uuid)
                .setCertificate(x509Certificate.getEncoded())
                .setAnswerUuid(answerUuid)
                .setAddress(address)
                .setName(name)
                .setPort(port)
                .setCertDeliveryPort(portCert)
                .setGreeting(greeting)
                .setTrusted(false);
        certificate = certificateDao.insertCertificate(certificate);
        certificateDao.unlockWrite();
        this.storeCertInKeyStore(uuid, x509Certificate);
        return certificate;
    }

    public void trustCertificate(Long certId, boolean trusted) throws SqlQueriesException {
        certificateDao.trustCertificate(certId, trusted);
    }

    private void loadTrustedCertificates() throws SqlQueriesException, KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        certificateDao.lockRead();
        for (Certificate dbCert : certificateDao.getTrustedCertificates()) {
            X509Certificate cert = loadX509CertificateFromBytes(dbCert.getCertificate().v());
            storeCertInKeyStore(dbCert.getUuid().v(), cert);
        }
        certificateDao.unlockRead();
    }

    private synchronized void storeCertInKeyStore(String name, X509Certificate certificate) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        if (keyStore.getCertificate(name) != null)
            keyStore.deleteEntry(name);
        keyStore.setCertificateEntry(name, certificate);
        storeKeyStore();
    }

    private byte[] readFile(String fileName) throws IOException {
        String path = createWorkingPath() + fileName;
        File f = new File(path);
        DataInputStream dis = new DataInputStream(new FileInputStream(f));
        byte[] bytes = new byte[(int) f.length()];
        dis.readFully(bytes);
        dis.close();
        return bytes;
    }

    private boolean loadKeys() throws IOException, ClassNotFoundException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        try {
            byte[] privkeyBytes = readFile(PK_FILENAME);
            byte[] pubkeyBytes = readFile(PUB_FILENAME);
            byte[] certBytes = readFile(CERT_FILENAME);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privkeyBytes);
            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubkeyBytes);
            privateKey = kf.generatePrivate(privSpec);
            publicKey = kf.generatePublic(pubSpec);
            certificate = loadX509CertificateFromBytes(certBytes);
            return true;
        } catch (Exception e) {
            privateKey = null;
            publicKey = null;
            certificate = null;
            System.err.println("error loading existing keys");
        }
        return false;
    }

    public void generateCertificate() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, IOException, CertificateException, KeyStoreException, OperatorCreationException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(keysize, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair(); // public/private key pair that we are creating certificate for

        /**
         * some slightly adjusted copy pasta from stackoverflow plus my basic understanding how to create a certificate.
         * bouncy castle documentation is somewhat holey
         */

        Provider bcProvider = new BouncyCastleProvider();
        Security.addProvider(bcProvider);
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        X500Name dnName = new X500Name("CN=Auth");
        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // <-- Using the current timestamp as the certificate serial number
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 25); // <-- 1 Yr validity
        Date endDate = calendar.getTime();
        String signatureAlgorithm = "SHA256WithRSA"; // <-- Use appropriate signature algorithm based on your keyPair algorithm.
        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());
        // Extensions --------------------------
        // Basic Constraints
        BasicConstraints basicConstraints = new BasicConstraints(false); // <-- true for CA, false for EndEntity
        certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints); // Basic Constraints is usually marked as critical.
        // -------------------------------------
        this.certificate = new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner));

        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();


        // save cert & PK
        saveFile(certificate.getEncoded(), CERT_FILENAME);
        saveFile(this.privateKey.getEncoded(), PK_FILENAME);
        saveFile(this.publicKey.getEncoded(), PUB_FILENAME);

        // save KeyStore
        storeKeyStore();
    }

    private void saveKeysInKeystore() throws KeyStoreException {
        char[] pwd = PASS.toCharArray();
        keyStore.setKeyEntry(PK_NAME, this.privateKey, pwd, new java.security.cert.Certificate[]{certificate});
    }

    private synchronized void storeKeyStore() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        char[] pwd = PASS.toCharArray();
        keyStore.store(new FileOutputStream(keyStoreFile), pwd);
    }

    private void saveFile(byte[] data, String fileName) throws IOException {
        String path = createWorkingPath() + fileName;
        FileOutputStream fos = new FileOutputStream(path);
        fos.write(data);
        fos.close();
    }


    public X509Certificate getMyX509Certificate() {
        return certificate;
    }

    public String getNewUUID() throws SqlQueriesException {
        UUID uuid = UUID.randomUUID();
        while (certificateDao.existsUUID(uuid.toString()))
            uuid = UUID.randomUUID();
        return uuid.toString();
    }

    private SSLContext getSSLContext() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        // todo android der Hurensohn
        KeyManagerFactory kmf = null;
        try {
            kmf = KeyManagerFactory.getInstance("X509");
        } catch (Exception e) {
            System.err.println("CertificateManager.getSSLContext(X509).failed.trying(SunX509)");
        }
        if (kmf == null)
            try {
                kmf = KeyManagerFactory.getInstance("SunX509");
            } catch (Exception e) {
                System.err.println("CertificateManager.getSSLContext(SunX509).failed");
            }
        kmf.init(keyStore, PASS.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(keyStore);
        SSLContext sslContext = null;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }


    public Socket getClientSocket() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLSocketFactory factory = getSSLContext().getSocketFactory();// (SSLSocketFactory) SSLSocketFactory.getDefault();
        return factory.createSocket();
    }

    public byte[] encrypt(String original) throws NoSuchPaddingException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, ClassNotFoundException {
        return Cryptor.encrypt(publicKey, original);
    }

    public String decrypt(byte[] encrypted) throws NoSuchPaddingException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, ClassNotFoundException {
        return Cryptor.decrypt(privateKey, encrypted);
    }

    public Certificate getTrustedCertificateByUuid(String uuid) throws SqlQueriesException {
        return certificateDao.getTrustedCertificateByUuid(uuid);
    }

    public Certificate getTrustedCertificateById(Long id) throws SqlQueriesException {
        return certificateDao.getTrustedCertificateById(id);
    }

    public Certificate getCertificateById(Long id) throws SqlQueriesException {
        return certificateDao.getCertificateById(id);

    }

    public void updateCertificate(Certificate certificate) throws SqlQueriesException {
        certificateDao.updateCertificate(certificate);
    }

    public List<Certificate> getTrustedCertificates() throws SqlQueriesException {
        return certificateDao.getTrustedCertificates();
    }

    public Certificate addAnswerUuid(Long certId, String ownUuid) throws SqlQueriesException {
        certificateDao.lockWrite();
        Certificate partnerCertificate = certificateDao.getTrustedCertificateById(certId);
        partnerCertificate.setAnswerUuid(ownUuid);
        certificateDao.updateCertificate(partnerCertificate);
        certificateDao.unlockWrite();
        return partnerCertificate;
    }

    public List<Certificate> getCertificatesByGreeting(String greeting) throws SqlQueriesException {
        certificateDao.lockRead();
        List<Certificate> certs = certificateDao.getCertificatesByGreeting(greeting);
        certificateDao.unlockRead();
        return certs;
    }

    public void deleteCertificate(Certificate certificate) throws SqlQueriesException {
        if (certificate.getId().v() != null) {
            certificateDao.delete(certificate.getId().v());
        }
    }

    public Certificate getCertificateByBytes(byte[] certBytes) throws SqlQueriesException {
        certificateDao.lockRead();
        Certificate certificate = certificateDao.getCertificateByBytes(certBytes);
        certificateDao.unlockRead();
        return certificate;
    }

    public Socket createSocket() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        SSLSocket socket = (SSLSocket) getSSLContext().getSocketFactory().createSocket();
        try {
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});
        } catch (Exception e) {
            try {
                socket.setEnabledProtocols(new String[]{"TLSv1.1"});
            } catch (Exception ee) {
                socket.setEnabledProtocols(new String[]{"TLSv1"});
            }
        }
        return socket;
    }

    public ServerSocket createServerSocket() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        SSLServerSocket socket = (SSLServerSocket) getSSLContext().getServerSocketFactory().createServerSocket();
        try {
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});
        } catch (Exception e) {
            try {
                socket.setEnabledProtocols(new String[]{"TLSv1.1"});
            } catch (Exception ee) {
                socket.setEnabledProtocols(new String[]{"TLSv1"});
            }
        }
        return socket;
    }

    public Certificate getTrustedCertificateByHash(String hash) throws SqlQueriesException {
        certificateDao.lockRead();
        Certificate certificate = certificateDao.getTrustedCertificateByHash(hash);
        certificateDao.unlockRead();
        return certificate;
    }


    public List<Certificate> getAllCertificateDetails() throws SqlQueriesException {
        return certificateDao.getAllCertificateDetails();
    }

    public void maintenance() throws SqlQueriesException {
        certificateDao.maintenance();
    }
}
