package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Changes after the first sync travel in both directions. Files made on the client are fetched by the server, the other way round the client fetches.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class FileSyncRoundTripTest {
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
        assertTrue(equal, "after " + seconds + "s the directories differ. expected " + expected.keySet() + " but got " + actual.keySet());
        assertEquals(expected, actual);
    }

    @Test
    public void aFileCreatedOnTheClientReachesTheServer() throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "first.bin", 20_000, 1);
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24300)) {
            instances.createServer();
            instances.createClient();
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
            FileSyncInstances.writeFile(instances.clientRoot, "made on the client.bin", 2_500_000, 2);
            FileSyncInstances.writeFile(instances.clientRoot, "dir/other.bin", 1_000, 3);
            waitUntilEqual(instances.clientRoot, instances.serverRoot, 60);
        }
    }

    @Test
    public void aFileCreatedOnTheServerAfterTheFirstSyncReachesTheClient() throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "first.bin", 20_000, 1);
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24310)) {
            instances.createServer();
            instances.createClient();
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
            FileSyncInstances.writeFile(instances.serverRoot, "later.bin", 2_500_000, 2);
            FileSyncInstances.writeFile(instances.serverRoot, "newdir/in a new dir.bin", 1_000, 3);
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
        }
    }

    @Test
    public void aFileDeletedOnTheServerIsDeletedOnTheClient() throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "stays.bin", 20_000, 1);
        FileSyncInstances.writeFile(serverRoot(), "goes.bin", 20_000, 2);
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24320)) {
            instances.createServer();
            instances.createClient();
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
            assertTrue(new File(instances.serverRoot, "goes.bin").delete());
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
            assertTrue(new File(instances.clientRoot, "stays.bin").exists());
        }
    }

    @Test
    public void aFileChangedOnTheServerIsChangedOnTheClient() throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "changes.bin", 200_000, 1);
        try (FileSyncInstances instances = new FileSyncInstances(dir, 24330)) {
            instances.createServer();
            instances.createClient();
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
            Files.write(new File(instances.serverRoot, "changes.bin").toPath(), FileSyncInstances.content(300_000, 99));
            waitUntilEqual(instances.serverRoot, instances.clientRoot, 60);
        }
    }
}
