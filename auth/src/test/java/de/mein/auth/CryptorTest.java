package de.mein.auth;

import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.tools.Cryptor;
import de.mein.sql.SqlQueriesException;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 4/18/16.
 */
public class CryptorTest {
    @Test
    public void encryptDecryptString() throws Exception, SqlQueriesException {
        CertificateManager certificateManager = CertificateManagerTest.createCertificateManager(new File("z_test"));
        certificateManager.generateCertificate();
        final String original = "bla";
        byte[] encrypted = Cryptor.encrypt(certificateManager.getPublicKey(), original);
        String decrypted = Cryptor.decrypt(certificateManager.getPrivateKey(), encrypted);
        System.out.println("CertificateManagerTest.encrypt:" + decrypted);
        assertEquals(original, decrypted);
    }

}
