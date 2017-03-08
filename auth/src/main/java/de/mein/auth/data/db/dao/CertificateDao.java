package de.mein.auth.data.db.dao;

import de.mein.auth.data.db.Certificate;
import de.mein.sql.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 4/11/16.
 */
public class CertificateDao extends Dao.ConnectionLockingDao {
    public CertificateDao(ISQLQueries ISQLQueries) {
        super(ISQLQueries);
    }

    public CertificateDao(ISQLQueries ISQLQueries, boolean lock) {
        super(ISQLQueries, lock);
    }

    public List<Certificate> getCertificates() throws SqlQueriesException {
        Certificate dummy = new Certificate();
        List<Certificate> re = sqlQueries.load(dummy.getAllAttributes(), dummy, null, null);
        return re;
    }

    public Certificate insertCertificate(Certificate certificate) throws SqlQueriesException {
        return certificate.setId(sqlQueries.insert(certificate));
    }

    public boolean existsUUID(String uuid) throws SqlQueriesException {
        Certificate dummy = new Certificate();
        String where = dummy.getUuid().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(uuid);
        List<SQLTableObject> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        return result.size() > 0;
    }

    public void updateCertificate(Certificate certificate) throws SqlQueriesException {
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(certificate.getId().v());
        sqlQueries.update(certificate, certificate.getId().k() + "=?", whereArgs);
    }

    public Certificate getCertificateByUuid(String uuid) throws SqlQueriesException {
        Certificate dummy = new Certificate();
        String where = dummy.getUuid().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(uuid);
        List<SQLTableObject> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        return (Certificate) result.get(0);
    }

    public Certificate getCertificateById(Long id) throws SqlQueriesException {
        Certificate dummy = new Certificate();
        String where = dummy.getId().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(id);
        List<SQLTableObject> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        if (result.size() == 1)
            return (Certificate) result.get(0);
        return null;
    }

    public void delete(Long id) throws SqlQueriesException {
        Certificate dummy = new Certificate().setId(id);
        List<Object> args = new ArrayList<>();
        args.add(id);
        sqlQueries.delete(dummy, dummy.getId().k() + "=?", args);
    }

    public List<Certificate> getCertificatesByGreeting(String greeting) throws SqlQueriesException {
        Certificate dummy = new Certificate();
        String where = dummy.getGreeting().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(greeting);
        List<SQLTableObject> re = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        List<Certificate> result = new ArrayList<>();
        for (SQLTableObject cert : re) {
            result.add((Certificate) cert);
        }
        return result;
    }

    public Certificate getCertificateByBytes(byte[] certBytes) throws SqlQueriesException {
        Certificate dummy = new Certificate();
        String where = dummy.getCertificate().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(certBytes);
        List<SQLTableObject> result = sqlQueries.load(dummy.getAllAttributes(), dummy, where, args);
        if (result.size() == 1)
            return (Certificate) result.get(0);
        return null;
    }

    public void trustCertificate(Long certId, boolean trusted) throws SqlQueriesException {
        Certificate cert = new Certificate();
        String sql = "update " + cert.getTableName() + " set " + cert.getTrusted().k() + "=? where "
                + cert.getId().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(trusted);
        args.add(certId);
        sqlQueries.execute(sql, args);
        System.out.println("CertificateDao.trustCertificate");
    }
}
