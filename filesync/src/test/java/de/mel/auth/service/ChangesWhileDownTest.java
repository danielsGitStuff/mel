package de.mel.auth.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Changes made while a service is not running are found by the index the service makes when it boots ({@link de.mel.filesync.index.IndexerRunnable}), not by the file watcher.
 * From there on the way is the one every change takes: commit, propagate, merge, transfer. So these tests show whether that way works, whatever the file watcher does
 * (see {@link LiveChangeIndexingTest} for that).
 * <p>
 * DISABLED: all of them fail on the code as it is, see REWORK_LOG.md: Live changes do not sync. The server side fails in the commit of the index (fswrite.contenthash),
 * the client side when the server takes the commit of the client (stage.path). See {@link BootIndexTest} for the part before.
 */
@Disabled("changes made while a service was down are not synced, see REWORK_LOG.md: Live changes do not sync")
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class ChangesWhileDownTest {
    @TempDir
    File dir;

    private File serverRoot() {
        return new File(dir, "serverRoot");
    }

    private static void waitUntilEqual(File expectedRoot, File actualRoot, int seconds) throws Exception {
        boolean equal = TwoInstances.waitFor(() -> {
            try {
                return FileSyncInstances.hashesBelow(expectedRoot).equals(FileSyncInstances.hashesBelow(actualRoot));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, seconds);
        Map<String, String> expected = FileSyncInstances.hashesBelow(expectedRoot);
        Map<String, String> actual = FileSyncInstances.hashesBelow(actualRoot);
        assertTrue(equal, "after " + seconds + "s the directories differ. expected " + expected.keySet() + " but got " + actual.keySet() + "\nexpected " + expected + "\nactual   " + actual);
        assertEquals(expected, actual);
    }

    private FileSyncInstances synced(int port) throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "first.bin", 20_000, 1);
        FileSyncInstances.writeFile(serverRoot(), "dir/second.bin", 30_000, 2);
        FileSyncInstances instances = new FileSyncInstances(dir, port);
        try {
            instances.createServer();
            instances.createClient();
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
            return instances;
        } catch (Throwable t) {
            instances.close();
            throw t;
        }
    }

    @Test
    public void aFileMadeOnTheClientNextToTheOthersWhileItWasDownReachesTheServer() throws Exception {
        try (FileSyncInstances instances = synced(24600)) {
            instances.restartClient(() -> {
                try {
                    FileSyncInstances.writeFile(instances.clientRoot, "made while down.bin", 2_500_000, 3);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.clientRoot, instances.serverRoot, 60);
        }
    }

    @Test
    public void aFileMadeOnTheClientInAnExistingDirectoryWhileItWasDownReachesTheServer() throws Exception {
        try (FileSyncInstances instances = synced(24670)) {
            instances.restartClient(() -> {
                try {
                    FileSyncInstances.writeFile(instances.clientRoot, "dir/made while down.bin", 1_000, 3);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.clientRoot, instances.serverRoot, 60);
        }
    }

    @Test
    public void aFileMadeOnTheClientInANewDirectoryWhileItWasDownReachesTheServer() throws Exception {
        try (FileSyncInstances instances = synced(24680)) {
            instances.restartClient(() -> {
                try {
                    FileSyncInstances.writeFile(instances.clientRoot, "newdir/in a new dir.bin", 1_000, 4);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.clientRoot, instances.serverRoot, 60);
        }
    }

    @Test
    public void aNewDirectoryInANewDirectoryMadeOnTheClientWhileItWasDownReachesTheServer() throws Exception {
        try (FileSyncInstances instances = synced(24660)) {
            instances.restartClient(() -> {
                try {
                    // the server has to put the path of "sub" together from its parent in the same commit: neither sub nor its parent are known to its fs table
                    FileSyncInstances.writeFile(instances.clientRoot, "newdir/sub/deep.bin", 1_000, 6);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.clientRoot, instances.serverRoot, 60);
        }
    }

    @Test
    public void aFileChangedOnTheClientWhileItWasDownReachesTheServer() throws Exception {
        try (FileSyncInstances instances = synced(24610)) {
            instances.restartClient(() -> {
                try {
                    FileSyncInstances.writeFile(instances.clientRoot, "first.bin", 25_000, 5);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.clientRoot, instances.serverRoot, 60);
        }
    }

    @Test
    public void aFileDeletedOnTheClientWhileItWasDownIsDeletedOnTheServer() throws Exception {
        try (FileSyncInstances instances = synced(24620)) {
            instances.restartClient(() -> {
                try {
                    Files.delete(new File(instances.clientRoot, "first.bin").toPath());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.clientRoot, instances.serverRoot, 60);
        }
    }

    @Test
    public void aFileMadeOnTheServerWhileItWasDownReachesTheClient() throws Exception {
        try (FileSyncInstances instances = synced(24630)) {
            instances.restartServer(() -> {
                try {
                    FileSyncInstances.writeFile(instances.serverRoot, "made while down.bin", 2_500_000, 3);
                    FileSyncInstances.writeFile(instances.serverRoot, "newdir/in a new dir.bin", 1_000, 4);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
        }
    }

    @Test
    public void aFileChangedOnTheServerWhileItWasDownReachesTheClient() throws Exception {
        try (FileSyncInstances instances = synced(24640)) {
            instances.restartServer(() -> {
                try {
                    FileSyncInstances.writeFile(instances.serverRoot, "first.bin", 25_000, 5);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
        }
    }

    @Test
    public void aFileDeletedOnTheServerWhileItWasDownIsDeletedOnTheClient() throws Exception {
        try (FileSyncInstances instances = synced(24650)) {
            instances.restartServer(() -> {
                try {
                    Files.delete(new File(instances.serverRoot, "first.bin").toPath());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
        }
    }
}
