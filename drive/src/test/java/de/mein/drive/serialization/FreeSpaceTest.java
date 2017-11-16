package de.mein.drive.serialization;

import org.junit.Test;

import java.io.File;

import de.mein.FsDummyDao;
import de.mein.TransferManagerDummy;
import de.mein.WasteBinDummy;
import de.mein.drive.quota.QuotaManager;

/**
 * Created by xor on 10.11.2017.
 */

public class FreeSpaceTest {
    @Test
    public void run(){
        new File("keks").getFreeSpace();
        FsDummyDao fsDao = new FsDummyDao();
        TransferManagerDummy transferManager = new TransferManagerDummy();
        WasteBinDummy wasteBin = new WasteBinDummy();

        //fsDao.get
    }
}
