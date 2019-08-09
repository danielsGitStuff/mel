package de.mein.drive.sql.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.drive.bash.FsBashDetails;
import de.mein.drive.data.FileDistTaskWrapper;
import de.mein.drive.nio.FileDistributionTask;
import de.mein.sql.Dao;
import de.mein.sql.ISQLQueries;
import de.mein.sql.SqlQueriesException;

/**
 * You cannot put a JSON into a database on Android
 */
public class FileDistTaskDao extends Dao {
    private FileDistTaskWrapper dummy = new FileDistTaskWrapper();
    private FileDistTaskWrapper.FileWrapper dummyFW = new FileDistTaskWrapper.FileWrapper();

    public FileDistTaskDao(ISQLQueries sqlQueries) {
        super(sqlQueries, false);
    }


    public void insert(FileDistributionTask task) throws SqlQueriesException {
        N.r(() -> Lok.debug("INSERT COPY: " + SerializableEntitySerializer.serialize(task)));
        FileDistTaskWrapper wrapper = FileDistTaskWrapper.fromTask(task);
        Long id = sqlQueries.insert(wrapper);
        wrapper.getId().v(id);
        N.forEach(wrapper.getTargetWraps(), fileWrapper -> {
            sqlQueries.insert(fileWrapper);
        });
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

    public Map<Long, FileDistributionTask> loadChunk() throws SqlQueriesException {
        String where = dummy.getDone().k() + "=? limit 10";
        Map<Long, FileDistributionTask> tasks = new HashMap<>();
        List<FileDistTaskWrapper> taskWrappers = sqlQueries.load(dummy.getAllAttributes(), dummy, where, ISQLQueries.whereArgs(false));
        N.forEach(taskWrappers, taskWrapper -> {
            FileDistributionTask task = new FileDistributionTask();
            task.setSourceHash(taskWrapper.getSourceHash().v());
            task.setServiceUuid(taskWrapper.getServiceUuid().v());
            task.setDeleteSource(taskWrapper.getDeleteSource().v());
            task.setSourceFile(AFile.instance(taskWrapper.getSourcePath().v()));
            if (taskWrapper.getSize().notNull()) {
                FsBashDetails bashDetails = (FsBashDetails) SerializableEntityDeserializer.deserialize(taskWrapper.getSourceDetails().v());
                task.setOptionals(bashDetails, taskWrapper.getSize().v());
            }

            String whereToo = dummyFW.getTaskId().k() + "=?";
            List<FileDistTaskWrapper.FileWrapper> fileWrappers = sqlQueries.load(dummyFW.getAllAttributes(), dummyFW, whereToo, ISQLQueries.whereArgs(taskWrapper.getId().v()));
            N.forEach(fileWrappers, fileWrapper -> {
                AFile targetFile = AFile.instance(fileWrapper.getTargetPath().v());
                task.addTargetFile(targetFile, fileWrapper.getTargetFsId().v());
            });
            task.initFromPaths();
            tasks.put(taskWrapper.getId().v(), task);
        });
        return tasks;
    }

    public void deleteMarkedDone() throws SqlQueriesException {
        String statement = "delete from " + dummy.getTableName() + " where " + dummy.getDone().k() + "=?";
        sqlQueries.execute(statement, ISQLQueries.whereArgs(true));
    }

    public void deleteAll() throws SqlQueriesException {
        sqlQueries.execute("delete from " + dummy.getTableName(), null);
    }

    public int countAll() throws SqlQueriesException {
        return sqlQueries.queryValue("select count(1) from " + dummy.getTableName(), Integer.class);

    }

    public int countDone() throws SqlQueriesException {
        return sqlQueries.queryValue("select count(1) from " + dummy.getTableName() + " where " + dummy.getDone().k() + "=?", Integer.class, ISQLQueries.whereArgs(true));
    }
}
