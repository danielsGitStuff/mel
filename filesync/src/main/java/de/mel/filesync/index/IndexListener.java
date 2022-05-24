package de.mel.filesync.index;

import de.mel.auth.tools.lock2.BunchOfLocks;

/**
 * Created by xor on 10.07.2016.
 */
public interface IndexListener {



    void done(Long stageSetId, BunchOfLocks bunchOfLocks);
}
