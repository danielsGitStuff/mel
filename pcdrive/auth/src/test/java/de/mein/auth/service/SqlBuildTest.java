package de.mein.auth.service;

import de.mein.sql.SqlQueriesException;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.sql.SQLException;

/**
 * Created by xor on 05.04.2016.
 */
public class SqlBuildTest {
    @Test
    public void buildDB() throws CertificateException, ClassNotFoundException, NoSuchAlgorithmException, KeyStoreException, SQLException, IOException, SignatureException, InvalidKeyException, SqlQueriesException {
//        CertificateManager certificateManager = new CertificateManager(new File("z_dbTest"));
//        Connection connection = certificateManager.getDbConnection();
//        SqliteExecutor.replaceSchema("meinauth", connection, new FileInputStream(new File("sql.sql")));
//        System.out.println("SqlBuildTest.buildDB");
    }
}
