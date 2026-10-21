package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import de.mel.auth.service.FileSyncInstances.Snapshot;

import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.StageSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the file watcher's indexer ({@link de.mel.filesync.index.watchdog.StageIndexerRunnable}) hands over when a file changes while the service runs.
 * <p>
 * The contract is the one {@link de.mel.filesync.index.IndexerRunnable} (the first index at boot) keeps and the listeners of the services rely on
 * (see {@link de.mel.filesync.service.MelFileSyncServerService#startIndexer()}): the id that is given to the "staging done" listener
 * is a stage set that exists, has the source FS and the status STAGED, and holds one stage for each change. Files have their content hash, because the commit needs it.
 * <p>
 * Only the server service is started and its listener is replaced by one that records what it gets. So nothing is committed, merged or transferred: this is only about the indexing.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class LiveChangeIndexingTest {
    @TempDir
    File dir;

    private class Server implements AutoCloseable {
        final FileSyncInstances instances;
        final FileSyncDatabaseManager database;
        final List<Snapshot> handed = new CopyOnWriteArrayList<>();

        Server(int port, File root) throws Exception {
            instances = new FileSyncInstances(dir, port);
            instances.createServer();
            database = instances.serverService.getFileSyncDatabaseManager();
            // not the server's own listener: nothing may commit what is handed over
            instances.serverService.getStageIndexer().setStagingDoneListener(stageSetId -> {
                // what the stage set holds right now. A later change of the indexer must not make this look better than it is
                try {
                    handed.add(Snapshot.of(database, stageSetId));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        /**
         * the first index is done when the file is known with its hash
         */
        void awaitFirstIndex(String name) throws Exception {
            assertTrue(TwoInstances.waitFor(() -> {
                try {
                    GenericFSEntry entry = database.getFsDao().getGenericByPathAndName("/", name);
                    return entry != null && entry.getContentHash().v() != null;
                } catch (Exception e) {
                    return false;
                }
            }, 60), "the server did not index " + name);
        }

        /**
         * every stage set in the database, to see what became of the ones that were not handed over
         */
        String dump() {
            StringBuilder builder = new StringBuilder();
            try {
                for (long id = 1; id < 12; id++) {
                    StageSet stageSet = database.getStageDao().getStageSetById(id);
                    if (stageSet != null)
                        builder.append("\n  ").append(Snapshot.of(database, id));
                }
            } catch (Exception e) {
                builder.append(e);
            }
            return builder.toString();
        }

        /**
         * @return what the watcher handed over that fits, as soon as it did. Fails with everything it handed over.
         */
        Snapshot await(String what, Predicate<Snapshot> fits) throws Exception {
            List<Snapshot> match = new ArrayList<>();
            boolean found = TwoInstances.waitFor(() -> {
                for (Snapshot h : handed)
                    if (fits.test(h)) {
                        match.add(h);
                        return true;
                    }
                return false;
            }, 40);
            assertTrue(found, "no stage set with " + what + " was handed over by the file watcher's indexer. It handed over " + handed.size() + ": " + handed + "\nthe database has:" + dump());
            return match.get(0);
        }

        @Override
        public void close() {
            instances.close();
        }
    }

    private File serverRoot() {
        return new File(dir, "serverRoot");
    }

    @Test
    public void aFileWhoseContentChangedIsHandedOverStaged() throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "changes.bin", 20_000, 1);
        FileSyncInstances.writeFile(serverRoot(), "stays.bin", 20_000, 2);
        try (Server server = new Server(24400, serverRoot())) {
            server.awaitFirstIndex("changes.bin");
            server.awaitFirstIndex("stays.bin");
            FileSyncInstances.writeFile(server.instances.serverRoot, "changes.bin", 30_000, 3);
            Snapshot handed = server.await("changes.bin, staged", h -> h.staged() && h.stage("/", "changes.bin") != null);
            Stage stage = handed.stage("/", "changes.bin");
            assertFalse(Snapshot.deleted(stage));
            assertEquals(FileSyncInstances.md5Of(new File(server.instances.serverRoot, "changes.bin")), stage.getContentHash(), "the stage needs the content hash of the new content, the commit writes it to fswrite");
            assertEquals(30_000L, stage.getSize());
            GenericFSEntry known = server.database.getFsDao().getGenericByPathAndName("/", "changes.bin");
            assertEquals(known.getId().v(), stage.getFsId(), "it is the file the fs table already knows");
            assertEquals(null, handed.stage("/", "stays.bin"), "what did not change is not part of the delta " + handed);
        }
    }

    @Test
    public void aNewFileInANewDirectoryIsHandedOverStagedWithItsDirectory() throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "first.bin", 20_000, 1);
        try (Server server = new Server(24410, serverRoot())) {
            server.awaitFirstIndex("first.bin");
            FileSyncInstances.writeFile(server.instances.serverRoot, "newdir/in a new dir.bin", 1_000, 2);
            Snapshot handed = server.await("newdir/in a new dir.bin, staged", h -> h.staged() && h.stage("/newdir/", "in a new dir.bin") != null);
            Stage file = handed.stage("/newdir/", "in a new dir.bin");
            assertEquals(FileSyncInstances.md5Of(new File(server.instances.serverRoot, "newdir/in a new dir.bin")), file.getContentHash());
            assertEquals(null, file.getFsId(), "the fs table does not know it yet");
            Stage directory = handed.stage("/", "newdir");
            assertNotNull(directory, "the new directory is part of the delta " + handed);
            assertEquals(Boolean.TRUE, directory.getIsDirectory());
            assertEquals(directory.getId(), file.getParentId(), "the file hangs below its directory");
        }
    }

    @Test
    public void aDeletedFileIsHandedOverStagedAsDeleted() throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "goes.bin", 20_000, 1);
        FileSyncInstances.writeFile(serverRoot(), "stays.bin", 20_000, 2);
        try (Server server = new Server(24420, serverRoot())) {
            server.awaitFirstIndex("goes.bin");
            server.awaitFirstIndex("stays.bin");
            Files.delete(new File(server.instances.serverRoot, "goes.bin").toPath());
            Snapshot handed = server.await("goes.bin as deleted, staged", h -> h.staged() && h.stage("/", "goes.bin") != null && Snapshot.deleted(h.stage("/", "goes.bin")));
            GenericFSEntry known = server.database.getFsDao().getGenericByPathAndName("/", "goes.bin");
            assertEquals(known.getId().v(), handed.stage("/", "goes.bin").getFsId(), "it is the file the fs table knows");
            assertEquals(null, handed.stage("/", "stays.bin"), "what did not change is not part of the delta " + handed);
        }
    }

    @Test
    public void whatIsHandedOverStillExistsWhenTheListenerLooksAtIt() throws Exception {
        FileSyncInstances.writeFile(serverRoot(), "first.bin", 20_000, 1);
        try (Server server = new Server(24430, serverRoot())) {
            server.awaitFirstIndex("first.bin");
            FileSyncInstances.writeFile(server.instances.serverRoot, "second.bin", 2_000, 2);
            // whatever the listener is given, the services pass it on to commitStage(id): there has to be a stage set behind the id
            TwoInstances.waitFor(() -> !server.handed.isEmpty(), 40);
            assertTrue(!server.handed.isEmpty(), "the watcher's indexer did not call the listener at all");
            for (Snapshot h : server.handed)
                assertNotNull(h.stageSet(), "the listener was given an id with no stage set behind: " + h);
        }
    }
}
