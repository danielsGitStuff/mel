package de.mein;

import de.mein.auth.file.AFile;
import de.mein.auth.file.DefaultFileConfiguration;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsEntry;
import de.mein.drive.sql.FsFile;
import de.mein.drive.sql.Stage;
import de.mein.drive.transfer.TransferManager;
import org.junit.Test;

/**
 * Created by xor on 10.11.2017.
 */

public class TransferManagerDummy   {

    @Test
    public void debugTest() throws Exception {
        FsDirectory parent1 = new FsDirectory();
        parent1.addDummyFsFile("same1.txt").addDummyFsFile("same2.txt");
        FsDirectory parent2 = new FsDirectory();
        parent2.addDummyFsFile("same1.txt").addDummyFsFile("same2.txt").addDummySubFsDirectory("samesub");
        parent1.calcContentHash();
        parent2.calcContentHash();

        System.out.println(parent1.getContentHash());
        System.out.println(parent2.getContentHash());

    }

}
