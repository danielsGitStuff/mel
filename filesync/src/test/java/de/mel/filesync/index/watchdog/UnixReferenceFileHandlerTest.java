package de.mel.filesync.index.watchdog;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.file.IFile;
import de.mel.filesync.bash.AutoKlausIterator;
import de.mel.filesync.bash.BashTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the watcher asks `find` for when it does not trust its events: everything that changed since the last time.
 */
public class UnixReferenceFileHandlerTest {
    @TempDir
    File dir;
    File root;
    File work;
    File transfer;

    @BeforeEach
    public void before() throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        BashTools.Companion.init();
        Assumptions.assumeTrue(BashTools.Companion.canFindByTime(), "this find cannot take a time");
        root = new File(dir, "root");
        work = new File(dir, "work");
        transfer = new File(root, ".transfer");
        assertTrue(root.mkdirs() && work.mkdirs() && transfer.mkdirs());
    }

    private UnixReferenceFileHandler handler() {
        UnixReferenceFileHandler handler = new UnixReferenceFileHandler(work, AbstractFile.instance(root), AbstractFile.instance(transfer));
        handler.onStart();
        return handler;
    }

    private static List<String> names(List<IFile> found) {
        List<String> names = new ArrayList<>();
        for (IFile file : found)
            names.add(file.getName());
        return names;
    }

    private File write(String name) throws Exception {
        File file = new File(root, name);
        Files.write(file.toPath(), name.getBytes());
        return file;
    }

    @Test
    public void aNewFileIsFound() throws Exception {
        UnixReferenceFileHandler handler = handler();
        write("new.bin");
        assertTrue(names(handler.stuffModifiedAfter()).contains("new.bin"));
    }

    @Test
    public void whatIsInTheTransferDirectoryIsNotFound() throws Exception {
        UnixReferenceFileHandler handler = handler();
        Files.write(new File(transfer, "download").toPath(), new byte[]{1});
        assertFalse(names(handler.stuffModifiedAfter()).contains("download"));
    }

    /**
     * the modified time of a file that is copied with its time (cp -p, rsync -t, our own received files) is old, it changed only in ctime
     */
    @Test
    public void aFileThatCameWithAnOldModifiedTimeIsFound() throws Exception {
        UnixReferenceFileHandler handler = handler();
        File file = write("copied.bin");
        assertTrue(file.setLastModified(1_577_869_200_000L));
        assertTrue(names(handler.stuffModifiedAfter()).contains("copied.bin"));
    }

    /**
     * the scan that follows a scan must not miss what happened in the moment of the first: the times of both can be the same
     */
    @Test
    public void whatHappensRightAfterAScanIsFoundByTheNext() throws Exception {
        UnixReferenceFileHandler handler = handler();
        handler.stuffModifiedAfter();
        write("right after.bin");
        assertTrue(names(handler.stuffModifiedAfter()).contains("right after.bin"));
    }

    @Test
    public void whatDidNotChangeForALongerTimeThanTheMarginIsNotFoundAgain() throws Exception {
        UnixReferenceFileHandler handler = handler();
        write("old news.bin");
        assertTrue(names(handler.stuffModifiedAfter()).contains("old news.bin"));
        Thread.sleep(UnixReferenceFileHandler.MARGIN_MS + 1_500);
        handler.stuffModifiedAfter();
        assertFalse(names(handler.stuffModifiedAfter()).contains("old news.bin"));
    }

    @Test
    public void aDirectoryThatIsNotThereFindsNothing() throws Exception {
        UnixReferenceFileHandler handler = new UnixReferenceFileHandler(work, AbstractFile.instance(new File(dir, "does not exist")), AbstractFile.instance(transfer));
        handler.onStart();
        assertEquals(0, handler.stuffModifiedAfter().size());
    }
}
