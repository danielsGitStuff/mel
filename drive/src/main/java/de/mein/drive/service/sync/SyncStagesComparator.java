package de.mein.drive.service.sync;

import de.mein.drive.sql.Stage;

/**
 * Created by xor on 5/6/17.
 */
public interface SyncStagesComparator {
    void stuffFound(Stage left, Stage right);
}
