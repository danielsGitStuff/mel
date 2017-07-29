package de.mein.drive.serialization;

import de.mein.auth.data.access.CertificateManager;
import de.mein.drive.bash.BashTools;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Created by thefa on 7/29/2017.
 */
public class WindowsTest {
    File testDir = new File("testdir1");
    List<String> paths;

    @Before
    public void before() throws IOException {
        BashTools.init();
        CertificateManager.deleteDirectory(testDir);
        paths = TestDirCreator.createTestDir(testDir);
    }

    @Test
    public void bashFind() throws Exception {
        Iterator<String> iterator = BashTools.find(testDir, new File("blaaaa"));
        while (iterator.hasNext())
            System.out.println(iterator.next());
        System.out.println("WindowsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter1() throws Exception {
        //expect one result
        CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        Iterator<String> iterator = BashTools.stuffModifiedAfter(testDir, new File("blaa"), 0L);
        assertTrue(iterator.hasNext());
        iterator.next();
        assertFalse(iterator.hasNext());
        System.out.println("WindowsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter2() throws Exception {
        //expect no result
        CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        Iterator<String> iterator = BashTools.stuffModifiedAfter(testDir, new File("blaa"), Long.MAX_VALUE);
        assertFalse(iterator.hasNext());
        System.out.println("WindowsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter3() throws Exception {
        // expect whole testdir
        Iterator<String> iterator = BashTools.stuffModifiedAfter(testDir, new File("blaa"), 0L);
        Iterator<String> expectedIterator = paths.iterator();
        while (iterator.hasNext()) {
            String path = iterator.next();
            String expected = expectedIterator.next();
            System.out.println(path + " vs " + expected);
            assertEquals(expected, path);
        }
        assertFalse(expectedIterator.hasNext());
        System.out.println("WindowsTest.bashtest.end");
    }

    @After
    public void after() {
        CertificateManager.deleteDirectory(testDir);
    }
}