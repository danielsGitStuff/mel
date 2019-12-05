package de.mel.filesync.data.conflict

import de.mel.auth.file.AbstractFile
import de.mel.auth.file.IFile
import de.mel.filesync.sql.Stage
import de.mel.sql.SqlQueriesException

/**
 * Created by xor on 5/6/17.
 */
abstract class SyncStageMerger(protected val lStageSetId: Long, protected val rStageSetId: Long) {
    /**
     * is called with two Stages which reference the same logical File or Directory
     *
     * @param left
     * @param right
     * @throws SqlQueriesException
     */
    @Throws(SqlQueriesException::class)
    abstract fun stuffFound(left: Stage?, right: Stage?, lFile: IFile?, rFile: IFile?)

}