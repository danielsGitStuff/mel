package de.mel;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.AbstractFileWriter;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.tools.F;
import de.mel.filesync.bash.AutoKlausIterator;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class BashToolsTest {

    static {
        File f = new File("la");
        Lok.debug("running in: " + f.getAbsolutePath());
        AbstractFile.configure(new DefaultFileConfiguration());
    }

    private final AbstractFile root = AbstractFile.instance("testdir.root");
    private final AbstractFile f1 = AbstractFile.instance(root, "f1.txt");
    private final AbstractFile f2 = AbstractFile.instance(root, "f2.txt");
    private final AbstractFile sub = AbstractFile.instance(root, "sub");
    private final AbstractFile f3 = AbstractFile.instance(sub, "f3.txt");
    private final AbstractFile subsub = AbstractFile.instance(sub, "subsub");
    private final AbstractFile f4 = AbstractFile.instance(subsub, "f4.txt");
    private final AbstractFile symFolder = AbstractFile.instance(root, "symfolder");
    private final AbstractFile symFolderTarget = subsub;
    private final AbstractFile symFile = AbstractFile.instance(root, "symfile.txt");
    private final AbstractFile symFileTarget = f3;

    @Before
    public void before() throws Exception {
        if (root.exists())
            F.rmRf(new File(root.getAbsolutePath()));
        subsub.mkdirs();
        write(f1, "f1");
        write(f2, "f2");
        write(f3, "f3");
        write(f4, "f4");
//        createSymlink(symlink, symTarget);
        BashTools.Companion.init();
    }

    private Path toPath(AbstractFile f) {
        return Paths.get(new File(f.getAbsolutePath()).toURI());
    }

    @After
    public void after() {
        if (root.exists())
            F.rmRf(new File(root.getAbsolutePath()));
    }

    private void write(AbstractFile f, String str) throws Exception {
        try (AbstractFileWriter writer = f.writer()) {
            writer.append(str.getBytes(), 0, str.length());
        }
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
        try (AutoKlausIterator<AbstractFile> found = BashTools.Companion.find(root, subsub)) {
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
