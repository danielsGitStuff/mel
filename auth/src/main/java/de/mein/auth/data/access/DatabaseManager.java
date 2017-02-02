package de.mein.auth.data.access;

import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.db.*;
import de.mein.auth.data.db.dao.ApprovalDao;
import de.mein.auth.data.db.dao.ServiceDao;
import de.mein.auth.data.db.dao.ServiceTypeDao;
import de.mein.execute.SqliteExecutor;
import de.mein.sql.*;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by xor on 4/26/16.
 */
public class DatabaseManager extends FileRelatedManager {
    public static final String DB_FILENAME = "meinauth.db";
    private final Connection dbConnection;
    private final SQLQueries sqlQueries;
    private ServiceTypeDao serviceTypeDao;
    private ServiceDao serviceDao;
    private final ApprovalDao approvalDao;

    public DatabaseManager(File workingDirectory) throws SQLException, ClassNotFoundException, IOException {
        super(workingDirectory);
        //init DB stuff
        this.dbConnection = SQLConnection.createSqliteConnection(new File(createWorkingPath() + DB_FILENAME));
        //check DB stuff
        SqliteExecutor sqliteExecutor = new SqliteExecutor(dbConnection);
        if (!sqliteExecutor.checkTablesExist("servicetype", "service", "approval", "certificate","transfer")) {
            //find sql file in workingdir
            sqliteExecutor.executeResource("/sql.sql");
            hadToInitialize = true;
        }
        this.sqlQueries = new SQLQueries(dbConnection, new RWLock());
        serviceTypeDao = new ServiceTypeDao(sqlQueries);
        approvalDao = new ApprovalDao(sqlQueries);
        serviceDao = new ServiceDao(sqlQueries);
    }

    public Connection getDbConnection() {
        return dbConnection;
    }

    public ServiceType getServiceTypeByName(String name) throws SqlQueriesException {
        return serviceTypeDao.getTypeByName(name);
    }

    public ServiceType createServiceType(String name, String description) throws SqlQueriesException {
        ServiceType serviceType = new ServiceType().setType(name).setDescription(description);
        long id = sqlQueries.insert(serviceType);
        return serviceType.setId(id);
    }

    public SQLQueries getSqlQueries() {
        return sqlQueries;
    }

    public Service getServiceByUuid(String uuid) throws SqlQueriesException {
        Service dummy = new Service();
        String where = dummy.getUuid().k() + "=?";
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(uuid);
        List<SQLTableObject> sqlTableObjects = sqlQueries.load(dummy.getAllAttributes(), dummy, where, whereArgs);
        if (sqlTableObjects.size() > 0)
            return (Service) sqlTableObjects.get(0);
        return null;
    }

    public Service createService(Long typeId, String name) throws SqlQueriesException {
        Service service = new Service()
                .setUuid(UUID.randomUUID().toString())
                .setTypeId(typeId)
                .setName(name);
        Long id = sqlQueries.insert(service);
        return service.setId(id);
    }

    public void grant(Long serviceId, Long certificateId) throws SqlQueriesException {
        Approval approval = new Approval();
        approval.setServiceid(serviceId);
        approval.setCertificateId(certificateId);
        sqlQueries.insert(approval);
    }


    public List<Service> getAllowedServices(Long certificateId) throws SqlQueriesException {
        return approvalDao.getAllowedServices(certificateId);
    }

    public boolean isApproved(Long certificateId, Long serviceId) throws SqlQueriesException {
        return approvalDao.isApproved(certificateId, serviceId);
    }

    public List<ServiceJoinServiceType> getAllServices() throws SqlQueriesException {
        return serviceDao.getAllServices();
    }

    public List<Approval> getAllApprovals() throws SqlQueriesException {
        return approvalDao.getAllApprovals();
    }

    public DatabaseManager saveApprovals(ApprovalMatrix approvalMatrix) throws SqlQueriesException {
        approvalDao.clear();
        for (Long serviceId : approvalMatrix.getMatrix().keySet()) {
            for (Approval approval : approvalMatrix.getMatrix().get(serviceId).values()) {
                approvalDao.insertApproval(approval);
            }
        }
        return this;
    }

    public List<Service> getServicesByType(Long typeId) throws SqlQueriesException {
        Service dummy = new Service();
        String where = dummy.getTypeId().k() + "=?";
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(typeId);
        List<SQLTableObject> sqlTableObjects = sqlQueries.load(dummy.getAllAttributes(), dummy, where, whereArgs);
        List<Service> result = new ArrayList<>();
        for (SQLTableObject service : sqlTableObjects) {
            result.add((Service) service);
        }
        return result;
    }

    @SuppressWarnings("Duplicates")
    public List<ServiceJoinServiceType> getAllowedServicesJoinTypes(Long certId) throws SqlQueriesException {
        Service s = new Service();
        ServiceType t = new ServiceType();
        Approval a = new Approval();
        Certificate c = new Certificate();
        ServiceJoinServiceType dummy = new ServiceJoinServiceType();
        String query = "select s." + s.getId().k() + ",s." + s.getUuid().k() + ",s." + s.getName().k() + ", t." + t.getType().k() + ", t." + t.getDescription().k()
                + " from " + s.getTableName() + " s"
                + " left join " + t.getTableName() + " t on s." + s.getTypeId().k() + "=t." + t.getId().k()
                + " left join " + a.getTableName() + " a on s." + s.getId().k() + "=a." + a.getServiceid().k()
                + " left join " + c.getTableName() + " c on c." + c.getId().k() + "=a." + a.getCertificateId().k() + " where c." + c.getId().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(certId);
        List<SQLTableObject> result = sqlQueries.loadString(dummy.getAllAttributes(), dummy, query, args);
        List<ServiceJoinServiceType> services = new ArrayList<>();
        for (SQLTableObject sqlTableObject : result) {
            services.add((ServiceJoinServiceType) sqlTableObject);
        }
        return services;
    }

    public void updateService(Service service) throws SqlQueriesException {
        serviceDao.update(service);
    }

    public void deleteService(Long serviceId) throws SqlQueriesException {
        serviceDao.delete(serviceId);
    }

    public void revoke(Long serviceId, Long certificateId) throws SqlQueriesException {
        Approval approval = new Approval();
        String where = approval.getServiceid().k() + "=? and " + approval.getCertificateId().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(serviceId);
        args.add(certificateId);
        sqlQueries.delete(approval, where, args);
    }
}
