package de.mel.drive.index;

import de.mel.auth.tools.lock.Transaction;
import de.mel.drive.sql.FsDirectory;

/**
 * Created by xor on 10.07.2016.
 */
public interface IndexListener {



    void done(Long stageSetId, Transaction transaction);
}
