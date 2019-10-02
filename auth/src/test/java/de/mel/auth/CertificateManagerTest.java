package de.mel.auth;

import de.mel.Lok;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.access.CertificateCreator;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.access.DatabaseManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.file.AFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.tools.F;
import de.mel.sql.SqlQueriesException;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
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
        KeyPair keyPair = CertificateCreator.generateKeyPair(1024);
        return CertificateCreator.generateCertificate(keyPair, "lala");
    }


    private CertificateManager certificateManager;
    private final File TEST_DIR = new File("cert_test");

    @Before
    public void before() throws Exception, SqlQueriesException {
        AFile.configure(new DefaultFileConfiguration());
        F.rmRf(TEST_DIR);
        TEST_DIR.mkdirs();
        certificateManager = createCertificateManager(new MelAuthSettings().setWorkingDirectory(TEST_DIR));
        Lok.debug();
    }

    public static CertificateManager createCertificateManager(MelAuthSettings melAuthSettings) throws SQLException, ClassNotFoundException, IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, SignatureException, InvalidKeyException, SqlQueriesException, OperatorCreationException, InvalidKeySpecException {
        DatabaseManager databaseManager = new DatabaseManager(melAuthSettings);
        return new CertificateManager(melAuthSettings.getWorkingDirectory(), databaseManager.getSqlQueries(), 1024);
    }

    @After
    public void after() throws IOException {
        F.rmRf(TEST_DIR);
    }

    @Test
    public void loadCertificateFromBytes() throws Exception {
        X509Certificate x509Certificate = genCert();
        byte[] bytes = x509Certificate.getEncoded();
        X509Certificate loadedCert = CertificateManager.loadX509CertificateFromBytes(bytes);
        assertEquals(x509Certificate, loadedCert);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void registerCertificateFail() throws Exception, SqlQueriesException {
        X509Certificate x509Certificate = genCert();
        byte[] byteCert = x509Certificate.getEncoded();
        UUID uuid = CertificateManager.randomUUID();
        Certificate buildCertificate = certificateManager.importCertificate(x509Certificate, "testname", uuid.toString(), null, null, null);
        // the next line should return n zero length list, cause the certificate is not trusted now
        Certificate dbCertificate = certificateManager.getTrustedCertificates().get(0);
        X509Certificate dbX509Certificate = CertificateManager.loadX509CertificateFromBytes(dbCertificate.getCertificate().v());
        assertEquals(x509Certificate, dbX509Certificate);
        assertNotNull(buildCertificate.getId().v());
        buildCertificate.setId(dbCertificate.getId().v());
        assertEquals(Arrays.toString(byteCert), Arrays.toString(dbCertificate.getCertificate().v()));
    }

    @Test
    public void registerCertificate() throws Exception, SqlQueriesException {
        X509Certificate x509Certificate = genCert();
        byte[] byteCert = x509Certificate.getEncoded();
        UUID uuid = CertificateManager.randomUUID();
        Certificate buildCertificate = certificateManager.importCertificate(x509Certificate, "testname", uuid.toString(), null, null, null);
        certificateManager.trustCertificate(buildCertificate.getId().v(), true);
        Certificate dbCertificate = certificateManager.getTrustedCertificates().get(0);
        X509Certificate dbX509Certificate = CertificateManager.loadX509CertificateFromBytes(dbCertificate.getCertificate().v());
        assertEquals(x509Certificate, dbX509Certificate);
        assertNotNull(buildCertificate.getId().v());
        buildCertificate.setId(dbCertificate.getId().v());
        assertEquals(Arrays.toString(byteCert), Arrays.toString(dbCertificate.getCertificate().v()));
    }

    public static CertificateManager createCertificateManager(File wd) throws SQLException, ClassNotFoundException, IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, SignatureException, InvalidKeyException, SqlQueriesException, OperatorCreationException, InvalidKeySpecException {
        return createCertificateManager(wd, true);
    }

    private static CertificateManager createCertificateManager(File wd, boolean delete) throws SQLException, IOException, ClassNotFoundException, CertificateException, NoSuchAlgorithmException, KeyStoreException, SignatureException, SqlQueriesException, InvalidKeyException, OperatorCreationException, InvalidKeySpecException {
        if (delete)
            CertificateManager.deleteDirectory(wd);
        wd.mkdirs();
        MelAuthSettings melAuthSettings = new MelAuthSettings().setWorkingDirectory(wd);
        DatabaseManager databaseManager = new DatabaseManager(melAuthSettings);
        return new CertificateManager(melAuthSettings.getWorkingDirectory(), databaseManager.getSqlQueries(), 1024);
    }

    @Test
    public void keySerialization() throws Exception, SqlQueriesException {
        CertificateManager certificateManager = createCertificateManager(TEST_DIR);
        certificateManager.generateCertificate();
        PrivateKey pk1 = certificateManager.getPrivateKey();
        PublicKey pubk1 = certificateManager.getPublicKey();
        X509Certificate cert1 = certificateManager.getMyX509Certificate();
        certificateManager = createCertificateManager(TEST_DIR, false);
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
        UUID uuid = CertificateManager.randomUUID();
        certificate.setUuid(uuid.toString());
        Lok.debug("CertificateManagerTest.storeCertificateDB");
    }

}
