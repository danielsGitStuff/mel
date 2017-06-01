package de.mein.drive.service.sync;

import de.mein.drive.sql.Stage;
import de.mein.sql.SqlQueriesException;

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
    public abstract void stuffFound(Stage left, Stage right) throws SqlQueriesException;

    public SyncStageMerger(Long lStageSetId, Long rStageSetId) {
        this.lStageSetId = lStageSetId;
        this.rStageSetId = rStageSetId;
    }
}
