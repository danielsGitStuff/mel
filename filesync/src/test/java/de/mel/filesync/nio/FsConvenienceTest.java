package de.mel.filesync.nio;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import de.mel.filesync.sql.FsFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FsConvenienceTest {
    private static final long OLD = 1_577_869_200_000L; // 2020-01-01
    @TempDir
    File dir;

    private File content(String name, int size) throws Exception {
        File file = new File(dir, name);
        byte[] bytes = new byte[size];
        new java.util.Random(size).nextBytes(bytes);
        Files.write(file.toPath(), bytes);
        return file;
    }

    private static FsFile fsFileWith(Long modified) {
        FsFile fsFile = new FsFile();
        fsFile.getModified().v(modified);
        return fsFile;
    }

    @Test
    public void theModifiedTimeOfTheFsEntryIsAppliedToTheFile() throws Exception {
        File file = content("a.bin", 100);
        assertTrue(new DefaultFsConvenience().applyModified(file, fsFileWith(OLD)));
        assertEquals(OLD, file.lastModified());
    }

    @Test
    public void withoutATimeNothingIsApplied() throws Exception {
        File file = content("a.bin", 100);
        long before = file.lastModified();
        assertFalse(new DefaultFsConvenience().applyModified(file, fsFileWith(null)));
        assertFalse(new DefaultFsConvenience().applyModified(file, null));
        assertEquals(before, file.lastModified());
    }

    @Test
    public void aFileThatCannotBeChangedSaysSo() {
        assertFalse(new DefaultFsConvenience().applyModified(new File(dir, "not there"), fsFileWith(OLD)));
    }

    @Test
    public void aPhysicalCopyHasTheContentAndTheTimeOfTheSource() throws Exception {
        File source = content("source.bin", 300_000);
        assertTrue(source.setLastModified(OLD));
        File target = new File(dir, "target.bin");
        assertEquals(CopyResult.PHYSICAL, new DefaultFsConvenience().copy(source, target, true));
        assertArrayEquals(Files.readAllBytes(source.toPath()), Files.readAllBytes(target.toPath()));
        assertEquals(OLD, target.lastModified());
    }

    @Test
    public void whenPhysicalIsNotAllowedAndThereIsNoReferenceNothingIsMade() throws Exception {
        File source = content("source.bin", 1_000);
        File target = new File(dir, "target.bin");
        assertEquals(CopyResult.NOT_DONE, new DefaultFsConvenience().copy(source, target, false));
        assertFalse(target.exists());
    }

    @Test
    public void aCopyOfAFileThatIsNotThereFailsAndLeavesNothing() {
        File target = new File(dir, "target.bin");
        assertThrows(IOException.class, () -> new DefaultFsConvenience().copy(new File(dir, "not there"), target, true));
        assertFalse(target.exists());
    }

    /**
     * Whatever the file system under the temp directory can do: a copy is made, with the content and the time of the source, and one that must not be physical is
     * either a reference or not made. It is never a physical copy by the back door.
     */
    @Test
    public void whateverTheFileSystemDoesTheCopyIsRight() throws Exception {
        UnixFsConvenience convenience = new UnixFsConvenience();
        File source = content("source.bin", 500_000);
        assertTrue(source.setLastModified(OLD));
        File physicalOk = new File(dir, "physical ok.bin");
        CopyResult result = convenience.copy(source, physicalOk, true);
        assertNotEquals(CopyResult.NOT_DONE, result);
        assertArrayEquals(Files.readAllBytes(source.toPath()), Files.readAllBytes(physicalOk.toPath()));
        assertEquals(OLD, physicalOk.lastModified());

        File referenceOnly = new File(dir, "reference only.bin");
        CopyResult strict = convenience.copy(source, referenceOnly, false);
        if (strict == CopyResult.NOT_DONE) {
            assertFalse(referenceOnly.exists(), "nothing may stay of a copy that was not made");
        } else {
            assertEquals(CopyResult.REFERENCE, strict);
            assertArrayEquals(Files.readAllBytes(source.toPath()), Files.readAllBytes(referenceOnly.toPath()));
        }
    }

    /**
     * tmpfs has no references: a strict copy is not made, a normal one is a physical copy, and the failed attempt left nothing
     */
    @Test
    public void whereThereAreNoReferencesStrictCopiesAreNotMade() throws Exception {
        File shm = new File("/dev/shm");
        Assumptions.assumeTrue(shm.isDirectory() && shm.canWrite(), "no /dev/shm");
        File work = Files.createTempDirectory(shm.toPath(), "mel.fsconvenience").toFile();
        try {
            File source = new File(work, "source.bin");
            Files.write(source.toPath(), new byte[50_000]);
            UnixFsConvenience convenience = new UnixFsConvenience();
            File strict = new File(work, "strict.bin");
            assertEquals(CopyResult.NOT_DONE, convenience.copy(source, strict, false));
            assertFalse(strict.exists());
            File normal = new File(work, "normal.bin");
            assertEquals(CopyResult.PHYSICAL, convenience.copy(source, normal, true));
            assertTrue(Arrays.equals(Files.readAllBytes(source.toPath()), Files.readAllBytes(normal.toPath())));
        } finally {
            for (File f : work.listFiles())
                f.delete();
            work.delete();
        }
    }

    /**
     * the project's own file system: if it makes references, the copy is one
     */
    @Test
    public void whereTheFileSystemMakesReferencesTheCopyIsOne() throws Exception {
        File base = new File("build");
        Assumptions.assumeTrue(base.isDirectory(), "no build directory");
        File work = Files.createTempDirectory(base.toPath(), "fsconvenience").toFile();
        try {
            File source = new File(work, "source.bin");
            Files.write(source.toPath(), new byte[200_000]);
            File target = new File(work, "target.bin");
            CopyResult result = new UnixFsConvenience().copy(source, target, false);
            Assumptions.assumeTrue(result == CopyResult.REFERENCE, "this file system makes no references");
            assertEquals(200_000, target.length());
        } finally {
            for (File f : work.listFiles())
                f.delete();
            work.delete();
        }
    }
}
