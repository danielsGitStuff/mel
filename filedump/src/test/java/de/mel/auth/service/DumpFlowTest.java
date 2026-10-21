package de.mel.auth.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.dump.DumpBootloader;
import de.mel.dump.DumpCreateServiceHelper;
import de.mel.dump.SourceService;
import de.mel.dump.TargetService;
import de.mel.filesync.FileSyncBootloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file dump is a one way backup: a source (a file sync client) hands its files to a target (a file sync server that takes whatever it is offered and never gives back).
 * The target is {@link TargetService}, the source {@link SourceService}. Both run in this process, paired like the file sync tests do it.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
@Disabled("the dump does not work: nothing arrives at the target, see REWORK_LOG.md, section 'File dump'. Enable when TargetSyncHandler is repaired.")
public class DumpFlowTest {
    @TempDir
    File dir;

    private class Dump implements AutoCloseable {
        final TwoInstances two;
        final File targetRoot = new File(dir, "targetRoot");
        final File sourceRoot = new File(dir, "sourceRoot");
        TargetService target;
        SourceService source;

        Dump(int portBase) throws Exception {
            two = new TwoInstances(dir, portBase, TwoInstances.ACCEPT_ALL, TwoInstances.ACCEPT_ALL, DumpBootloader.class);
            assertTrue(targetRoot.isDirectory() || targetRoot.mkdirs());
            assertTrue(sourceRoot.isDirectory() || sourceRoot.mkdirs());
            two.pair();
        }

        void startTarget() throws Exception {
            AtomicReference<TargetService> booted = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = service -> {
                booted.set((TargetService) service);
                latch.countDown();
            };
            IFile root = AbstractFile.instance(targetRoot);
            new DumpCreateServiceHelper((de.mel.auth.service.MelAuthServiceImpl) two.server).createServerService("target", root, .5f, 666, false);
            assertTrue(latch.await(30, TimeUnit.SECONDS), "the target did not boot");
            target = booted.get();
            Long sourceAtTarget = TwoInstances.idOf(two.client, two.server);
            for (ServiceJoinServiceType service : two.server.getDatabaseManager().getAllServices())
                if (service.getUuid().equalsValue(target.getUuid()))
                    two.server.getDatabaseManager().grant(service.getServiceId().v(), sourceAtTarget);
        }

        void startSource() throws Exception {
            assertNotNull(target, "no target");
            AtomicReference<SourceService> booted = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = service -> {
                booted.set((SourceService) service);
                latch.countDown();
            };
            IFile root = AbstractFile.instance(sourceRoot);
            Long targetAtSource = TwoInstances.idOf(two.server, two.client);
            new DumpCreateServiceHelper((de.mel.auth.service.MelAuthServiceImpl) two.client).createClientService("source", root, targetAtSource, target.getUuid(), .5f, 666, false);
            assertTrue(latch.await(30, TimeUnit.SECONDS), "the source did not boot");
            source = booted.get();
        }

        @Override
        public void close() {
            two.close();
        }
    }

    /**
     * the files of the target (below its root, without the service's own directories) must hold everything of expected, with the same content
     */
    private static void waitUntilHolds(File expectedRoot, File actualRoot, int seconds) throws Exception {
        boolean holds = TwoInstances.waitFor(() -> {
            try {
                return FileSyncInstances.hashesBelow(actualRoot).entrySet().containsAll(FileSyncInstances.hashesBelow(expectedRoot).entrySet());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, seconds);
        Map<String, String> expected = FileSyncInstances.hashesBelow(expectedRoot);
        Map<String, String> actual = FileSyncInstances.hashesBelow(actualRoot);
        assertTrue(holds, "after " + seconds + "s the target does not hold everything of the source. source " + expected + "\ntarget " + actual);
    }

    @Test
    public void filesOfTheSourceArriveAtTheTarget() throws Exception {
        FileSyncInstances.writeFile(new File(dir, "sourceRoot"), "first.bin", 20_000, 1);
        FileSyncInstances.writeFile(new File(dir, "sourceRoot"), "dir/second.bin", 30_000, 2);
        FileSyncInstances.writeFile(new File(dir, "sourceRoot"), "dir/deeper/third.bin", 1_500_000, 3);
        try (Dump dump = new Dump(24800)) {
            dump.startTarget();
            dump.startSource();
            waitUntilHolds(dump.sourceRoot, dump.targetRoot, 90);
        }
    }

    @Test
    public void aFileMadeAtTheSourceLaterArrivesToo() throws Exception {
        FileSyncInstances.writeFile(new File(dir, "sourceRoot"), "first.bin", 20_000, 1);
        try (Dump dump = new Dump(24810)) {
            dump.startTarget();
            dump.startSource();
            waitUntilHolds(dump.sourceRoot, dump.targetRoot, 90);
            FileSyncInstances.writeFile(dump.sourceRoot, "later.bin", 40_000, 4);
            FileSyncInstances.writeFile(dump.sourceRoot, "newdir/in a new dir.bin", 2_000, 5);
            waitUntilHolds(dump.sourceRoot, dump.targetRoot, 90);
        }
    }

    @Test
    public void aFileThatIsAlreadyAtTheTargetIsNotOverwrittenBothAreKept() throws Exception {
        String mineSha = FileSyncInstances.writeFile(new File(dir, "targetRoot"), "same name.bin", 10_000, 7);
        String theirsSha = FileSyncInstances.writeFile(new File(dir, "sourceRoot"), "same name.bin", 12_000, 8);
        try (Dump dump = new Dump(24820)) {
            dump.startTarget();
            dump.startSource();
            boolean both = TwoInstances.waitFor(() -> {
                try {
                    return FileSyncInstances.hashesBelow(dump.targetRoot).values().containsAll(java.util.List.of(mineSha, theirsSha));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 90);
            assertTrue(both, "the target should hold both versions: " + FileSyncInstances.hashesBelow(dump.targetRoot));
            assertEquals(mineSha, FileSyncInstances.hashesBelow(dump.targetRoot).get("same name.bin"), "the file that was there first keeps its name");
        }
    }
}
