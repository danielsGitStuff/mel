package de.mein.auth.data.access;

import de.mein.Lok;
import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.db.*;
import de.mein.auth.data.db.dao.ApprovalDao;
import de.mein.auth.data.db.dao.ServiceDao;
import de.mein.auth.data.db.dao.ServiceTypeDao;
import de.mein.execute.SqliteExecutor;
import de.mein.sql.*;
import de.mein.sql.conn.SQLConnector;
import de.mein.sql.transform.SqlResultTransformer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Takes care about Services and Approvals (which Certificate is permitted to talk to which Service).<br>
 */
public final class DatabaseManager extends FileRelatedManager {
    public static final String DB_FILENAME = "meinauth.db";
    protected final ISQLQueries ISQLQueries;
    protected final ServiceTypeDao serviceTypeDao;
    protected final ServiceDao serviceDao;
    protected final ApprovalDao approvalDao;

    public ServiceType getServiceTypeById(Long id) throws SqlQueriesException {
        return serviceTypeDao.getServiceTypeById(id);
    }

    public String getServiceNameByServiceUuid(String uuid) throws SqlQueriesException {
        ServiceType serviceType = new ServiceType();
        Service service = new Service();
        String query = "select " + serviceType.getType().k()
                + " from " + service.getTableName() + " s left join " + serviceType.getTableName() + " t on s."
                + service.getTypeId().k() + "=t." + serviceType.getId().k()
                + " where " + service.getUuid().k() + "=?";
        String name = getSqlQueries().queryValue(query, String.class, de.mein.sql.ISQLQueries.whereArgs(uuid));
        return name;
    }

    public void shutDown() {
        getSqlQueries().onShutDown();
    }

    public interface SqlInputStreamInjector {
        InputStream createSqlFileInputStream();
    }


    private static SqlInputStreamInjector sqlInputStreamInjector = () -> DatabaseManager.class.getResourceAsStream("/de/mein/auth/sql.sql");


    public static void setSqlInputStreamInjector(SqlInputStreamInjector sqlInputStreamInjector) {
        DatabaseManager.sqlInputStreamInjector = sqlInputStreamInjector;
    }

    public interface SQLConnectionCreator {
        ISQLQueries createConnection(DatabaseManager databaseManager) throws SQLException, ClassNotFoundException;
    }

    private static SQLConnectionCreator sqlConnectionCreator = databaseManager -> {
        File f = new File(databaseManager.createWorkingPath() + DB_FILENAME);
        Lok.debug("opening database: " + f.getAbsolutePath());
        SQLQueries sqlQueries = new SQLQueries(SQLConnector.createSqliteConnection(f), true, new RWLock(), SqlResultTransformer.sqliteResultSetTransformer());
        // turn on foreign keys
        try {
            sqlQueries.execute("PRAGMA foreign_keys = ON;", null);
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
        return sqlQueries;
    };

    public static void setSqlConnectionCreator(SQLConnectionCreator sqlConnectionCreator) {
        DatabaseManager.sqlConnectionCreator = sqlConnectionCreator;
    }

    public DatabaseManager(MeinAuthSettings meinAuthSettings) throws SQLException, ClassNotFoundException, IOException {
        super(meinAuthSettings.getWorkingDirectory());
        //android der Hurensohn
        //init DB stuff
        this.ISQLQueries = sqlConnectionCreator.createConnection(this);
        //check DB stuff
        SqliteExecutor sqliteExecutor = new SqliteExecutor(ISQLQueries.getSQLConnection());
        if (!sqliteExecutor.checkTablesExist("servicetype", "service", "approval", "certificate")) {
            //find sql file in workingdir
            sqliteExecutor.executeStream(sqlInputStreamInjector.createSqlFileInputStream());
            hadToInitialize = true;
        }
        serviceTypeDao = new ServiceTypeDao(ISQLQueries);
        approvalDao = new ApprovalDao(ISQLQueries);
        serviceDao = new ServiceDao(ISQLQueries);
    }


    public ServiceType getServiceTypeByName(String name) throws SqlQueriesException {
        return serviceTypeDao.getTypeByName(name);
    }

    public ServiceType createServiceType(String name, String description) throws SqlQueriesException {
        ServiceType serviceType = new ServiceType().setType(name).setDescription(description);
        long id = ISQLQueries.insert(serviceType);
        return serviceType.setId(id);
    }

    public ISQLQueries getSqlQueries() {
        return ISQLQueries;
    }

    public Service getServiceByUuid(String uuid) throws SqlQueriesException {
        Service dummy = new Service();
        String where = dummy.getUuid().k() + "=?";
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(uuid);
        List<SQLTableObject> sqlTableObjects = ISQLQueries.load(dummy.getAllAttributes(), dummy, where, whereArgs);
        if (sqlTableObjects.size() > 0)
            return (Service) sqlTableObjects.get(0);
        return null;
    }

    public Service createService(Long typeId, String name) throws SqlQueriesException {
        Service service = new Service()
                .setUuid(UUID.randomUUID().toString())
                .setTypeId(typeId)
                .setName(name)
                .setActive(true);
        Long id = ISQLQueries.insert(service);
        return service.setId(id);
    }

    public void grant(Long serviceId, Long certificateId) throws SqlQueriesException {
        Approval approval = new Approval();
        approval.setServiceId(serviceId);
        approval.setCertificateId(certificateId);
        ISQLQueries.insert(approval);
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

    public List<Service> getActiveServicesByType(Long typeId) throws SqlQueriesException {
        Service dummy = new Service();
        String where = dummy.getTypeId().k() + "=? and " + dummy.getActivePair().k() + "=?";
        List<SQLTableObject> sqlTableObjects = ISQLQueries.load(dummy.getAllAttributes(), dummy, where, de.mein.sql.ISQLQueries.whereArgs(typeId, true));
        List<Service> result = new ArrayList<>();
        for (SQLTableObject service : sqlTableObjects) {
            result.add((Service) service);
        }
        return result;
    }

    public List<Service> getServicesByType(Long typeId) throws SqlQueriesException {
        Service dummy = new Service();
        String where = dummy.getTypeId().k() + "=?";
        List<Object> whereArgs = new ArrayList<>();
        whereArgs.add(typeId);
        List<SQLTableObject> sqlTableObjects = ISQLQueries.load(dummy.getAllAttributes(), dummy, where, whereArgs);
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
        String query = "select s." + s.getId().k() + ",s." + s.getUuid().k() + ",s." + s.getName().k() + ", t." + t.getType().k() + ", t." + t.getDescription().k() + ", s." + s.getActivePair().k()
                + " from " + s.getTableName() + " s"
                + " left join " + t.getTableName() + " t on s." + s.getTypeId().k() + "=t." + t.getId().k()
                + " left join " + a.getTableName() + " a on s." + s.getId().k() + "=a." + a.getServiceId().k()
                + " left join " + c.getTableName() + " c on c." + c.getId().k() + "=a." + a.getCertificateId().k() + " where c." + c.getId().k() + "=? and s." + s.getActivePair().k() + "=?";
        List<SQLTableObject> result = ISQLQueries.loadString(dummy.getAllAttributes(), dummy, query, de.mein.sql.ISQLQueries.whereArgs(certId, true));
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
        String where = approval.getServiceId().k() + "=? and " + approval.getCertificateId().k() + "=?";
        List<Object> args = new ArrayList<>();
        args.add(serviceId);
        args.add(certificateId);
        ISQLQueries.delete(approval, where, args);
    }
}
