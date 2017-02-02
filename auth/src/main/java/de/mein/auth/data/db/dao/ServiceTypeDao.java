package de.mein.auth.data.db.dao;

import de.mein.auth.data.db.ServiceType;
import de.mein.sql.Dao;
import de.mein.sql.SQLQueries;
import de.mein.sql.SQLTableObject;
import de.mein.sql.SqlQueriesException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 4/27/16.
 */
public class ServiceTypeDao extends Dao {
    public ServiceTypeDao(SQLQueries sqlQueries) {
        super(sqlQueries, false);
    }

    public ServiceType getTypeByName(String name) throws SqlQueriesException {
        ServiceType dummy = new ServiceType();
        String where = dummy.getType().k() + "=?";
        List<Object> whereargs = new ArrayList<>();
        whereargs.add(name);
        List<SQLTableObject> sqlTableObjects = sqlQueries.load(dummy.getAllAttributes(), dummy, where, whereargs);
        if (sqlTableObjects.size()>0)
            return (ServiceType) sqlTableObjects.get(0);
        return null;
    }

    public ServiceType insertType(ServiceType serviceType) throws SqlQueriesException {
        Long id =  sqlQueries.insert(serviceType);
        return serviceType.setId(id);
    }


}
