package de.mein.drive.index;

import de.mein.auth.tools.lock.Transaction;
import de.mein.drive.sql.FsDirectory;

/**
 * Created by xor on 10.07.2016.
 */
public interface IndexListener {


    void foundDirectory(FsDirectory fsDirectory);

    void done(Long stageSetId, Transaction transaction);
}
