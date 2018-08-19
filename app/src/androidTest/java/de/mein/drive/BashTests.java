package de.mein.drive;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import de.mein.android.file.DFile;
import de.mein.auth.data.access.CertificateManager;
import de.mein.drive.bash.BashTools;
import de.mein.drive.serialization.TestDirCreator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by xor on 7/29/17.
 */

public class BashTests {
    DFile testDir = new DFile("testdir1");
    List<String> paths;

//    @Before
//    public void before() throws IOException {
//        BashTools.init();
//        CertificateManager.deleteDirectory(testDir);
//        paths = TestDirCreator.createTestDir(testDir);
//    }

    @Test
    public void bashFind() throws Exception {
        Iterator<String> iterator = BashTools.find(testDir, new DFile("blaaaa"));
        while (iterator.hasNext())
            System.out.println(iterator.next());
        System.out.println("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter1() throws Exception {
        //expect one result
        CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        Iterator<String> iterator = BashTools.stuffModifiedAfter(testDir, new DFile("blaa"), 0L);
        assertTrue(iterator.hasNext());
        iterator.next();
        assertFalse(iterator.hasNext());
        System.out.println("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter2() throws Exception {
        //expect no result
        CertificateManager.deleteDirectory(testDir);
        testDir.mkdirs();
        Iterator<String> iterator = BashTools.stuffModifiedAfter(testDir, new DFile("blaa"), Long.MAX_VALUE);
        assertFalse(iterator.hasNext());
        System.out.println("BashCommandsTest.bashtest.end");
    }

    @Test
    public void bashFindModifiedAfter3() throws Exception {
        // expect whole testdir
        Iterator<String> iterator = BashTools.stuffModifiedAfter(testDir, new DFile("blaa"), 0L);
        Iterator<String> expectedIterator = paths.iterator();
        while (iterator.hasNext()) {
            String path = iterator.next();
            String expected = expectedIterator.next();
            System.out.println(path + " vs " + expected);
            assertEquals(expected, path);
        }
        assertFalse(expectedIterator.hasNext());
        System.out.println("BashCommandsTest.bashtest.end");
    }

    @Test
    public void timestamp1() throws InterruptedException {
        testDir.mkdirs();
        Long t1 = testDir.lastModified();
        Thread.sleep(1);
        File dir = new File(testDir.getAbsolutePath() + File.separator + "ttttttt");
        dir.mkdirs();
        Long t2 = testDir.lastModified();
        System.out.println("before: " + t1);
        System.out.println("after : " + t2);
        assertTrue(t1 < t2);
    }

    @Test
    public void timestamp2() {
        File dir = new File("ttttttt");
        dir.mkdirs();
        Long t1 = dir.lastModified();
        Long tt1 = testDir.lastModified();
        testDir.renameTo(new DFile(dir.getAbsolutePath() + File.separator + "movedTest"));
        Long t2 = dir.lastModified();
        Long tt2 = testDir.lastModified();
        System.out.println("upper dir before: " + t1);
        System.out.println("upper dir after : " + t2);
        System.out.println("moved dir before: " + tt1);
        System.out.println("moved dir after : " + tt2);
        assertEquals(t1, t2);
        assertEquals(tt1, tt2);
    }

    @Test
    public void timestamp3() {
        BashTools.init();
        Context context = InstrumentationRegistry.getTargetContext();
        System.out.println("bla " + context.getPackageCodePath());
        System.out.println(context.getApplicationInfo().dataDir);
        final String rootPath = context.getApplicationInfo().dataDir;
        File dir = new File(rootPath + File.separator + "ttttttt");
        while (true){
            Long before = System.currentTimeMillis();
            dir.mkdirs();
            Long after = System.currentTimeMillis();
            Long modified = dir.lastModified();
            System.out.println("b "+before+" / m "+modified+" / a "+after);
            dir.delete();
        }
    }

//    @After
//    public void after() {
//        CertificateManager.deleteDirectory(testDir);
//    }
}
