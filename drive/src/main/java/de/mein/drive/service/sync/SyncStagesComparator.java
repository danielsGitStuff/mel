package de.mein.drive.service.sync;

import de.mein.drive.sql.Stage;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 5/6/17.
 */
public abstract class SyncStagesComparator {
    protected final Long lStageSetId;
    protected final Long rStageSetId;

    public abstract void stuffFound(Stage left, Stage right) throws SqlQueriesException;
    public SyncStagesComparator(Long lStageSetId, Long rStageSetId){
        this.lStageSetId=lStageSetId;
        this.rStageSetId=rStageSetId;
    }
}
