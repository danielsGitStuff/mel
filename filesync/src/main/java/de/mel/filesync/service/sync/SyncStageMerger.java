package de.mel.filesync.service.sync;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.filesync.sql.Stage;
import de.mel.sql.SqlQueriesException;

/**
 * Created by xor on 5/6/17.
 */
public abstract class SyncStageMerger {
    protected final Long lStageSetId;
    protected final Long rStageSetId;


    /**
     * is called with two Stages which reference the same logical File or Directory
     *
     * @param left
     * @param right
     * @throws SqlQueriesException
     */
    public abstract void stuffFound(Stage left, Stage right, IFile lFile, IFile rFile) throws SqlQueriesException;

    public SyncStageMerger(Long lStageSetId, Long rStageSetId) {
        this.lStageSetId = lStageSetId;
        this.rStageSetId = rStageSetId;
    }
}
