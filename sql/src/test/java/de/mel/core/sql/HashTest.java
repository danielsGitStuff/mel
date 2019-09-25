package de.mel.core.sql;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import de.mel.sql.Hash;

import static org.junit.Assert.assertEquals;

public class HashTest {
    private static final int BUFFER_SIZE = 1024;
    private File testdir;
    private File srcFile;
    private byte[] srcBytes;
    private Path srcPath;

    @Before
    public void before() throws IOException {
        testdir = new File("copy.test");
        System.out.println("test dir = " + testdir.getAbsolutePath());
        testdir.mkdir();
        srcFile = new File(testdir, "src.txt");
        srcPath = Paths.get(srcFile.toURI());
        // test with a little bit more
        srcBytes = new byte[BUFFER_SIZE];
        new Random().nextBytes(srcBytes);
        Files.write(srcPath, srcBytes);
    }

    @Test
    public void md5() throws IOException {
        final String hashBytes = Hash.md5(srcBytes);
        final String hashFile = Hash.md5(new FileInputStream(srcFile));
        assertEquals(hashBytes,hashFile);
    }

    @Test
    public void sha256() throws IOException {
        final String hashBytes = Hash.sha256(srcBytes);
        final String hashFile = Hash.sha256(new FileInputStream(srcFile));
        assertEquals(hashBytes,hashFile);
    }

    @After
    public void after() throws IOException {
      srcFile.delete();
      testdir.delete();
    }


}
