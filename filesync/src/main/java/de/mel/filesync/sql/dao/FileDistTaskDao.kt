package de.mel.filesync.sql.dao

import java.util.HashMap

import de.mel.auth.file.AFile
import de.mel.auth.tools.N
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mel.filesync.bash.FsBashDetails
import de.mel.filesync.data.FileDistTaskWrapper
import de.mel.filesync.nio.FileDistributionTask
import de.mel.sql.Dao
import de.mel.sql.ISQLQueries
import de.mel.sql.SqlQueriesException

/**
 * You cannot put a JSON into a database on Android
 */
class FileDistTaskDao(sqlQueries: ISQLQueries) : Dao(sqlQueries, false) {


    private val dummy = FileDistTaskWrapper()
    private val dummyFW = FileDistTaskWrapper.FileWrapper()

    fun isComplete(): Boolean {
        val queryDifference = "select ((select count(1) from ${dummy.tableName})-(select count(1) from ${dummy.tableName} where ${dummy.state.k()}=?))"
        val count: Long = sqlQueries.queryValue(queryDifference, Long::class.java, ISQLQueries.args(FileDistributionTask.FileDistributionState.DONE))
        return count == 0L
    }


    @Throws(SqlQueriesException::class)
    fun insert(task: FileDistributionTask) {
        val wrapper = FileDistTaskWrapper.fromTask(task)
        val id = sqlQueries.insert(wrapper)
        wrapper.id.v(id)
        task.id = id
        N.forEach(wrapper.targetWraps) { fileWrapper -> sqlQueries.insert(fileWrapper) }
    }

    fun getNotReadyYetByHash(hash: String): FileDistributionTask? {
        val where = "${dummy.sourceHash.k()}=? and ${dummy.state.k()}=?"
        val wrapper = sqlQueries.loadFirstRow(dummy.allAttributes, dummy, where, ISQLQueries.args(hash, FileDistributionTask.FileDistributionState.NRY), FileDistTaskWrapper::class.java)

        return wrapperToTask(wrapper)
    }

    private fun wrapperToTask(taskWrapper: FileDistTaskWrapper?): FileDistributionTask? {
        if (taskWrapper == null)
            return null
        val task = FileDistributionTask()
        task.id = taskWrapper.id.v()
        task.sourceHash = taskWrapper.sourceHash.v()
        task.deleteSource = taskWrapper.deleteSource.v()
        if (taskWrapper.sourcePath.notNull())
            task.sourceFile = AFile.instance(taskWrapper.sourcePath.v())
        if (taskWrapper.size.notNull()) {
            val bashDetails = SerializableEntityDeserializer.deserialize(taskWrapper.sourceDetails.v()) as FsBashDetails
            task.setOptionals(bashDetails, taskWrapper.size.v())
        }

        val whereToo = dummyFW.taskId.k() + "=?"
        val fileWrappers = sqlQueries.load(dummyFW.allAttributes, dummyFW, whereToo, ISQLQueries.args(taskWrapper.id.v()))
        N.forEach(fileWrappers) { fileWrapper ->
            val targetFile = AFile.instance(fileWrapper.getTargetPath().v())
            task.addTargetFile(targetFile, fileWrapper.getTargetFsId().v())
        }
        task.initFromPaths()
        return task
    }

    fun completeJob(task: FileDistributionTask) {
        if (task.id == null) {
            insert(task)
            return
        }
        val wrapper = FileDistTaskWrapper.fromTask(task)
        wrapper.id.v(task.id)
        val where = "${dummy.id.k()}=?"
        N.forEach(wrapper.targetWraps) { fileWrapper ->
            fileWrapper.taskId.v(wrapper.id)
            sqlQueries.insert(fileWrapper)
        }
        sqlQueries.update(wrapper, where, ISQLQueries.args(task.id))
    }


    @Throws(SqlQueriesException::class)
    fun markDone(id: Long) {
        val statement = "update " + dummy.tableName + " set " + dummy.state.k() + "=? where " + dummy.id.k() + "=?"
        sqlQueries.execute(statement, ISQLQueries.args(FileDistributionTask.FileDistributionState.DONE, id))
    }

    @Throws(SqlQueriesException::class)
    fun loadChunk(): Map<Long, FileDistributionTask> {
        val where = dummy.state.k() + "=? limit 10"
        val tasks = HashMap<Long, FileDistributionTask>()
        val taskWrappers = sqlQueries.load(dummy.allAttributes, dummy, where, ISQLQueries.args(FileDistributionTask.FileDistributionState.READY))!!
        N.forEach(taskWrappers) { taskWrapper ->
            val task = wrapperToTask(taskWrapper)
            tasks[taskWrapper.id.v()] = task!!
        }
        return tasks
    }

    @Throws(SqlQueriesException::class)
    fun deleteMarkedDone() {
        val statement = "delete from " + dummy.tableName + " where " + dummy.state.k() + "=?"
        sqlQueries.execute(statement, ISQLQueries.args(FileDistributionTask.FileDistributionState.DONE))
    }

    @Throws(SqlQueriesException::class)
    fun deleteAll() {
        sqlQueries.execute("delete from " + dummy.tableName, null)
    }

    @Throws(SqlQueriesException::class)
    fun countAll(): Int {
        return sqlQueries.queryValue("select count(1) from " + dummy.tableName, Int::class.java)

    }

    @Throws(SqlQueriesException::class)
    fun countDone(): Int {
        return sqlQueries.queryValue("select count(1) from " + dummy.tableName + " where " + dummy.state.k() + "=?", Int::class.java, ISQLQueries.args(FileDistributionTask.FileDistributionState.DONE))
    }


    @Throws(SqlQueriesException::class)
    fun hasWork(): Boolean {
        val query = "select count(1) from " + dummy.tableName + " where " + dummy.state.k() + "=?"
        val count = sqlQueries.queryValue(query, Long::class.java, ISQLQueries.args(FileDistributionTask.FileDistributionState.READY))
        return count > 0
    }
}
