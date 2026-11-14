package de.mel.auth.data;

import de.mel.auth.data.access.CertificateCreator;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.access.DatabaseManager;
import de.mel.auth.data.db.AddressCandidate;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.CertificateAddress;
import de.mel.auth.data.db.dao.CertificateAddressDao;
import de.mel.auth.tools.AddressParser;
import de.mel.sql.SQLQueries;
import de.mel.sql.conn.SQLConnector;
import de.mel.sql.transform.SqlResultTransformer;
import de.mel.sql.RWLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CertificateAddressTest {
    @TempDir
    File tempDir;

    @Test
    public void daoReplaceOrderingAndCascadeDelete() throws Exception {
        DatabaseManager db = new DatabaseManager(new MelAuthSettings().setWorkingDirectory(tempDir));
        CertificateManager cm = new CertificateManager(tempDir, db.getSqlQueries(), 1024);
        KeyPair kp = CertificateCreator.generateKeyPair(1024);
        X509Certificate x509 = CertificateCreator.generateCertificate(kp, "peer");
        Certificate cert = cm.registerTrusted(x509, "peer", "10.0.0.1", 8888, 8889);

        cm.replaceAlternativeAddresses(cert.getId().v(), Arrays.asList(
                AddressParser.parse("first.example:9000"),
                AddressParser.parse("second.example"),
                AddressParser.parse("first.example:9000")
        ));

        List<CertificateAddress> stored = cm.getAlternativeAddresses(cert.getId().v());
        assertEquals(2, stored.size());
        assertEquals("first.example", stored.get(0).getAddress().v());
        assertEquals(9000, stored.get(0).getPort().v());
        assertEquals(0, stored.get(0).getPriority().v());
        assertEquals("second.example", stored.get(1).getAddress().v());
        assertNull(stored.get(1).getPort().v());
        assertEquals(1, stored.get(1).getPriority().v());

        // Atomic replace overwrites previous list
        cm.replaceAlternativeAddresses(cert.getId().v(), Collections.singletonList(AddressParser.parse("only.example")));
        stored = cm.getAlternativeAddresses(cert.getId().v());
        assertEquals(1, stored.size());
        assertEquals("only.example", stored.get(0).getAddress().v());

        // Deleting certificate cascades
        cm.deleteCertificate(cert);
        assertTrue(cm.getAlternativeAddresses(cert.getId().v()).isEmpty());
        db.shutDown();
    }

    @Test
    public void migratesExistingDatabaseWithoutCertificateAddressTable() throws Exception {
        File dbFile = new File(tempDir, DatabaseManager.DB_FILENAME);
        SQLQueries raw = new SQLQueries(SQLConnector.createSqliteConnection(dbFile), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
        raw.execute("CREATE TABLE servicetype (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL UNIQUE, description TEXT NOT NULL);", null);
        raw.execute("CREATE TABLE service (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL UNIQUE, name TEXT NOT NULL, typeid INTEGER NOT NULL, active INTEGER NOT NULL, lasterror TEXT);", null);
        raw.execute("CREATE TABLE certificate (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL UNIQUE, name TEXT NOT NULL, certificate BLOB NOT NULL, address TEXT, port INTEGER, certport INTEGER, trusted INTEGER, hash TEXT, wifi TEXT);", null);
        raw.execute("CREATE TABLE approval (id INTEGER PRIMARY KEY AUTOINCREMENT, certificateid INTEGER NOT NULL, serviceid INTEGER NOT NULL);", null);
        raw.onShutDown();

        DatabaseManager db = new DatabaseManager(new MelAuthSettings().setWorkingDirectory(tempDir));
        CertificateAddressDao dao = new CertificateAddressDao(db.getSqlQueries());
        assertTrue(dao.getAddressesForCertificate(1L).isEmpty());
        db.shutDown();
    }

    @Test
    public void buildsOrderedDeduplicatedCandidates() {
        Certificate cert = new Certificate()
                .setId(1L)
                .setAddress("10.0.0.1")
                .setPort(8888)
                .setCertDeliveryPort(8889);
        List<AddressCandidate> candidates = CertificateManager.buildCandidates(cert, Arrays.asList(
                AddressParser.parse("10.0.0.1:8888"),
                AddressParser.parse("fallback1.example"),
                AddressParser.parse("fallback2.example:7777")
        ));
        assertEquals(3, candidates.size());
        assertEquals("10.0.0.1", candidates.get(0).getAddress());
        assertEquals(8888, candidates.get(0).getPort());
        assertEquals("fallback1.example", candidates.get(1).getAddress());
        assertEquals(8888, candidates.get(1).getPort());
        assertEquals(8889, candidates.get(1).getPortCert());
        assertEquals("fallback2.example", candidates.get(2).getAddress());
        assertEquals(7777, candidates.get(2).getPort());
        assertEquals(8889, candidates.get(2).getPortCert());
    }
}
