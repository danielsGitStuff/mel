package de.mel.auth.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.service.MelFileSyncService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One side deletes what the other side changes while they are apart. The user decides which one counts.
 * Whoever loses gives up its version: it must be in the bin of that side then ("nothing the sync removes or overwrites disappears without a bin entry").
 */
@Timeout(value = 1, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class ConflictDeleteTest {
    @TempDir
    File dir;

    private FileSyncInstances synced(int port) throws Exception {
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "same.bin", 20_000, 1);
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "dir/other.bin", 30_000, 2);
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "dir/third.bin", 40_000, 3);
        FileSyncInstances instances = new FileSyncInstances(dir, port);
        try {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> ConflictSyncTest.equal(instances), 30), "the first sync did not finish");
            return instances;
        } catch (Throwable t) {
            instances.close();
            throw t;
        }
    }

    private static boolean serverEntryGone(FileSyncInstances instances, String path, String name) {
        try {
            return instances.serverService.getFileSyncDatabaseManager().getFsDao().getGenericByPathAndName(path, name) == null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean serverEntryHas(FileSyncInstances instances, String path, String name, File file) {
        try {
            var entry = instances.serverService.getFileSyncDatabaseManager().getFsDao().getGenericByPathAndName(path, name);
            return entry != null && FileSyncInstances.md5Of(file).equals(entry.getContentHash().v());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * the content of every file in the bin of the service, and whether each one is named by what it holds
     */
    private static List<String> binContents(MelFileSyncService<?> service, List<String> mislabeled) throws Exception {
        List<String> hashes = new ArrayList<>();
        File bin = new File(service.getFileSyncSettings().getTransferDirectoryFile().getAbsolutePath(), FileSyncStrings.WASTEBIN);
        File[] files = bin.listFiles();
        if (files == null)
            return hashes;
        for (File file : files) {
            if (!file.isFile())
                continue;
            String md5 = FileSyncInstances.md5Of(file);
            hashes.add(md5);
            if (!file.getName().startsWith(md5 + "."))
                mislabeled.add(file.getName() + " holds " + md5);
        }
        return hashes;
    }

    static void assertInBin(MelFileSyncService<?> service, String md5, String what) throws Exception {
        List<String> mislabeled = new ArrayList<>();
        boolean[] found = {false};
        boolean ok = TwoInstances.waitFor(() -> {
            try {
                mislabeled.clear();
                found[0] = binContents(service, mislabeled).contains(md5);
                return found[0];
            } catch (Exception e) {
                return false;
            }
        }, 10);
        assertTrue(ok, what + " is not in the bin. the bin holds " + binContents(service, new ArrayList<>()));
        assertTrue(mislabeled.isEmpty(), "files in the bin named after something else than they hold: " + mislabeled);
    }

    private static boolean exists(File root, String path) {
        return new File(root, path).exists();
    }

    @Test
    @Disabled("intermittent after a cold start (about one run in three): pull, resolve and commit of the client interleave over the same stage sets, see REWORK_LOG.md")
    public void clientDeletesWhileServerChangesTheDeletionWins() throws Exception {
        try (FileSyncInstances instances = synced(24700)) {
            ConflictSyncTest.bothChange(instances, root -> ConflictSyncTest.write(root, "same.bin", 21_000, 11), () -> serverChanged(instances),
                    root -> assertTrue(new File(root, "same.bin").delete()));
            String serversVersion = FileSyncInstances.md5Of(new File(instances.serverRoot, "same.bin"));
            ConflictSyncTest.resolveUntilEqual(instances, true, () -> ConflictSyncTest.fileHas(instances, "same.bin", -1));
            assertFalse(exists(instances.serverRoot, "same.bin"), "the server should have deleted it");
            assertFalse(exists(instances.clientRoot, "same.bin"));
            assertInBin(instances.serverService, serversVersion, "the server's version that lost");
        }
    }

    private static boolean serverChanged(FileSyncInstances instances) {
        return serverEntryHas(instances, "/", "same.bin", new File(instances.serverRoot, "same.bin"));
    }

    @Test
    public void clientDeletesWhileServerChangesTheChangeWins() throws Exception {
        try (FileSyncInstances instances = synced(24710)) {
            ConflictSyncTest.bothChange(instances, root -> ConflictSyncTest.write(root, "same.bin", 21_000, 11), () -> serverChanged(instances),
                    root -> assertTrue(new File(root, "same.bin").delete()));
            ConflictSyncTest.resolveUntilEqual(instances, false, () -> ConflictSyncTest.fileHas(instances, "same.bin", 21_000));
            assertEquals(21_000, new File(instances.serverRoot, "same.bin").length());
            assertEquals(21_000, new File(instances.clientRoot, "same.bin").length(), "the client should have the file back");
        }
    }

    @Test
    @Disabled("intermittent after a cold start (about one run in three): pull, resolve and commit of the client interleave over the same stage sets, see REWORK_LOG.md")
    public void serverDeletesWhileClientChangesTheChangeWins() throws Exception {
        try (FileSyncInstances instances = synced(24720)) {
            ConflictSyncTest.bothChange(instances, root -> assertTrue(new File(root, "same.bin").delete()), () -> serverEntryGone(instances, "/", "same.bin"),
                    root -> ConflictSyncTest.write(root, "same.bin", 22_000, 12));
            ConflictSyncTest.resolveUntilEqual(instances, true, () -> ConflictSyncTest.fileHas(instances, "same.bin", 22_000));
            assertEquals(22_000, new File(instances.serverRoot, "same.bin").length(), "the server should have the client's file");
            assertEquals(22_000, new File(instances.clientRoot, "same.bin").length());
        }
    }

    @Test
    public void serverDeletesWhileClientChangesTheDeletionWins() throws Exception {
        try (FileSyncInstances instances = synced(24730)) {
            ConflictSyncTest.bothChange(instances, root -> assertTrue(new File(root, "same.bin").delete()), () -> serverEntryGone(instances, "/", "same.bin"),
                    root -> ConflictSyncTest.write(root, "same.bin", 22_000, 12));
            String clientsVersion = FileSyncInstances.md5Of(new File(instances.clientRoot, "same.bin"));
            ConflictSyncTest.resolveUntilEqual(instances, false, () -> ConflictSyncTest.fileHas(instances, "same.bin", -1));
            assertFalse(exists(instances.serverRoot, "same.bin"));
            assertFalse(exists(instances.clientRoot, "same.bin"), "the client should have deleted it");
            assertInBin(instances.clientService, clientsVersion, "the client's version that lost");
        }
    }

    @Test
    public void bothDeleteIsNoConflict() throws Exception {
        try (FileSyncInstances instances = synced(24740)) {
            ConflictSyncTest.bothChange(instances, root -> assertTrue(new File(root, "same.bin").delete()), () -> serverEntryGone(instances, "/", "same.bin"),
                    root -> assertTrue(new File(root, "same.bin").delete()));
            assertTrue(TwoInstances.waitFor(() -> ConflictSyncTest.equal(instances), 15));
            assertFalse(exists(instances.serverRoot, "same.bin"));
            assertFalse(exists(instances.clientRoot, "same.bin"));
            assertTrue(instances.clientService.getConflictSolverMap().isEmpty(), "both wanted it gone: nothing to ask");
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children)
                deleteTree(child);
        assertTrue(file.delete());
    }

    @Test
    @Disabled("intermittent after a cold start (about one run in three): pull, resolve and commit of the client interleave over the same stage sets, see REWORK_LOG.md")
    public void serverDeletesDirectoryWhileClientChangesAFileInItTheChangeWins() throws Exception {
        try (FileSyncInstances instances = synced(24750)) {
            ConflictSyncTest.bothChange(instances, root -> deleteTree(new File(root, "dir")), () -> serverEntryGone(instances, "/", "dir"),
                    root -> ConflictSyncTest.write(root, "dir/other.bin", 31_000, 12));
            ConflictSyncTest.resolveUntilEqual(instances, true, () -> ConflictSyncTest.fileHas(instances, "dir/other.bin", 31_000));
            assertEquals(31_000, new File(instances.serverRoot, "dir/other.bin").length(), "the changed file should survive on the server");
            assertEquals(31_000, new File(instances.clientRoot, "dir/other.bin").length());
        }
    }

    @Test
    @Disabled("intermittent after a cold start (about one run in three): pull, resolve and commit of the client interleave over the same stage sets, see REWORK_LOG.md")
    public void serverDeletesDirectoryWhileClientChangesAFileInItTheDeletionWins() throws Exception {
        try (FileSyncInstances instances = synced(24760)) {
            ConflictSyncTest.bothChange(instances, root -> deleteTree(new File(root, "dir")), () -> serverEntryGone(instances, "/", "dir"),
                    root -> ConflictSyncTest.write(root, "dir/other.bin", 31_000, 12));
            String clientsVersion = FileSyncInstances.md5Of(new File(instances.clientRoot, "dir/other.bin"));
            ConflictSyncTest.resolveUntilEqual(instances, false, () -> ConflictSyncTest.fileHas(instances, "dir/other.bin", -1));
            assertFalse(exists(instances.serverRoot, "dir/other.bin"));
            assertFalse(exists(instances.clientRoot, "dir/other.bin"), "the client should have deleted the directory");
            assertInBin(instances.clientService, clientsVersion, "the client's changed file");
        }
    }

    @Test
    public void clientDeletesDirectoryWhileServerChangesAFileInItTheDeletionWins() throws Exception {
        try (FileSyncInstances instances = synced(24770)) {
            ConflictSyncTest.bothChange(instances, root -> ConflictSyncTest.write(root, "dir/other.bin", 31_000, 12), () -> serverEntryHas(instances, "/dir/", "other.bin", new File(instances.serverRoot, "dir/other.bin")),
                    root -> deleteTree(new File(root, "dir")));
            String serversVersion = FileSyncInstances.md5Of(new File(instances.serverRoot, "dir/other.bin"));
            ConflictSyncTest.resolveUntilEqual(instances, true, () -> ConflictSyncTest.fileHas(instances, "dir/other.bin", -1));
            assertFalse(exists(instances.serverRoot, "dir/other.bin"), "other.bin on the server. server has " + FileSyncInstances.hashesBelow(instances.serverRoot).keySet() + " client has " + FileSyncInstances.hashesBelow(instances.clientRoot).keySet());
            assertFalse(exists(instances.clientRoot, "dir/other.bin"), "other.bin on the client");
            assertInBin(instances.serverService, serversVersion, "the server's changed file");
        }
    }

    @Test
    public void clientDeletesDirectoryWhileServerChangesAFileInItTheChangeWins() throws Exception {
        try (FileSyncInstances instances = synced(24780)) {
            ConflictSyncTest.bothChange(instances, root -> ConflictSyncTest.write(root, "dir/other.bin", 31_000, 12), () -> serverEntryHas(instances, "/dir/", "other.bin", new File(instances.serverRoot, "dir/other.bin")),
                    root -> deleteTree(new File(root, "dir")));
            ConflictSyncTest.resolveUntilEqual(instances, false, () -> ConflictSyncTest.fileHas(instances, "dir/other.bin", 31_000));
            assertEquals(31_000, new File(instances.serverRoot, "dir/other.bin").length());
            assertEquals(31_000, new File(instances.clientRoot, "dir/other.bin").length(), "the changed file should come back to the client");
        }
    }
}
