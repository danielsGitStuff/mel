package de.mein.auth.data.db.dao;

import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.data.db.ServiceType;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 6/25/16.
 */
public class ServiceDao extends Dao {
    public ServiceDao(ISQLQueries ISQLQueries) {
        super(ISQLQueries);
    }

    public List<ServiceJoinServiceType> getAllServices() throws SqlQueriesException {
        Service s = new Service();
        ServiceType t = new ServiceType();
        ServiceJoinServiceType dummy = new ServiceJoinServiceType();
        String query = "select s." + s.getId().k() + ",s." + s.getName().k() + ",s." + s.getUuid().k() + ", t." + t.getType().k() + ", t." + t.getDescription().k() + " from service s left join servicetype t on s.typeid=t.id";
        List<SQLTableObject> result = sqlQueries.loadString(dummy.getAllAttributes(), dummy, query, null);
        List<ServiceJoinServiceType> services = new ArrayList<>();
        for (SQLTableObject sqlTableObject : result) {
            services.add((ServiceJoinServiceType) sqlTableObject);
        }
        return services;
    }


    public void update(Service service) throws SqlQueriesException {
        List<Object> args = new ArrayList<>();
        args.add(service.getId().v());
        sqlQueries.update(service, service.getId().k() + "=?", args);
    }

    public void delete(Long serviceId) throws SqlQueriesException {
        Service service = new Service();
        List<Object> args = new ArrayList<>();
        args.add(serviceId);
        sqlQueries.delete(service, service.getId().k() + "=?", args);
    }
}
