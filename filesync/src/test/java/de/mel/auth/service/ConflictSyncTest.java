package de.mel.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.jobs.CommitJob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both sides change the same thing while they are apart: the client finds out when it syncs and asks (here: the test, usually the user) which side wins.
 * Whatever is decided, both directories must be the same afterwards and nothing may be lost that was not decided against.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class ConflictSyncTest {
    @TempDir
    File dir;

    private FileSyncInstances synced(int port) throws Exception {
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "same.bin", 20_000, 1);
        FileSyncInstances.writeFile(new File(dir, "serverRoot"), "dir/other.bin", 30_000, 2);
        FileSyncInstances instances = new FileSyncInstances(dir, port);
        try {
            instances.createServer();
            instances.createClient();
            assertTrue(TwoInstances.waitFor(() -> equal(instances), 60), "the first sync did not finish");
            return instances;
        } catch (Throwable t) {
            instances.close();
            throw t;
        }
    }

    /**
     * the stage sets of the sync are used up: nothing is left that the client would commit or merge again
     */
    private static boolean nothingLeftToMerge(FileSyncInstances instances) {
        try {
            var stageDao = instances.clientService.getFileSyncDatabaseManager().getStageDao();
            return stageDao.getStagedStageSetsFromFS().isEmpty() && stageDao.getUpdateStageSetsFromServer().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean serverKnowsItsFile(FileSyncInstances instances) {
        try {
            var entry = instances.serverService.getFileSyncDatabaseManager().getFsDao().getGenericByPathAndName("/", "same.bin");
            return entry != null && FileSyncInstances.md5Of(new File(instances.serverRoot, "same.bin")).equals(entry.getContentHash().v());
        } catch (Exception e) {
            return false;
        }
    }

    private static String sizes(File root) {
        StringBuilder sizes = new StringBuilder();
        for (String path : new String[]{"same.bin", "dir/other.bin"})
            sizes.append(path).append("=").append(new File(root, path).length()).append(" ");
        return sizes.toString();
    }

    private static boolean equal(FileSyncInstances instances) {
        try {
            return FileSyncInstances.hashesBelow(instances.serverRoot).equals(FileSyncInstances.hashesBelow(instances.clientRoot));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * the server changes same.bin while it is down and indexes it, then the client changes its files while it is down
     */
    private static void bothChange(FileSyncInstances instances, Consumer<File> onServer, Consumer<File> onClient) throws Exception {
        instances.restartServer(() -> onServer.accept(instances.serverRoot));
        // the server's boot index finds the change a bit after the service is up. The client must not commit before the server has it, else there is nothing to collide
        assertTrue(TwoInstances.waitFor(() -> serverKnowsItsFile(instances), 60), "the server did not index its change");
        instances.restartClient(() -> onClient.accept(instances.clientRoot));
    }

    private static void write(File root, String path, long size, long seed) {
        try {
            FileSyncInstances.writeFile(root, path, size, seed);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Like a user who answers each question that pops up: the client may replace a question by a newer one about the same conflict (its stage sets are new after each
     * pull), so the answer goes to whatever is open now, and the sync is told to go on each time something was answered.
     *
     * @return the number of questions answered
     */
    private static int answerOpenQuestions(FileSyncInstances instances, boolean local) {
        int answered = 0;
        for (ConflictSolver solver : new ArrayList<>(instances.clientService.getConflictSolverMap().values())) {
            if (!solver.isSolving() || solver.isSolved() || solver.getConflictMap().isEmpty())
                continue;
            for (Conflict conflict : new ArrayList<>(solver.getConflictMap().values())) {
                if (local)
                    conflict.decideLocal();
                else
                    conflict.decideRemote();
            }
            answered++;
        }
        if (answered > 0)
            instances.clientService.addJob(new CommitJob());
        return answered;
    }

    /**
     * @return the number of questions that had to be answered until both directories were the same
     */
    private static int resolveUntilEqual(FileSyncInstances instances, boolean local) throws Exception {
        AtomicInteger questions = new AtomicInteger();
        boolean equal = TwoInstances.waitFor(() -> {
            questions.addAndGet(answerOpenQuestions(instances, local));
            return equal(instances);
        }, 60);
        assertTrue(questions.get() > 0, "the client never asked");
        assertTrue(equal, "the directories are not the same after the decision (" + questions.get() + " questions answered)");
        return questions.get();
    }

    @Test
    public void sameFileChangedOnBothSidesTheServersVersionWins() throws Exception {
        try (FileSyncInstances instances = synced(24600)) {
            bothChange(instances, root -> write(root, "same.bin", 21_000, 11), root -> write(root, "same.bin", 22_000, 12));
            resolveUntilEqual(instances, false);
            assertEquals(FileSyncInstances.md5Of(new File(instances.serverRoot, "same.bin")), FileSyncInstances.md5Of(new File(instances.clientRoot, "same.bin")));
            assertEquals(21_000, new File(instances.clientRoot, "same.bin").length(), "the client should have the server's version");
        }
    }

    @Test
    public void sameFileChangedOnBothSidesTheClientsVersionWins() throws Exception {
        try (FileSyncInstances instances = synced(24610)) {
            bothChange(instances, root -> write(root, "same.bin", 21_000, 11), root -> write(root, "same.bin", 22_000, 12));
            resolveUntilEqual(instances, true);
            assertEquals(22_000, new File(instances.serverRoot, "same.bin").length(), "the server should have the client's version. server: " + FileSyncInstances.hashesBelow(instances.serverRoot).keySet() + " client: " + FileSyncInstances.hashesBelow(instances.clientRoot).keySet());
            assertEquals(22_000, new File(instances.clientRoot, "same.bin").length());
        }
    }

    /**
     * different files are no conflict: both changes meet
     */
    @Test
    public void differentFilesChangedOnBothSidesAreMerged() throws Exception {
        try (FileSyncInstances instances = synced(24620)) {
            bothChange(instances, root -> write(root, "same.bin", 21_000, 11), root -> write(root, "dir/other.bin", 31_000, 12));
            assertTrue(TwoInstances.waitFor(() -> equal(instances), 30), "the changes did not meet. server: " + sizes(instances.serverRoot) + " client: " + sizes(instances.clientRoot));
            assertEquals(21_000, new File(instances.clientRoot, "same.bin").length());
            assertEquals(31_000, new File(instances.serverRoot, "dir/other.bin").length());
            assertTrue(instances.clientService.getConflictSolverMap().isEmpty(), "different files are no conflict");
            assertTrue(TwoInstances.waitFor(() -> nothingLeftToMerge(instances), 30), "the client keeps stage sets it has merged already");
        }
    }
}
