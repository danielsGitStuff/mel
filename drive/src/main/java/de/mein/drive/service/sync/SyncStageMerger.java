package de.mein.drive.service.sync;

import de.mein.drive.sql.Stage;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 5/6/17.
 */
public abstract class SyncStageMerger {
    protected final Long lStageSetId;
    protected final Long rStageSetId;
    protected Map<String, Conflict> deletedParentLeft = new HashMap<>();
    protected Map<String, Conflict> deletedParentRight = new HashMap<>();

    /**
     * is called with two Stages which reference the same logical File or Directory
     *
     * @param left
     * @param right
     * @throws SqlQueriesException
     */
    public abstract void stuffFound(Stage left, Stage right, File lFile, File rFile) throws SqlQueriesException;

    public SyncStageMerger(Long lStageSetId, Long rStageSetId) {
        this.lStageSetId = lStageSetId;
        this.rStageSetId = rStageSetId;
    }
}
