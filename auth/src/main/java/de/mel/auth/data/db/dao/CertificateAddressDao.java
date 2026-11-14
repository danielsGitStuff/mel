package de.mel.auth.data.db.dao;

import de.mel.auth.data.db.CertificateAddress;
import de.mel.sql.Dao;
import de.mel.sql.ISQLQueries;
import de.mel.sql.SqlQueriesException;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CertificateAddressDao extends Dao.ConnectionLockingDao {
    public CertificateAddressDao(ISQLQueries sqlQueries) {
        super(sqlQueries);
    }

    public CertificateAddressDao(ISQLQueries sqlQueries, boolean lock) {
        super(sqlQueries, lock);
    }

    public List<CertificateAddress> getAddressesForCertificate(long certificateId) throws SqlQueriesException {
        CertificateAddress dummy = new CertificateAddress();
        return sqlQueries.load(
                dummy.getAllAttributes(),
                dummy,
                dummy.getCertificateId().k() + "=?",
                ISQLQueries.args(certificateId),
                "order by " + dummy.getPriority().k() + " ASC, " + dummy.getId().k() + " ASC"
        );
    }

    public void replaceAddressesForCertificate(long certificateId, List<CertificateAddress> addresses) throws SqlQueriesException {
        boolean inTx = false;
        try {
            sqlQueries.beginTransaction();
            inTx = true;
            CertificateAddress dummy = new CertificateAddress();
            sqlQueries.delete(dummy, dummy.getCertificateId().k() + "=?", ISQLQueries.args(certificateId));
            if (addresses != null) {
                Set<String> seen = new HashSet<>();
                int priority = 0;
                for (CertificateAddress addr : addresses) {
                    if (addr == null || addr.getAddress().v() == null)
                        continue;
                    String key = addr.getAddress().v() + "|" + addr.getPort().v();
                    if (!seen.add(key))
                        continue;
                    CertificateAddress row = new CertificateAddress()
                            .setCertificateId(certificateId)
                            .setAddress(addr.getAddress().v())
                            .setPort(addr.getPort().v())
                            .setCertPort(addr.getCertPort().v())
                            .setPriority(priority++);
                    long id = sqlQueries.insert(row);
                    addr.setId(id).setCertificateId(certificateId).setPriority(row.getPriority().v());
                }
            }
            sqlQueries.commit();
        } catch (SQLException e) {
            if (inTx) {
                try {
                    sqlQueries.rollback();
                } catch (SqlQueriesException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw new SqlQueriesException(e);
        } catch (SqlQueriesException | RuntimeException e) {
            if (inTx) {
                try {
                    sqlQueries.rollback();
                } catch (SqlQueriesException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
    }
}
