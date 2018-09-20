package de.mein.auth.data.db.dao;

import de.mein.auth.data.db.Approval;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 4/25/16.
 */
public class ApprovalDao extends Dao {
    public ApprovalDao(ISQLQueries ISQLQueries) {
        super(ISQLQueries, false);
    }

    public Approval insertApproval(Approval approval) throws SqlQueriesException {
        sqlQueries.insert(approval);
        return approval;
    }

    public boolean isApproved(Long certificateId, Long serviceId) throws SqlQueriesException {
        Certificate c = new Certificate();
        Service s = new Service();
        Approval a = new Approval();
        String query = "select s." + s.getId().k() + ",s." + s.getUuid().k() + ",s." + s.getTypeId().k() + ",s." + s.getName().k()
                + " from (" + c.getTableName()
                + " c left join " + a.getTableName() + "  a on c." + c.getId().k() + "=a." + a.getCertificateId().k()
                + ") left join " + s.getTableName() + " s on a." + a.getServiceId().k() + "=s." + s.getId().k()
                + " where c." + c.getId().k() + "=? and s." + s.getId().k() + "=?";
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(certificateId);
        whereArgs.add(serviceId);
        List<SQLTableObject> result = sqlQueries.loadString(s.getAllAttributes(), s, query, whereArgs);
        return result.size() == 1;
    }

    public List<Service> getAllowedServices(Long certificateId) throws SqlQueriesException {
        Certificate c = new Certificate();
        Service s = new Service();
        Approval a = new Approval();
        String query = "select s." + s.getId().k() + ",s." + s.getUuid().k() + ",s." + s.getTypeId().k() + ",s." + s.getName().k() +
                " from (" + c.getTableName() +
                " c inner join " + a.getTableName() + "  a on c." + c.getId().k() + "=a." + a.getCertificateId().k() +
                ") left join " + s.getTableName() + " s on a." + a.getServiceId().k() + "=s." + s.getId().k() + " where c." + c.getId().k() + "=? and " + s.getActivePair().k() + "=?";
        List<SQLTableObject> result = sqlQueries.loadString(s.getAllAttributes(), s, query, ISQLQueries.whereArgs(certificateId,true));
        List<Service> services = new ArrayList<>();
        for (SQLTableObject sqlTableObject : result) {
            services.add((Service) sqlTableObject);
        }
        return services;
    }

    public List<Approval> getAllApprovals() throws SqlQueriesException {
        Approval a = new Approval();
        List<SQLTableObject> result = sqlQueries.load(a.getAllAttributes(), a, null, null);
        List<Approval> approvals = new ArrayList<>();
        for (SQLTableObject sqlTableObject : result) {
            approvals.add((Approval) sqlTableObject);
        }
        return approvals;
    }

    public void clear() throws SqlQueriesException {
        sqlQueries.delete(new Approval(), null, null);
    }
}
