package de.mein.drive.serialization;

import de.mein.WastebinDummy;
import org.junit.Test;

import java.io.File;

import de.mein.FsDummyDao;
import de.mein.TransferManagerDummy;

/**
 * Created by xor on 10.11.2017.
 */

public class FreeSpaceTest {
    @Test
    public void run(){
        new File("keks").getFreeSpace();
        FsDummyDao fsDao = new FsDummyDao();
        TransferManagerDummy transferManager = new TransferManagerDummy();
        WastebinDummy wasteBin = new WastebinDummy();

        //fsDao.get
    }
}
