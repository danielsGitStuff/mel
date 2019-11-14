package de.mel.filesync.serialization;

import de.mel.Lok;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.filesync.bash.AutoKlausIterator;
import de.mel.filesync.bash.BashTools;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by thefa on 7/29/2017.
 */
public class BashCommandsTest {
    AbstractFile testDir;
    List<String> paths;
    AbstractFile timeDir;

    @Before
    public void before() throws IOException {
        AbstractFile.configure(new DefaultFileConfiguration());
        testDir = AbstractFile.instance("testdir1");
        BashTools.Companion.init();
        CertificateManager.deleteDirectory(testDir);
        paths = TestDirCreator.createTestDir(testDir);
        timeDir = AbstractFile.instance(testDir.absolutePath + File.separator + "timetest");

    }

    @Test
    public void bashFind() throws Exception {
        try (AutoKlausIterator<AbstractFile<?>> iterator = BashTools.Companion.find(testDir, AbstractFile.instance("blaaaa"))) {
            while (iterator.hasNext())
                Lok.debug(iterator.next());
            Lok.debug("BashCommandsTest.bashtest.end");
        }
    }

    @Test
    public void bashFindModifiedAfter1() throws Exception {
        //todo disabled for now because platform specific -> merge BashTools.Companion.stuffModifiedAfter methods
        //expect one result
//        CertificateManager.deleteDirectory(testDir);
//        testDir.mkdirs();
//        Iterator<AFile> iterator = BashTools.Companion.stuffModifiedAfter(testDir, AFile.instance("blaa"), 0L);
//        assertTrue(iterator.hasNext());
//        iterator.next();
//        assertFalse(iterator.hasNext());
//        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter2() throws Exception {
        //todo disabled for now because platform specific
        //expect no result
//        CertificateManager.deleteDirectory(testDir);
//        testDir.mkdirs();
//        Iterator<AFile> iterator = BashTools.Companion.stuffModifiedAfter(testDir, AFile.instance("blaa"), Long.MAX_VALUE);
//        assertFalse(iterator.hasNext());
//        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter3() throws Exception {
        //todo disabled for now because platform specific
        // expect whole testdir
//        Iterator<AFile> iterator = BashTools.Companion.stuffModifiedAfter( testDir, new FFile("blaa"), 0L);
//        Iterator<String> expectedIterator = paths.iterator();
//        while (iterator.hasNext()) {
//            String path = iterator.next().getAbsolutePath();
//            String expected = expectedIterator.next();
//            Lok.debug(path + " vs " + expected);
//            assertEquals(expected, path);
//        }
//        assertFalse(expectedIterator.hasNext());
//        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void timestamp1() throws InterruptedException {
        Long t1 = testDir.lastModified();
        Thread.sleep(1000);
        timeDir.mkdirs();
        Long t2 = testDir.lastModified();
        Lok.debug("before: " + t1);
        Lok.debug("after : " + t2);
        assertTrue(t1 < t2);
    }

    @Test
    public void timestamp2() {
        timeDir.mkdirs();
        Long t1 = timeDir.lastModified();
        Long tt1 = testDir.lastModified();

        File source = new File(testDir.absolutePath);
        File target = new File(source.getAbsolutePath(), "movedTest");
        source.renameTo(target);

        Long t2 = timeDir.lastModified();
        Long tt2 = testDir.lastModified();
        Lok.debug("upper dir before: " + t1);
        Lok.debug("upper dir after : " + t2);
        Lok.debug("moved dir before: " + tt1);
        Lok.debug("moved dir after : " + tt2);
        assertEquals(t1, t2);
        assertEquals(tt1, tt2);
    }

    @After
    public void after() {
        CertificateManager.deleteDirectory(testDir);
        CertificateManager.deleteDirectory(timeDir);
    }
}
