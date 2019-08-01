package de.mein.drive;

import android.content.Context;
import androidx.test.InstrumentationRegistry;

import de.mein.auth.file.DefaultFileConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import de.mein.Lok;
import de.mein.android.file.JFile;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.file.AFile;
import de.mein.drive.bash.BashTools;
import de.mein.drive.serialization.TestDirCreator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by xor on 7/29/17.
 */

public class BashTests {
    static {
        AFile.configure(new DefaultFileConfiguration());
    }
    AFile testDir = AFile.instance("testdir1");
    List<String> paths;

    @Before
    public void before() throws IOException {
        BashTools.init();
        CertificateManager.deleteDirectory(testDir);
        paths = TestDirCreator.createTestDir(testDir);
    }

    @Test
    public void bashFind() throws Exception {
        Iterator<AFile<?>> iterator = BashTools.find(testDir, AFile.instance("blaaaa"));
        while (iterator.hasNext())
            Lok.debug(iterator.next());
        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter1() throws Exception {
        //expect one result
        CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        Iterator<AFile> iterator = BashTools.stuffModifiedAfter(testDir, AFile.instance("blaa"), 0L);
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
        Iterator<AFile> iterator = BashTools.stuffModifiedAfter(testDir, AFile.instance("blaa"), Long.MAX_VALUE);
        assertFalse(iterator.hasNext());
        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter3() throws Exception {
        // expect whole testdir
        Iterator<AFile> iterator = BashTools.stuffModifiedAfter(testDir, AFile.instance("blaa"), 0L);
        Iterator<String> expectedIterator = paths.iterator();
        while (iterator.hasNext()) {
            String path = iterator.next().getAbsolutePath();
            String expected = expectedIterator.next();
            Lok.debug(path + " vs " + expected);
            assertEquals(expected, path);
        }
        assertFalse(expectedIterator.hasNext());
        Lok.debug("BashCommandsTest.bashtest.end");
    }

    @Test
    public void timestamp1() throws InterruptedException {
        testDir.mkdirs();
        Long t1 = testDir.lastModified();
        Thread.sleep(1);
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

    @Test
    public void timestamp3() {
        BashTools.init();
        Context context = InstrumentationRegistry.getTargetContext();
        Lok.debug("bla " + context.getPackageCodePath());
        Lok.debug(context.getApplicationInfo().dataDir);
        final String rootPath = context.getApplicationInfo().dataDir;
        File dir = new File(rootPath + File.separator + "ttttttt");
        while (true){
            Long before = System.currentTimeMillis();
            dir.mkdirs();
            Long after = System.currentTimeMillis();
            Long modified = dir.lastModified();
            Lok.debug("b "+before+" / m "+modified+" / a "+after);
            dir.delete();
        }
    }

//    @After
//    public void after() {
//        CertificateManager.deleteDirectory(testDir);
//    }
}
