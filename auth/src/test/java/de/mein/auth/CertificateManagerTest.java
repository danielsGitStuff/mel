package de.mein.auth;

import de.mein.Lok;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.file.AFile;
import de.mein.sql.SqlQueriesException;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by xor on 3/28/16.
 */
public class CertificateManagerTest {
    private X509Certificate genCert() throws Exception {
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
        certGen.setSerialNumber(BigInteger.valueOf(5));
        certGen.setIssuerDN(new X509Principal("CN=" + "mein.auth.test.junit, OU=None, O=None L=None, C=None"));
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30));
        certGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365 * 10)));
        certGen.setSubjectDN(new X509Principal("CN=" + "mein.auth.TEST.JUNIT, OU=None, O=None L=None, C=None"));
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair(); // public/private key pair that we are creating certificate for
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        certGen.setPublicKey(publicKey);
        certGen.setSignatureAlgorithm("SHA512WITHRSA");
        // create Cert
        X509Certificate certificate = certGen.generateX509Certificate(privateKey);
        return certificate;
    }


    private CertificateManager certificateManager;

    @Before
    public void init() throws Exception, SqlQueriesException {
        CertificateManager.deleteDirectory(new File("z_test"));
        certificateManager = createCertificateManager(new MeinAuthSettings().setWorkingDirectory((new File("z_test"))));
    }

    public static CertificateManager createCertificateManager(MeinAuthSettings meinAuthSettings) throws SQLException, ClassNotFoundException, IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, SignatureException, InvalidKeyException, SqlQueriesException, OperatorCreationException {
        DatabaseManager databaseManager = new DatabaseManager(meinAuthSettings);
        return new CertificateManager(meinAuthSettings.getWorkingDirectory(), databaseManager.getSqlQueries(), 1024);
    }

    @After
    public void finish() throws IOException {
        CertificateManager.deleteDirectory(new File("z_test"));
    }

    @Test
    public void loadCertificateFromBytes() throws Exception {
        X509Certificate x509Certificate = genCert();
        byte[] bytes = x509Certificate.getEncoded();
        X509Certificate loadedCert = CertificateManager.loadX509CertificateFromBytes(bytes);
        assertEquals(x509Certificate, loadedCert);
    }

    @Test
    public void registerCertificate() throws Exception, SqlQueriesException {
        X509Certificate x509Certificate = genCert();
        byte[] byteCert = x509Certificate.getEncoded();
        UUID uuid = UUID.randomUUID();
        Certificate buildCertificate = certificateManager.importCertificate(x509Certificate, "testname", uuid.toString(), null, null, null, "huiii");
        Certificate dbCertificate = certificateManager.getTrustedCertificates().get(0);
        X509Certificate dbX509Certificate = CertificateManager.loadX509CertificateFromBytes(dbCertificate.getCertificate().v());
        assertEquals(x509Certificate, dbX509Certificate);
        assertNotNull(buildCertificate.getId().v());
        buildCertificate.setId(dbCertificate.getId().v());
        assertEquals(Arrays.toString(byteCert), Arrays.toString(dbCertificate.getCertificate().v()));
    }

    public static CertificateManager createCertificateManager(File wd) throws SQLException, ClassNotFoundException, IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, SignatureException, InvalidKeyException, SqlQueriesException, OperatorCreationException {
        return createCertificateManager(wd, true);
    }

    private static CertificateManager createCertificateManager(File wd, boolean delete) throws SQLException, IOException, ClassNotFoundException, CertificateException, NoSuchAlgorithmException, KeyStoreException, SignatureException, SqlQueriesException, InvalidKeyException, OperatorCreationException {
        if (delete)
            CertificateManager.deleteDirectory(wd);
        wd.mkdirs();
        MeinAuthSettings meinAuthSettings = new MeinAuthSettings().setWorkingDirectory(wd);
        DatabaseManager databaseManager = new DatabaseManager(meinAuthSettings);
        return new CertificateManager(meinAuthSettings.getWorkingDirectory(), databaseManager.getSqlQueries(), 1024);
    }

    @Test
    public void keySerialization() throws Exception, SqlQueriesException {
        CertificateManager certificateManager = createCertificateManager(new File("testDir"));
        certificateManager.generateCertificate();
        PrivateKey pk1 = certificateManager.getPrivateKey();
        PublicKey pubk1 = certificateManager.getPublicKey();
        X509Certificate cert1 = certificateManager.getMyX509Certificate();
        certificateManager = createCertificateManager(new File("testDir"), false);
        PrivateKey pk2 = certificateManager.getPrivateKey();
        PublicKey pubk2 = certificateManager.getPublicKey();
        X509Certificate cert2 = certificateManager.getMyX509Certificate();
        Lok.debug("CertificateManagerTest.keySerialization");
        assertEquals(pk1, pk2);
        assertEquals(pubk1, pubk2);
        assertEquals(cert1, cert2);
    }


    @Test
    public void storeCertificate() throws Exception, SqlQueriesException {
        certificateManager.generateCertificate();
        Certificate certificate = new Certificate();
        certificate.setCertificate(certificateManager.getMyX509Certificate().getEncoded());
        certificate.setAnswerUuid("some address");
        UUID uuid = UUID.randomUUID();
        certificate.setUuid(uuid.toString());
        Lok.debug("CertificateManagerTest.storeCertificateDB");
    }

}
