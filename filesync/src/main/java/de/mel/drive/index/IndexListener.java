package de.mel.drive.index;

import de.mel.auth.tools.lock.Warden;

/**
 * Created by xor on 10.07.2016.
 */
public interface IndexListener {



    void done(Long stageSetId, Warden warden);
}
