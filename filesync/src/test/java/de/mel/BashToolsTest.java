package de.mel;

import de.mel.auth.tools.F;
import de.mel.filesync.bash.AutoKlausIterator;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BashToolsTest {

    static {
        File f = new File("la");
        Lok.debug("running in: " + f.getAbsolutePath());
    }

    private final File root = new File("testdir.root");
    private final File f1 = new File(root, "f1.txt");
    private final File f2 = new File(root, "f2.txt");
    private final File sub = new File(root, "sub");
    private final File f3 = new File(sub, "f3.txt");
    private final File subsub = new File(sub, "subsub");
    private final File f4 = new File(subsub, "f4.txt");
    private final File symFolder = new File(root, "symfolder");
    private final File symFolderTarget = subsub;
    private final File symFile = new File(root, "symfile.txt");
    private final File symFileTarget = f3;

    @BeforeEach
    public void before() throws Exception {
        Lok.debug("running in " + System.getProperties().get("user.dir"));
        if (root.exists())
            F.rmRf(root);
        subsub.mkdirs();
        write(f1, "f1");
        write(f2, "f2");
        write(f3, "f3");
        write(f4, "f4");
//        createSymlink(symlink, symTarget);
        BashTools.Companion.init();
    }

    private Path toPath(File f) {
        return f.toPath();
    }

    @AfterEach
    public void after() {
        if (root.exists())
            F.rmRf(root);
    }

    private void write(File f, String str) throws Exception {
        Files.write(f.toPath(), str.getBytes());
    }

    @Test
    public void getFsBashDetails() throws IOException, InterruptedException {
        FsBashDetails fsBashDetails = BashTools.Companion.getFsBashDetails(f2);
        assertNotNull(fsBashDetails);
        assertNotNull(fsBashDetails.getiNode());
        assertNotNull(fsBashDetails.getModified());
        assertNull(fsBashDetails.getSymLinkTarget());
        assertNotNull(fsBashDetails.getName());
        assertFalse(fsBashDetails.isSymLink());
    }

    @Test
    public void getFsBashDetailsModified() throws Exception {
        Thread.sleep(1001);
        write(f2, "NEIN!");
        FsBashDetails fsBashDetails = BashTools.Companion.getFsBashDetails(f2);

        assertNotEquals(f2.lastModified(), fsBashDetails.getCreated());
        Long diff = f2.lastModified() - fsBashDetails.getCreated();
        assertTrue(diff >= 0);
        assertTrue(diff < 2000);

    }

    @Test
    public void rmRf() throws IOException {
        BashTools.Companion.rmRf(subsub);
        assertFalse(subsub.exists());
    }

    @Test
    public void find() throws Exception {
        Set<String> expectedPaths = new HashSet<>();
        expectedPaths.add(root.getAbsolutePath());
        expectedPaths.add(sub.getAbsolutePath());
        expectedPaths.add(f1.getAbsolutePath());
        expectedPaths.add(f2.getAbsolutePath());
        expectedPaths.add(f3.getAbsolutePath());
        try (AutoKlausIterator<File> found = BashTools.Companion.find(root, subsub)) {
            while (found.hasNext()) {
                expectedPaths.remove(found.next().getAbsolutePath());
            }
        }
        assertTrue(expectedPaths.isEmpty());
    }


    @Test
    public void isSymLink() {
        lnS();
        assertTrue(BashTools.Companion.isSymLink(symFolder));
        assertTrue(BashTools.Companion.isSymLink(symFile));
        assertFalse(BashTools.Companion.isSymLink(f1));
        assertFalse(BashTools.Companion.isSymLink(sub));
    }

    @Test
    public void getContentFsBashDetails() throws IOException, InterruptedException {
        // create symlinks first
        lnS();
        Map<String, FsBashDetails> details = BashTools.Companion.getContentFsBashDetails(root);
        Set<String> expectedNames = new HashSet<>();
        expectedNames.add(f1.getName());
        expectedNames.add(f2.getName());
        expectedNames.add(sub.getName());
        expectedNames.add(symFile.getName());
        expectedNames.add(symFolder.getName());

        // we might get a few entries more than required. ("." and ".." may be in the result set)
        details.forEach((name, fsBashDetails) -> {
            expectedNames.remove(name);
            assertNotNull(fsBashDetails.getModified());
            assertNotNull(fsBashDetails.getiNode());
            if (name.equals(symFile.getName())) {
                assertTrue(fsBashDetails.isSymLink());
                assertEquals(symFileTarget.getAbsolutePath(), fsBashDetails.getSymLinkTarget());
            } else if (name.equals(symFolder.getName())) {
                assertTrue(fsBashDetails.isSymLink());
                assertEquals(symFolderTarget.getAbsolutePath(), fsBashDetails.getSymLinkTarget());
            } else {
                assertNull(fsBashDetails.getSymLinkTarget());
                assertFalse(fsBashDetails.isSymLink());
            }
        });
        assertTrue(expectedNames.isEmpty());
    }

    @Test
    public void lnS() {
        BashTools.Companion.lnS(symFolder, symFolderTarget.getAbsolutePath());
        BashTools.Companion.lnS(symFile, symFileTarget.getAbsolutePath());
        if (BashTools.Companion.isWindows()) {
            assertTrue(symFolder.exists());
            assertTrue(symFile.exists());
        } else {
            assertTrue(Files.isSymbolicLink(toPath(symFile)));
            assertTrue(Files.isSymbolicLink(toPath(symFolder)));
        }
    }
}
