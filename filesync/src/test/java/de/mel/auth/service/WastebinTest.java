package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.mel.auth.file.IFile;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.bash.FsBashDetails;
import de.mel.filesync.service.Wastebin;
import de.mel.filesync.sql.DbTransferDetails;
import de.mel.filesync.sql.FsFile;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.TransferState;
import de.mel.filesync.sql.dao.FsDao;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A file the sync wants gone goes to the bin; when it is wanted again it comes back out ({@link Wastebin#restoreFsFiles()}, {@link Wastebin#getByHash(String)}).
 * Whatever happens in between, the file is either in its place or in the bin where the program finds it: never lost to the program.
 * <p>
 * The tests work on the bin of a synced client that is stopped then, so its own sync does not get in the way.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class WastebinTest {
    @TempDir
    File dir;

    private FileSyncInstances instances;
    private File file;
    private String md5;

    private FileSyncInstances synced(int port) throws Exception {
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "same.bin", 20_000, 1);
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "other.bin", 30_000, 2);
        instances = new FileSyncInstances(dir, port);
        try {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> ConflictSyncTest.equal(instances) && synced("same.bin") && synced("other.bin"), TwoInstances.TIMEOUT_SECONDS), "the first sync did not finish");
            instances.clientService.stop();
            file = new File(instances.clientRoot, "same.bin");
            md5 = FileSyncInstances.md5Of(file);
            return instances;
        } catch (Throwable t) {
            instances.close();
            throw t;
        }
    }

    private boolean synced(String name) {
        try {
            GenericFSEntry entry = fsDao().getGenericByPathAndName("/", name);
            return entry != null && Boolean.TRUE.equals(entry.getSynced().v());
        } catch (Exception e) {
            return false;
        }
    }

    private FsDao fsDao() {
        return instances.clientService.getFileSyncDatabaseManager().getFsDao();
    }

    private Wastebin bin() {
        return instances.clientService.getWastebin();
    }

    private FsFile entry(String name) throws Exception {
        GenericFSEntry entry = fsDao().getGenericByPathAndName("/", name);
        return entry == null ? null : fsDao().getFile(entry.getId().v());
    }

    /**
     * The fs entry of a file that came from the server: what the server knows, not what this disk says (no inode, no time), and not here yet.
     * Like committing a stage set of the server does.
     */
    private FsFile entryFromServer(FsFile like) throws Exception {
        FsFile fsFile = new FsFile();
        fsFile.getName().v(like.getName().v());
        fsFile.getContentHash().v(like.getContentHash().v());
        fsFile.getVersion().v(like.getVersion().v());
        fsFile.getSize().v(like.getSize().v());
        fsFile.getDepth().v(like.getDepth().v());
        fsFile.getPath().v(like.getPath().v());
        fsFile.getParentId().v(like.getParentId().v());
        fsFile.getSynced().v(false);
        fsDao().insert(fsFile);
        return fsFile;
    }

    /**
     * the file is wanted from the server: that is what makes the bin give it back
     */
    private void wantedFromServer(String hash, long size) throws Exception {
        DbTransferDetails details = new DbTransferDetails();
        details.getHash().v(hash);
        details.getCertId().v(instances.serverIdAtClient());
        details.getServiceUuid().v(instances.serverService.getUuid());
        details.getSize().v(size);
        details.getState().v(TransferState.NOT_STARTED);
        details.getAvailable().v(false);
        details.getDeleted().v(false);
        instances.clientService.getFileSyncDatabaseManager().getTransferDao().insert(details);
    }

    private void assertFoundInBin(String what) throws Exception {
        IFile inBin = bin().getByHash(md5);
        assertNotNull(inBin, what + ": the bin does not know the file. the bin holds " + binListing());
        assertTrue(inBin.exists(), what + ": the bin knows the file, but it is not there. the bin holds " + binListing());
        assertEquals(md5, FileSyncInstances.md5Of(new File(inBin.getAbsolutePath())), what + ": the bin gives another file");
        assertNoUnnamedWaste();
    }

    private void assertNoUnnamedWaste() {
        String[] names = new File(bin().getWasteLocationPath()).list();
        assertFalse(Arrays.stream(names).anyMatch(name -> name.endsWith(".null")), "a file in the bin without an entry: " + Arrays.toString(names));
    }

    private String binListing() {
        return Arrays.toString(new File(bin().getWasteLocationPath()).list());
    }

    private void assertInPlaceAsOnDisk(String what) throws Exception {
        assertTrue(file.exists(), what + ": the file is not in its place");
        assertEquals(md5, FileSyncInstances.md5Of(file), what + ": another file is in its place");
        FsFile fsFile = entry("same.bin");
        assertNotNull(fsFile, what + ": no fs entry");
        assertTrue(fsFile.getSynced().v(), what + ": the fs entry says the file is not here");
        FsBashDetails disk = BashTools.Companion.getFsBashDetails(AbstractFileOf(file));
        assertEquals(disk.getiNode(), fsFile.getiNode().v(), what + ": the fs entry does not have the inode of the file");
        assertEquals(disk.getModified(), fsFile.getModified().v(), what + ": the fs entry does not have the time of the file");
        assertEquals(file.length(), fsFile.getSize().v(), what + ": the fs entry does not have the size of the file");
    }

    private static IFile AbstractFileOf(File file) {
        return de.mel.auth.file.AbstractFile.instance(file);
    }

    @Test
    public void aFileIsBinnedAndFound() throws Exception {
        try (FileSyncInstances ignored = synced(24800)) {
            bin().deleteFsEntry(entry("same.bin").getId().v());
            assertFalse(file.exists(), "the file is still in its place");
            assertFoundInBin("binned");
        }
    }

    /**
     * The fs entry of a file that came from the server has no inode of this disk. The bin must name the file by what the disk says.
     */
    @Test
    public void aFileWhoseFsEntryCameFromTheServerIsFoundInTheBin() throws Exception {
        try (FileSyncInstances ignored = synced(24810)) {
            FsFile ours = entry("same.bin");
            fsDao().deleteById(ours.getId().v());
            FsFile fromServer = entryFromServer(ours);
            fsDao().setSynced(fromServer.getId().v(), true);
            bin().deleteFsEntry(fromServer.getId().v());
            assertFoundInBin("binned with the fs entry of the server");
        }
    }

    /**
     * The fs entry has the inode of a file that is in the bin already (it is stale): the bin must not mix the two up.
     */
    @Test
    public void aFileWhoseFsEntryHasTheInodeOfAnotherBinnedFileIsFoundInTheBin() throws Exception {
        try (FileSyncInstances ignored = synced(24820)) {
            FsFile other = entry("other.bin");
            bin().deleteFsEntry(other.getId().v());
            FsFile ours = entry("same.bin");
            ours.getiNode().v(other.getiNode().v());
            fsDao().update(ours);
            bin().deleteFsEntry(ours.getId().v());
            assertFoundInBin("binned with a stale inode");
            assertNotNull(bin().getByHash(other.getContentHash().v()), "the other file got lost from the bin");
        }
    }

    /**
     * What a trace of the client sync showed: the client's own file is binned (it came back from the server as "not here yet"), restored, and binned again.
     */
    @Test
    public void aFileBinnedRestoredAndBinnedAgainIsFoundInTheBin() throws Exception {
        try (FileSyncInstances ignored = synced(24830)) {
            FsFile ours = entry("same.bin");
            bin().deleteFsEntry(ours.getId().v());
            entryFromServer(ours);
            wantedFromServer(md5, ours.getSize().v());

            bin().restoreFsFiles();
            assertInPlaceAsOnDisk("restored");

            bin().deleteFsEntry(entry("same.bin").getId().v());
            assertFalse(file.exists(), "the file is still in its place");
            assertFoundInBin("binned again");
        }
    }

    /**
     * The sync bins while the transfer manager restores. Whatever comes first: the file is in its place with its fs entry, or it is in the bin and the program finds it there.
     */
    @Test
    public void binAndRestoreAtTheSameTimeNeverLoseTheFile() throws Exception {
        try (FileSyncInstances ignored = synced(24840)) {
            FsFile ours = entry("same.bin");
            bin().deleteFsEntry(ours.getId().v());
            wantedFromServer(md5, ours.getSize().v());
            for (int round = 0; round < 50; round++) {
                FsFile fromServer = entryFromServer(ours);
                CountDownLatch go = new CountDownLatch(1);
                AtomicReference<Throwable> failed = new AtomicReference<>();
                Thread restore = new Thread(() -> {
                    try {
                        go.await();
                        bin().restoreFsFiles();
                    } catch (Throwable t) {
                        failed.set(t);
                    }
                });
                Thread binAgain = new Thread(() -> {
                    try {
                        go.await();
                        bin().deleteFsEntry(fromServer.getId().v());
                    } catch (Throwable t) {
                        failed.set(t);
                    }
                });
                restore.start();
                binAgain.start();
                go.countDown();
                restore.join();
                binAgain.join();
                assertNull(failed.get(), "round " + round + ": " + failed.get());

                FsFile now = entry("same.bin");
                if (file.exists()) {
                    assertNotNull(now, "round " + round + ": the file is in its place, but the program does not know it");
                    assertInPlaceAsOnDisk("round " + round);
                    // back into the bin for the next round
                    bin().deleteFsEntry(now.getId().v());
                } else {
                    assertNull(now, "round " + round + ": the program thinks the file is in its place");
                }
                assertFalse(file.exists(), "round " + round + ": the file did not go back to the bin");
                assertFoundInBin("round " + round);
            }
        }
    }
}
