package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.mel.auth.data.EmptyPayload;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.jobs.CommitJob;
import de.mel.filesync.jobs.SyncClientJob;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The client syncs in sessions, one at a time (see SYNC_SESSION_PLAN.md). The server holds back its answers to commits here, so that the window in which
 * the client used to start a second round on the same stage sets (the old race) is as long as the test wants.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class SyncSessionTest {
    @TempDir
    File dir;

    private FileSyncInstances synced(int port) throws Exception {
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "same.bin", 20_000, 1);
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "dir/other.bin", 30_000, 2);
        FileSyncInstances instances = new FileSyncInstances(dir, port);
        try {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> ConflictSyncTest.equal(instances), TwoInstances.TIMEOUT_SECONDS), "the first sync did not finish");
            return instances;
        } catch (Throwable t) {
            instances.close();
            throw t;
        }
    }

    /**
     * @return true if the client's service worker answered a request of the server
     */
    private static boolean clientAnswers(FileSyncInstances instances) throws Exception {
        CountDownLatch answered = new CountDownLatch(1);
        Long clientAtServer = TwoInstances.idOf(instances.two.client, instances.two.server);
        instances.two.server.connect(clientAtServer).onDone(mvp -> {
            try {
                EmptyPayload payload = new EmptyPayload();
                payload.setIntent(FileSyncStrings.INTENT_DRIVE_DETAILS);
                mvp.request(instances.clientService.getUuid(), payload).onDone(answer -> answered.countDown());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return answered.await(TwoInstances.TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static int openQuestions(FileSyncInstances instances) {
        int open = 0;
        for (ConflictSolver solver : new ArrayList<>(instances.clientService.getConflictSolverMap().values()))
            if (solver.isSolving() && !solver.isSolved() && !solver.getConflictMap().isEmpty())
                open++;
        return open;
    }

    @Test
    public void whileACommitWaitsForItsAnswerTheClientStartsNothingElseAndStillAnswersMessages() throws Exception {
        try (FileSyncInstances instances = synced(25000)) {
            FileSyncInstances.RecordingServerService server = instances.serverService;
            server.holdCommits = true;
            int commitsBefore = server.commitsReceived.get();
            ConflictSyncTest.write(instances.clientRoot, "a.bin", 10_000, 31);
            assertTrue(TwoInstances.waitFor(() -> server.commitsReceived.get() > commitsBefore, TwoInstances.TIMEOUT_SECONDS), "the client did not commit its change");
            int pulls = server.pullsReceived.get();

            // every reason to sync again: another change, the jobs that start a sync
            ConflictSyncTest.write(instances.clientRoot, "b.bin", 11_000, 32);
            instances.clientService.addJob(new SyncClientJob());
            instances.clientService.addJob(new CommitJob());
            instances.clientService.addJob(new CommitJob(true));
            assertTrue(clientAnswers(instances), "the client's worker did not answer a request while its commit waited");
            // time to index b.bin and to do whatever the jobs do
            Thread.sleep(FileSyncInstances.QUIET_MS * 5 + 500);
            assertEquals(commitsBefore + 1, server.commitsReceived.get(), "the client sent another commit while the first one waited for its answer");
            assertEquals(pulls, server.pullsReceived.get(), "the client pulled while its commit waited for its answer");

            server.releaseCommits();
            assertTrue(TwoInstances.waitFor(() -> ConflictSyncTest.equal(instances) && ConflictSyncTest.fileHas(instances, "a.bin", 10_000)
                    && ConflictSyncTest.fileHas(instances, "b.bin", 11_000), TwoInstances.TIMEOUT_SECONDS), "the changes of the client did not reach the server");
            assertTrue(TwoInstances.waitFor(() -> ConflictSyncTest.nothingLeftToMerge(instances), TwoInstances.TIMEOUT_SECONDS), "stage sets are left over");
        }
    }

    /**
     * The user answers, the result waits for the server's answer, the server moves on meanwhile: the commit is too old. The next session pulls again,
     * meets the same two versions and takes the answer that was given, without asking again.
     */
    @Test
    public void aCommitThatIsTooOldIsResolvedAgainWithTheAnswerGivenBefore() throws Exception {
        try (FileSyncInstances instances = synced(25010)) {
            ConflictSyncTest.bothChange(instances, root -> ConflictSyncTest.write(root, "same.bin", 21_000, 11), () -> {
                try {
                    var entry = instances.serverService.getFileSyncDatabaseManager().getFsDao().getGenericByPathAndName("/", "same.bin");
                    return entry != null && FileSyncInstances.md5Of(new File(instances.serverRoot, "same.bin")).equals(entry.getContentHash().v());
                } catch (Exception e) {
                    return false;
                }
            }, root -> ConflictSyncTest.write(root, "same.bin", 22_000, 12));
            FileSyncInstances.RecordingServerService server = instances.serverService;
            server.holdCommits = true;
            int commitsBefore = server.commitsReceived.get();
            assertTrue(TwoInstances.waitFor(() -> ConflictSyncTest.answerOpenQuestions(instances, true) > 0, TwoInstances.TIMEOUT_SECONDS), "the client did not ask");
            assertTrue(TwoInstances.waitFor(() -> server.commitsReceived.get() > commitsBefore, TwoInstances.TIMEOUT_SECONDS), "the client did not commit the answer");

            // the server moves on while the commit waits
            ConflictSyncTest.write(instances.serverRoot, "later.bin", 7_000, 31);
            assertTrue(TwoInstances.waitFor(() -> {
                try {
                    return server.getFileSyncDatabaseManager().getFsDao().getGenericByPathAndName("/", "later.bin") != null;
                } catch (Exception e) {
                    return false;
                }
            }, TwoInstances.TIMEOUT_SECONDS), "the server did not index its new file");

            server.releaseCommits();
            boolean[] askedAgain = {false};
            assertTrue(TwoInstances.waitFor(() -> {
                if (openQuestions(instances) > 0)
                    askedAgain[0] = true;
                return ConflictSyncTest.equal(instances) && ConflictSyncTest.fileHas(instances, "same.bin", 22_000) && ConflictSyncTest.fileHas(instances, "later.bin", 7_000);
            }, TwoInstances.TIMEOUT_SECONDS), "the answer was lost or the sync did not go on");
            assertFalse(askedAgain[0], "the client asked again about the same two versions");
            assertTrue(server.commitsReceived.get() >= commitsBefore + 2, "the first commit should have been too old and a second one sent");
            assertTrue(TwoInstances.waitFor(() -> ConflictSyncTest.nothingLeftToMerge(instances), TwoInstances.TIMEOUT_SECONDS), "stage sets are left over");
        }
    }
}
