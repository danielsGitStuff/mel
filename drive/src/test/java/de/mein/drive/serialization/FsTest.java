package de.mein.drive.serialization;

import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Created by xor on 12/6/16.
 */
public class FsTest {
    @Test
    public void hash(){
        FsFile f = new FsFile();
        f.getName().v("name");
        FsDirectory subDir = new FsDirectory();
        subDir.getName().v("name");
        FsDirectory d1 = new FsDirectory().addFile(f);
        d1.calcContentHash();
        String h1 = d1.getContentHash().v();
        FsDirectory d2 = new FsDirectory().addSubDirectory(subDir);
        d2.calcContentHash();
        String h2 = d2.getContentHash().v();
        assertNotEquals(h1,h2);
    }
}
