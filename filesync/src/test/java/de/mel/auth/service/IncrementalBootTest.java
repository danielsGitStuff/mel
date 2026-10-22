package de.mel.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import de.mel.auth.service.FileSyncInstances.Snapshot;
import de.mel.filesync.data.FileSyncSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The index that runs when a service starts only looks at what changed since the last one that went through (the file systems of old phones cannot take a look at every file
 * each time), unless the settings or the circumstances say otherwise. A look at everything is started by hand.
 * <p>
 * To see the difference a change is made while the service is down and hidden from the start: the saved time of the last scan is set after the change. A user who
 * does that to a file deserves that it is not noticed.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class IncrementalBootTest {
    @TempDir
    File dir;

    @AfterEach
    public void releaseTheCommit() {
        FileSyncInstances.RecordingServerService.holdBackCommit = false;
    }

    /**
     * The server indexes at its first start, then it is shut down, changes.bin is changed, {@code settingsWhileDown} sets up the settings, and it starts again.
     *
     * @return the instances, the server is running again and has indexed
     */
    private FileSyncInstances restartedWithChangeAndSettings(int port, Consumer<FileSyncSettings> settingsWhileDown) throws Exception {
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "changes.bin", 20_000, 1);
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "stays.bin", 21_000, 2);
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "dir/in a dir.bin", 22_000, 3);
        FileSyncInstances instances = new FileSyncInstances(dir, port);
        try {
            instances.createServer();
            assertTrue(TwoInstances.waitFor(() -> !instances.serverService.indexed.isEmpty(), TwoInstances.TIMEOUT_SECONDS), "the server did not index at first boot");
            assertNotNull(instances.serverService.getFileSyncSettings().getLastScanTime(), "the first index should save the time it began");
            FileSyncInstances.RecordingServerService.holdBackCommit = true;
            instances.restartServer(() -> {
                try {
                    FileSyncInstances.writeFile(instances.serverRoot, "changes.bin", 30_000, 9);
                    // more than the margin the start looks back (plus up to a second: find rounds the time down), so the change is hidden
                    Thread.sleep(FileSyncInstances.SCAN_MARGIN_MS + 1_300);
                    FileSyncSettings settings = instances.serverService.getFileSyncSettings();
                    settings.setLastScanTime(System.currentTimeMillis());
                    settingsWhileDown.accept(settings);
                    settings.save();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertTrue(TwoInstances.waitFor(() -> !instances.serverService.indexed.isEmpty(), TwoInstances.TIMEOUT_SECONDS), "the server did not index at the second boot");
            return instances;
        } catch (Throwable t) {
            instances.close();
            throw t;
        }
    }

    private static Snapshot firstIndexOf(FileSyncInstances instances) throws Exception {
        return Snapshot.of(instances.serverService.getFileSyncDatabaseManager(), instances.serverService.indexed.get(0).stageSetId());
    }

    @Test
    public void aStartLooksOnlyAtWhatChangedSinceTheLastScanAndTheFullScanFindsTheRest() throws Exception {
        try (FileSyncInstances instances = restartedWithChangeAndSettings(24900, settings -> {
        })) {
            assertNull(firstIndexOf(instances).stage("/", "changes.bin"), "the start looked at a file that did not change since the last scan: " + firstIndexOf(instances));
            int before = instances.serverService.indexed.size();
            instances.serverService.fullScan();
            assertTrue(TwoInstances.waitFor(() -> instances.serverService.indexed.size() > before, TwoInstances.TIMEOUT_SECONDS), "the full scan did not report");
            Snapshot full = Snapshot.of(instances.serverService.getFileSyncDatabaseManager(), instances.serverService.indexed.get(before).stageSetId());
            assertNotNull(full.stage("/", "changes.bin"), "the full scan did not find the change: " + full);
            assertNull(full.stage("/", "stays.bin"), "what did not change is not part of it: " + full);
            // and the full scan counts as a scan that went through
            assertTrue(instances.serverService.getFileSyncSettings().getLastScanTime() > 0);
        }
    }

    @Test
    public void withoutFastBootEveryFileIsLookedAtAtTheStart() throws Exception {
        try (FileSyncInstances instances = restartedWithChangeAndSettings(24910, settings -> settings.setFastBoot(false))) {
            assertNotNull(firstIndexOf(instances).stage("/", "changes.bin"), "the start should look at every file: " + firstIndexOf(instances));
        }
    }

    @Test
    public void aClockThatWentBackMeansEveryFileIsLookedAt() throws Exception {
        try (FileSyncInstances instances = restartedWithChangeAndSettings(24920, settings -> settings.setLastScanTime(System.currentTimeMillis() + 3_600_000L))) {
            assertNotNull(firstIndexOf(instances).stage("/", "changes.bin"), "a scan time in the future cannot be trusted: " + firstIndexOf(instances));
        }
    }

    @Test
    public void aStartWithoutASavedTimeLooksAtEveryFile() throws Exception {
        try (FileSyncInstances instances = restartedWithChangeAndSettings(24930, settings -> settings.setLastScanTime(null))) {
            assertNotNull(firstIndexOf(instances).stage("/", "changes.bin"), "without a saved time everything has to be looked at: " + firstIndexOf(instances));
        }
    }
}
