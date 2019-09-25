package de.mel.auth.data.db.dao;

import de.mel.auth.data.db.ServiceType;
import de.mel.sql.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 4/27/16.
 */
public class ServiceTypeDao extends Dao {
    public ServiceTypeDao(ISQLQueries ISQLQueries) {
        super(ISQLQueries, false);
    }

    public ServiceType getTypeByName(String name) throws SqlQueriesException {
        ServiceType dummy = new ServiceType();
        String where = dummy.getType().k() + "=?";
        List<Object> whereargs = new ArrayList<>();
        whereargs.add(name);
        List<SQLTableObject> sqlTableObjects = sqlQueries.load(dummy.getAllAttributes(), dummy, where, whereargs);
        if (sqlTableObjects.size() > 0)
            return (ServiceType) sqlTableObjects.get(0);
        return null;
    }

    public ServiceType insertType(ServiceType serviceType) throws SqlQueriesException {
        Long id = sqlQueries.insert(serviceType);
        return serviceType.setId(id);
    }

    public ServiceType getServiceTypeById(Long id) throws SqlQueriesException {
        ServiceType dummy = new ServiceType();
        ISQLResource<ServiceType> resource = sqlQueries.loadResource(dummy.getAllAttributes(), ServiceType.class, dummy.getId().k() + "=?", ISQLQueries.args(id));
        ServiceType result = resource.getNext();
        resource.close();
        return result;
    }


}
