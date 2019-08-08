package de.mein.drive.sql.dao;

import org.jetbrains.annotations.Nullable;

import de.mein.drive.data.FileDistTaskWrapper;
import de.mein.drive.nio.FileDistributionTask;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;

public class FileDistTaskDao extends Dao {
    private FileDistTaskWrapper dummy = new FileDistTaskWrapper();

    public FileDistTaskDao(ISQLQueries sqlQueries) {
        super(sqlQueries);
    }


    public void insert(FileDistributionTask task) throws SqlQueriesException {
        FileDistTaskWrapper wrapper = new FileDistTaskWrapper(task);
        sqlQueries.insert(wrapper);
    }

    public boolean hasContent() throws SqlQueriesException {
        String query = "select count(1) from " + dummy.getTableName() + " where " + dummy.getDone().k() + "=?";
        Long count = sqlQueries.queryValue(query, Long.class, ISQLQueries.whereArgs(false));
        return count > 0;
    }

    public void markDone(long id) throws SqlQueriesException {
        String statement = "update " + dummy.getTableName() + " set " + dummy.getDone().k() + "=? where " + dummy.getId().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.whereArgs(true, id));
    }

    public ISQLResource<FileDistTaskWrapper> resource() throws SqlQueriesException {
        String where = dummy.getDone().k() + "=?";
        return sqlQueries.loadResource(dummy.getAllAttributes(), FileDistTaskWrapper.class, where, ISQLQueries.whereArgs(false));
    }

    public void deleteMarkedDone() throws SqlQueriesException {
        String statement = "delete from " + dummy.getTableName() + " where " + dummy.getDone().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.whereArgs(true));
    }
}
