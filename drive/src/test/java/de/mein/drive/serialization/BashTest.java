package de.mein.drive.serialization;

import de.mein.drive.index.BashTools;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Created by xor on 10/28/16.
 */
public class BashTest {
    @Test
    public void bash1() throws IOException {
        File file = new File(System.getProperty("user.dir"));
        BashTools.getINodesOfDirectory(file);
    }
}
