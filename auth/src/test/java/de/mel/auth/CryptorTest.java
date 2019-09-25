package de.mel.auth;

import de.mel.Lok;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.tools.Cryptor;
import de.mel.sql.SqlQueriesException;
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
        Lok.debug("CertificateManagerTest.encrypt:" + decrypted);
        assertEquals(original, decrypted);
    }

}
