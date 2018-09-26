package de.mein.drive.index;

import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;

/**
 * Created by xor on 10.07.2016.
 */
public interface IndexListener {


    void foundDirectory(FsDirectory fsDirectory);

    void done(Long stageSetId);
}
