package mel.de.meldrive;

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

import de.mel.Lok;
import de.mel.android.service.CopyService;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.filesync.bash.BashTools;
import de.mel.sql.Hash;

import static org.junit.Assert.*;

public class CopyServiceTest {
    private File testdir;
    private File srcFile;
    private File targetFile;
    private Path srcPath;
    private byte[] srcBytes;

    @Before
    public void before() throws IOException {
        BashTools.Companion.init();
        AbstractFile.configure(new DefaultFileConfiguration());
        testdir = new File("copy.test");
        Lok.debug("test dir = " + testdir.getAbsolutePath());
        testdir.mkdir();
        srcFile = new File(testdir, "src.txt");
        targetFile = new File(testdir, "target.txt");
        srcPath = Paths.get(srcFile.toURI());
        // test with a little bit more
        srcBytes = new byte[CopyService.BUFFER_SIZE + 13];
        new Random().nextBytes(srcBytes);
        Files.write(srcPath, srcBytes);
    }

    /**
     * copy random srcBytes
     */
    @Test
    public void copy() throws IOException {
        final String hash1 = Hash.md5(srcBytes);
        // the actual copying
        CopyService copyService = new CopyService();
        FileInputStream in = new FileInputStream(srcFile);
        FileOutputStream out = new FileOutputStream(targetFile);
        copyService.copyStream(in, out);
        String hash2 = Hash.md5(new FileInputStream(srcFile));
        String hash3 = Hash.md5(new FileInputStream(targetFile));
        assertEquals(hash1, hash2);
        assertEquals(hash2, hash3);
    }

    @After
    public void after() throws IOException {
//        BashTools.Companion.rmRf(AFile.instance(testdir));
    }
}
