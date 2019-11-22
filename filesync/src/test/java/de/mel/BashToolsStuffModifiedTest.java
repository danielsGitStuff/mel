package de.mel;



import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.file.IFile;
import de.mel.filesync.bash.AutoKlausIterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.file.AbstractFile;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.serialization.TestDirCreator;

import static org.junit.Assert.*;

/**
 * Created by xor on 7/29/17.
 */

public class BashToolsStuffModifiedTest {
    static {
        AbstractFile.configure(new DefaultFileConfiguration());
    }
    IFile testDir = AbstractFile.instance("testdir1");
    Set<String> paths;

    @Before
    public void before() throws Exception {
        BashTools.Companion.init();
        AbstractFile.configure(new DefaultFileConfiguration());
        CertificateManager.deleteDirectory(testDir);
        paths = new HashSet<>();
        paths.addAll(TestDirCreator.createTestDir(testDir));
    }

    @Test
    public void bashFind() throws Exception {
        Iterator<IFile> iterator = BashTools.Companion.find(testDir, AbstractFile.instance("blaaaa"));
        while (iterator.hasNext())
            Lok.debug(iterator.next());
        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter1() throws Exception {
        //expect one result
        CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        AutoKlausIterator<IFile> iterator = BashTools.Companion.stuffModifiedAfter(testDir, AbstractFile.instance("blaa"), 0L);
        assertTrue(iterator.hasNext());
        iterator.next();
        assertFalse(iterator.hasNext());
        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter2() throws Exception {
        //expect no result
        CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        AutoKlausIterator<IFile> iterator = BashTools.Companion.stuffModifiedAfter(testDir, AbstractFile.instance("blaa"), Long.MAX_VALUE);
        assertFalse(iterator.hasNext());
        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter3() throws Exception {
        // expect whole testdir
        AutoKlausIterator<IFile> iterator = BashTools.Companion.stuffModifiedAfter(testDir, AbstractFile.instance("blaa"), 0L);
        while (iterator.hasNext()) {
            String path = iterator.next().getAbsolutePath();
            assertNotNull(paths.remove(path));
        }
        assertTrue(paths.isEmpty());
        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void timestamp1() throws InterruptedException {
        testDir.mkdirs();
        Long t1 = testDir.lastModified();
        Thread.sleep(100);
        File dir = new File(testDir.getAbsolutePath() + File.separator + "ttttttt");
        dir.mkdirs();
        Long t2 = testDir.lastModified();
        Lok.debug("before: " + t1);
        Lok.debug("after : " + t2);
        assertTrue(t1 < t2);
    }

    @Test
    public void timestamp2() {
        File dir = new File("ttttttt");
        dir.mkdirs();
        Long t1 = dir.lastModified();
        Long tt1 = testDir.lastModified();
        File source = new File(testDir.getAbsolutePath());
        File target = new File(source.getAbsolutePath(),"movedTest");
        source.renameTo(target);
        Long t2 = dir.lastModified();
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
    }
}
