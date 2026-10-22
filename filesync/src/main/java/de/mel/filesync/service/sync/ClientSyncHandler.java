package de.mel.filesync.service.sync;

import de.mel.Lok;
import de.mel.auth.data.cached.CachedInitializer;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.file.IFile;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.Order;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.auth.tools.lock2.P;
import de.mel.filesync.FileSyncSyncListener;
import de.mel.filesync.data.*;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.data.conflict.StageSetMerger;
import de.mel.filesync.jobs.CommitJob;
import de.mel.filesync.jobs.SyncClientJob;
import de.mel.filesync.quota.OutOfSpaceException;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.sql.*;
import de.mel.filesync.sql.dao.ConflictDao;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.filesync.sql.dao.TransferDao;
import de.mel.filesync.tasks.AvailHashEntry;
import de.mel.filesync.tasks.AvailableHashesContainer;
import de.mel.filesync.tasks.SyncRequest;
import de.mel.sql.ISQLResource;
import de.mel.sql.SqlQueriesException;

import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by xor on 10/27/16.
 */
@SuppressWarnings("Duplicates")
public class ClientSyncHandler extends SyncHandler {

    private final FileSyncClientSettingsDetails clientSettings;
    private final ConflictDao conflictDao;
    private FileSyncSyncListener syncListener;
    private MelFileSyncClientService melDriveService;
    private final Map<String, ConflictSolver> conflictSolverMap = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Questions that were replaced by a newer one (the stage sets are new after each pull). A dialog may still show one of them: what the user answers there counts.
     */
    private final java.util.ArrayDeque<ConflictSolver> supersededSolvers = new java.util.ArrayDeque<>();
    /**
     * what the user decided so far, by what is in conflict (see {@link ConflictSolver#decisions()})
     */
    private final Map<String, Boolean> decisions = new java.util.LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 1000;
        }
    };

    public void updateHashes(Set<String> hashes) {
        FsDao fsDao = fileSyncDatabaseManager.getFsDao();
        StageDao stageDao = fileSyncDatabaseManager.getStageDao();
        TransferDao transferDao = fileSyncDatabaseManager.getTransferDao();
        BunchOfLocks bunchOfLocks = P.confine(fsDao);
        N.forEach(hashes, s -> {
            // if is stage from server or is transfer -> flag as available
            N.forEach(stageDao.getUpdateStageSetsFromServer(), stageSet -> {
                stageDao.devUpdateSyncedByHashSet(stageSet.getId().v(), hashes);
            });
            FileSyncClientSettingsDetails clientSettings = fileSyncSettings.getClientSettings();
            transferDao.updateAvailableByHashSet(clientSettings.getServerCertId(), clientSettings.getServerServiceUuid(), hashes);
        });
        bunchOfLocks.end();
        transferManager.research();
    }

    private boolean debugFlag = true;

    public void askForAvailableTransfers() {
        try {
            if (fileSyncSettings.getClientSettings().getServerCertId() == null)
                return;
            Lok.debug("asking for new available hashes....");
            TransferDao transferDao = fileSyncDatabaseManager.getTransferDao();
            ISQLResource<DbTransferDetails> transfers = transferDao.getNotStartedNotAvailableTransfers(fileSyncSettings.getClientSettings().getServerCertId());
            AvailableHashesContainer availableHashesTask = new AvailableHashesContainer(melDriveService.getCacheDirectory(), CachedInitializer.randomId(), FileSyncSettings.CACHE_LIST_SIZE);
            availableHashesTask.setIntent(FileSyncStrings.INTENT_ASK_HASHES_AVAILABLE);
            N.readSqlResource(transfers, (sqlResource, transfer) -> {
                availableHashesTask.add(new AvailHashEntry(transfer.getHash().v()));
            });
            // uncomment to see some communication happen. but be warned: will cause infinite loop!
//            N.r(() -> {
//                availableHashesTask.add(new AvailHashEntry("51037a4a37730f52c8732586d3aaa316"));
//                availableHashesTask.add(new AvailHashEntry("238810397cd86edae7957bca350098bc"));
//                availableHashesTask.add(new AvailHashEntry("fdcbc1aca23cfebaa128bac31df20969"));
//            });

            if (availableHashesTask.getSize() > 0 && debugFlag) {
                melAuthService.connect(fileSyncSettings.getClientSettings().getServerCertId())
                        .onDone(mvp -> {
                            N.r(() -> {
                                mvp.request(fileSyncSettings.getClientSettings().getServerServiceUuid(), availableHashesTask)
                                        .onDone(result -> N.r(() -> {
                                            debugFlag = false;
                                            Lok.debug("got available hashes.");
                                            AvailableHashesContainer availHashesContainer = (AvailableHashesContainer) result;
                                            availHashesContainer.toDisk();
                                            availHashesContainer.loadFirstCached();
                                            N.forEachIgnorantly(availHashesContainer, hashEntry -> {
                                                transferDao.flagNotStartedHashAvailable(hashEntry.getHash());
                                            });
                                            availHashesContainer.cleanUp();
                                            if (availHashesContainer.getSize() > 0)
                                                transferManager.research();
                                        }));
                                availableHashesTask.cleanUp();
                            });
                        }).onError(result -> {
                            availableHashesTask.cleanUp();
                            Lok.debug("fail");
                        });
            } else {
                availableHashesTask.cleanUp();
            }

        } catch (SqlQueriesException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setSyncListener(FileSyncSyncListener syncListener) {
        this.syncListener = syncListener;
    }


    public ClientSyncHandler(MelAuthService melAuthService, MelFileSyncClientService melDriveService) {
        super(melAuthService, melDriveService);
        this.melDriveService = melDriveService;
        this.clientSettings = melDriveService.getFileSyncSettings().getClientSettings();
        this.conflictDao = melDriveService.getFileSyncDatabaseManager().getConflictDao();
    }

    private ClientSyncHandler(MelAuthService melAuthService, MelFileSyncClientService melDriveService, boolean initFileDist) {
        super(melAuthService, melDriveService, initFileDist);
        this.melDriveService = melDriveService;
        this.clientSettings = melDriveService.getFileSyncSettings().getClientSettings();
        this.conflictDao = melDriveService.getFileSyncDatabaseManager().getConflictDao();
    }

    public static ClientSyncHandler testIntance(MelAuthService melAuthService, MelFileSyncClientService melDriveService) {
        return new ClientSyncHandler(melAuthService, melDriveService, false);
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------
    // The sync session: one at a time, on a thread of its own, see SYNC_SESSION_PLAN.md.
    // Its goal is to commit one version (or, without local changes, to take over the server's). It owns the whole exchange: stage data back and forth
    // with the server and the questions to the user. It waits for both; the service worker does not, it stays free for messages and transfer requests.
    // ------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * a commit that is not answered by then is given up (the request times out earlier: this is the last resort)
     */
    private static final long COMMIT_WAIT_MS = 5 * 60_000L;
    /**
     * a pull that did not end by then is given up (requests time out earlier)
     */
    private static final long PULL_WAIT_MS = 10 * 60_000L;
    private static final long TOO_OLD_RETRY_BASE_MS = 100;
    private static final long TOO_OLD_RETRY_MAX_MS = 5_000;

    private final ExecutorService sessionExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sync session " + melAuthService.getName());
        thread.setDaemon(true);
        return thread;
    });
    private final Object sessionLock = new Object();
    /**
     * somebody asked for a session that has not started yet
     */
    private boolean sessionRequested;
    private boolean sessionRunning;
    /**
     * the service has booted: before, requests are kept
     */
    private boolean booted;
    private boolean stopped;
    private Thread sessionThread;
    private int tooOldInARow;
    private volatile boolean pullFailed;

    /**
     * Asks for a sync session. Returns at once: the session runs now, or after the one that runs, or when the service has booted. Several requests make one session.
     * Also tells a session that waits for the user to look again.
     */
    public void requestSession() {
        synchronized (sessionLock) {
            sessionRequested = true;
            sessionLock.notifyAll();
            startIfDue();
        }
    }

    /**
     * the service has booted: sessions that were asked for may run
     */
    public void onBooted() {
        synchronized (sessionLock) {
            booted = true;
            startIfDue();
        }
    }

    /**
     * holds the session lock
     */
    private void startIfDue() {
        if (booted && !stopped && sessionRequested && !sessionRunning) {
            sessionRunning = true;
            sessionExecutor.execute(this::runSessions);
        }
    }

    private void runSessions() {
        synchronized (sessionLock) {
            sessionThread = Thread.currentThread();
        }
        try {
            while (true) {
                synchronized (sessionLock) {
                    if (!sessionRequested || stopped)
                        return;
                    sessionRequested = false;
                }
                try {
                    runSession();
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    Lok.error("the sync session failed: " + e);
                    e.printStackTrace();
                    melDriveService.onSyncFailed();
                }
            }
        } finally {
            synchronized (sessionLock) {
                sessionRunning = false;
                sessionThread = null;
                Thread.interrupted();
                // a request that came while this was ending
                startIfDue();
            }
        }
    }

    @Override
    public void suspend() {
        super.suspend();
        synchronized (sessionLock) {
            stopped = true;
            sessionLock.notifyAll();
            if (sessionThread != null)
                sessionThread.interrupt();
        }
    }

    @Override
    public void resume() {
        super.resume();
        synchronized (sessionLock) {
            stopped = false;
            startIfDue();
        }
    }

    @Override
    public void onShutDown() {
        suspend();
        sessionExecutor.shutdownNow();
        super.onShutDown();
    }

    private void checkStopped() throws InterruptedException {
        synchronized (sessionLock) {
            if (stopped || Thread.currentThread().isInterrupted())
                throw new InterruptedException("the sync session was stopped");
        }
    }

    /**
     * One session: merge what the indexer found, pull, ask the user if there are conflicts, commit, and apply what the server accepted.
     * Nothing is applied before the server accepted the commit; the local stage sets stay until then, the result is never kept.
     */
    private void runSession() throws Exception {
        // a result left over by a session that did not end (the service stopped in the middle) is not needed: results are made again
        stageDao.deleteStageSetsWithStatus(FileSyncStrings.STAGESET_STATUS_RESULT);
        StageSet local = mergeLocal();
        checkStopped();
        StageSet server = pull();
        if (pullFailed)
            return; // syncFromServerFailed() asks again later
        checkStopped();
        if (local == null) {
            if (server != null)
                apply(server);
            endSession();
            return;
        }
        StageSet toSend;
        long basedOn;
        if (server == null) {
            // only local changes: on top of what we have of the server
            toSend = local;
            basedOn = fileSyncDatabaseManager.getLatestVersion();
        } else {
            ConflictSolver solver = solve(local, server);
            toSend = stageDao.getStageSetById(solver.getMergedStageSet().getId().v());
            basedOn = server.getVersion().v();
            // what the user kept of this side must not be replaced by the other side's version
            for (Conflict conflict : solver.getConflictMap().values())
                if (conflict.getChosenLocal() && conflict.getRemoteStage() != null)
                    stageDao.deleteStageById(conflict.getRemoteStage().getId());
            if (toSend != null) {
                deleteKnownToServer(toSend, server, solver.getMustBeSent());
                stageDao.deleteIdenticalToFs(toSend.getId().v(), solver.getMustBeSent());
            }
        }
        if (toSend == null || !stageDao.stageSetHasContent(toSend.getId().v())) {
            // nothing the server does not have already
            if (server != null)
                apply(server);
            deleteStageSetIfThere(toSend);
            deleteStageSetIfThere(local);
            endSession();
            return;
        }
        checkStopped();
        CommitResult result = commit(toSend, basedOn);
        checkStopped();
        switch (result.outcome) {
            case ACCEPTED:
                tooOldInARow = 0;
                if (server != null)
                    apply(server);
                applyAnswer(toSend, result.answer);
                deleteStageSetIfThere(local);
                endSession();
                break;
            case TOO_OLD:
                // the server moved on meanwhile: the next session pulls again and resolves again (the answers of the user are remembered)
                deleteStageSetIfThere(toSend == local ? null : toSend);
                deleteStageSetIfThere(server);
                long delay = Math.min(TOO_OLD_RETRY_BASE_MS << Math.min(tooOldInARow++, 10), TOO_OLD_RETRY_MAX_MS);
                syncRetryExecutor.schedule(this::requestSession, delay, TimeUnit.MILLISECONDS);
                break;
            case FAILED:
                deleteStageSetIfThere(toSend == local ? null : toSend);
                deleteStageSetIfThere(server);
                melDriveService.onSyncFailed();
                syncFromServerFailed(result.reason);
                break;
        }
    }

    /**
     * @return the one local stage set with content, null if there is none
     */
    private StageSet mergeLocal() throws SqlQueriesException {
        BunchOfLocks warden = P.confine(P.read(fsDao));
        try {
            StageSet local = new LocalStageSetMerger(stageDao).mergeAll(stageDao.getStagedStageSetsFromFS());
            if (local != null && !stageDao.stageSetHasContent(local.getId().v())) {
                stageDao.deleteStageSet(local.getId().v());
                return null;
            }
            return local;
        } finally {
            warden.end();
        }
    }

    /**
     * @return the server's changes since our version, null if there are none or the pull failed (then {@link #pullFailed} is set)
     */
    private StageSet pull() {
        pullFailed = false;
        try {
            syncFromServer();
            List<StageSet> serverSets = stageDao.getUpdateStageSetsFromServer();
            if (serverSets.isEmpty())
                return null;
            // there is only one (one pull at a time): anything else would be left over
            for (int i = 0; i < serverSets.size() - 1; i++)
                stageDao.deleteStageSet(serverSets.get(i).getId().v());
            StageSet server = serverSets.get(serverSets.size() - 1);
            if (!stageDao.stageSetHasContent(server.getId().v())) {
                stageDao.deleteStageSet(server.getId().v());
                return null;
            }
            return server;
        } catch (Exception e) {
            Lok.error("pull failed: " + e);
            pullFailed = true;
            syncFromServerFailed(e);
            return null;
        }
    }

    /**
     * Finds the conflicts between our changes and the server's, asks the user about those that were not answered before and waits for the answers.
     *
     * @return the solver, merged: its merged stage set is what we send (it may be gone when nothing is left of it)
     */
    private ConflictSolver solve(StageSet local, StageSet server) throws SqlQueriesException, InterruptedException {
        ConflictSolver solver = new ConflictSolver(conflictDao, local, server);
        solver.findConflicts();
        // answers to questions that were asked before (about the same versions) count, even if the question was replaced by a newer one since
        harvestDecisions();
        solver.applyDecisions(decisions);
        if (solver.hasConflicts()) {
            Lok.debug("conflicts: asking " + solver.getConflictIdentifier());
            putConflictSolver(solver);
            solver.setSolving(true);
            melDriveService.onConflicts(solver);
            synchronized (sessionLock) {
                while (!solver.isSolved()) {
                    if (stopped)
                        throw new InterruptedException("the sync session was stopped while it waited for the user");
                    // woken by requestSession() (the user answered and the job to go on came), looks again now and then anyway
                    sessionLock.wait(1000);
                }
            }
            harvestDecisions();
        }
        solver.merge();
        StageSet merged = stageDao.getStageSetById(solver.getMergedStageSet().getId().v());
        if (merged != null)
            new BfsToDfsConverter(stageDao).convert(merged);
        return solver;
    }

    /**
     * Removes files from the set that the server has like that already (in its stage set): the user chose the server's version, or both sides did the same.
     */
    private void deleteKnownToServer(StageSet stageSet, StageSet server, Set<Long> keep) throws SqlQueriesException {
        for (Stage stage : stageDao.getStagesByStageSetAsList(stageSet.getId().v())) {
            if (stage.getIsDirectory() || keep.contains(stage.getId()))
                continue;
            Stage known = stageDao.getStageByPathAndName(server.getId().v(), stage.getPath(), stage.getName());
            if (known != null && !known.getIsDirectory() && java.util.Objects.equals(known.getContentHash(), stage.getContentHash())
                    && java.util.Objects.equals(known.getDeleted(), stage.getDeleted()))
                stageDao.deleteStageById(stage.getId());
        }
    }

    /**
     * Takes over the server's changes (the set is gone afterwards). A file that is wanted and lies in the bin (we had it and it was binned in a step before)
     * is taken out right away instead of being fetched.
     */
    private void apply(StageSet server) throws SqlQueriesException, OutOfSpaceException, IOException {
        if (stageDao.getStageSetById(server.getId().v()) == null)
            return;
        BunchOfLocks warden = P.confine(fsDao);
        try {
            commitStage(server.getId().v(), warden, null);
            researchTransfers();
        } catch (OutOfSpaceException e) {
            melDriveService.onInsufficientSpaceAvailable(server.getId().v());
            throw e;
        } finally {
            warden.end();
        }
        // takes the lock of the fs table itself
        wastebin.restoreFsFiles();
        transferManager.research();
    }

    /**
     * The server accepted the set: it gets the fs ids the server gave and is put in place (the set is gone afterwards).
     * It is put in place as of the version the server is at with it: our fs table gets that, so that the next pull does not bring our own commit back as news
     * of the server (that has not got the content yet, so the file would look missing here).
     */
    private void applyAnswer(StageSet sent, CommitAnswer answer) throws SqlQueriesException, OutOfSpaceException {
        BunchOfLocks warden = P.confine(fsDao);
        try {
            if (answer.getNewVersion() != null) {
                sent.setVersion(answer.getNewVersion());
                stageDao.updateStageSet(sent);
            }
            for (Long stageId : answer.getStageIdFsIdMap().keySet()) {
                Long fsId = answer.getStageIdFsIdMap().get(stageId);
                Stage stage = stageDao.getStageById(stageId);
                if (stage == null)
                    continue;
                // the server made a new entry for what we knew by another id (it had deleted it, the user kept our change): the old row is stale
                if (stage.getFsId() != null && !stage.getFsId().equals(fsId))
                    fileSyncDatabaseManager.getFsDao().deleteById(stage.getFsId());
                stage.setFsId(fsId);
                stageDao.update(stage);
            }
            // the parent may have got a new id as well (the server made it new: it had deleted it, the user kept something in it); the old one is gone
            for (Stage stage : stageDao.getStagesByStageSetAsList(sent.getId().v())) {
                Long parentFsId = stage.getParentId() == null ? null : answer.getStageIdFsIdMap().get(stage.getParentId());
                if (parentFsId != null && !parentFsId.equals(stage.getFsParentId())) {
                    stage.setFsParentId(parentFsId);
                    stageDao.update(stage);
                }
            }
            commitStage(sent.getId().v(), warden, null);
            researchTransfers();
            transferManager.research();
        } finally {
            warden.end();
        }
    }

    private void deleteStageSetIfThere(StageSet stageSet) throws SqlQueriesException {
        if (stageSet != null)
            stageDao.deleteStageSet(stageSet.getId().v());
    }

    /**
     * The session is over: the questions it asked are answered and gone (what was decided stays, see {@link #decisions}).
     * If the indexer found something meanwhile, the next session takes it.
     */
    private void endSession() throws SqlQueriesException {
        conflictSolverMap.clear();
        supersededSolvers.clear();
        if (!stageDao.getStagedStageSetsFromFS().isEmpty())
            synchronized (sessionLock) {
                sessionRequested = true;
            }
        melDriveService.onSyncDone();
    }

    private enum Outcome {ACCEPTED, TOO_OLD, FAILED}

    private static class CommitResult {
        final Outcome outcome;
        final CommitAnswer answer;
        final Object reason;

        CommitResult(Outcome outcome, CommitAnswer answer, Object reason) {
            this.outcome = outcome;
            this.answer = answer;
            this.reason = reason;
        }
    }

    /**
     * Sends the set to the server and waits for the answer. The network threads only hand the answer over: it is handled by the session.
     */
    @SuppressWarnings("unchecked")
    private CommitResult commit(StageSet stageSet, long basedOn) throws InterruptedException {
        java.util.concurrent.CompletableFuture<CommitResult> result = new java.util.concurrent.CompletableFuture<>();
        Commit[] sent = {null};
        Promise<MelValidationProcess, Exception> connected;
        try {
            connected = melAuthService.connect(clientSettings.getServerCertId());
        } catch (Exception e) {
            return new CommitResult(Outcome.FAILED, null, e);
        }
        connected.onDone(mvp -> {
            try {
                Commit commit = new Commit(melDriveService.getCacheDirectory(), CachedInitializer.randomId(), FileSyncSettings.CACHE_LIST_SIZE, melDriveService.getUuid());
                sent[0] = commit;
                N.readSqlResource(stageDao.getStagesByStageSetForCommitResource(stageSet.getId().v()), (sqlResource, stage) -> commit.add(stage));
                commit.setBasedOnVersion(basedOn);
                commit.setIntent(FileSyncStrings.INTENT_COMMIT);
                Request committed = mvp.request(clientSettings.getServerServiceUuid(), commit);
                committed.onDone(answer -> result.complete(new CommitResult(Outcome.ACCEPTED, (CommitAnswer) answer, null)))
                        .onError(e -> result.complete(new CommitResult(e instanceof TooOldVersionException ? Outcome.TOO_OLD : Outcome.FAILED, null, e)));
            } catch (Exception e) {
                result.complete(new CommitResult(Outcome.FAILED, null, e));
            }
        }).onError(e -> result.complete(new CommitResult(Outcome.FAILED, null, e)));
        try {
            return result.get(COMMIT_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            return new CommitResult(Outcome.FAILED, null, e);
        } finally {
            if (sent[0] != null)
                N.r(() -> sent[0].cleanUp());
        }
    }

    /**
     * The client found unknown files in places where it expected files of the server when it first indexed. Not reached at the moment:
     * {@link de.mel.filesync.index.InitialIndexConflictHelper#check} is never called, so its stage set is always empty. A session finds such conflicts anyway.
     */
    public void handleInitialConflicts() {
        requestSession();
    }

    private void putConflictSolver(ConflictSolver conflictSolver) {
        // the new question is about the current stage sets: the older ones deprecate
        for (ConflictSolver old : conflictSolverMap.values()) {
            if (old != conflictSolver) {
                supersededSolvers.addLast(old);
                while (supersededSolvers.size() > 20)
                    supersededSolvers.removeFirst();
            }
        }
        conflictSolverMap.clear();
        conflictSolverMap.put(conflictSolver.getConflictIdentifier(), conflictSolver);
    }

    private void harvestDecisions() {
        for (ConflictSolver old : supersededSolvers)
            decisions.putAll(old.decisions());
        for (ConflictSolver current : conflictSolverMap.values())
            decisions.putAll(current.decisions());
    }


    private static final int MAX_SYNC_FAILURES = 10;
    private static final long SYNC_RETRY_BASE_MS = 300;
    private static final long SYNC_RETRY_MAX_MS = 10_000;
    private final AtomicInteger syncFailures = new AtomicInteger();
    private final ScheduledExecutorService syncRetryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sync retry");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * The server could not be asked (no connection, the request failed). The connection can be closed under the request, eg. when it lost against
     * another one to the same partner (duplicate rule), and nobody else would ask again. So ask again a bit later, a few times.
     */
    private void syncFromServerFailed(Object reason) {
        int failures = syncFailures.getAndIncrement();
        if (failures >= MAX_SYNC_FAILURES) {
            Lok.error("giving up to sync from the server after " + failures + " attempts, until it tells us about a new version or we boot: " + reason);
            syncFailures.set(0);
            return;
        }
        long delay = Math.min(SYNC_RETRY_BASE_MS << failures, SYNC_RETRY_MAX_MS);
        Lok.debug("syncing from the server failed (" + reason + "), trying again in " + delay + "ms");
        syncRetryExecutor.schedule(() -> N.r(() -> melDriveService.addJob(new SyncClientJob())), delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Pulls the server's changes since our version into a server stage set (staged when it is complete). Blocks until that is done or failed
     * (then {@link #pullFailed} is set and another attempt is scheduled). Called by the sync session only.
     */
    public void syncFromServer() throws SqlQueriesException, InterruptedException {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        stageDao.deleteServerStageSets();
        Certificate serverCert = melAuthService.getCertificateManager().getTrustedCertificateById(clientSettings.getServerCertId());
        Promise<MelValidationProcess, Exception> connected = melAuthService.connect(serverCert.getId().v());
        connected.onDone(mvp -> {
            boolean handedOn = false;
            try {
                long oldeSyncedVersion = fileSyncDatabaseManager.getFileSyncSettings().getLastSyncedVersion();
                StageSet stageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_SERVER, clientSettings.getServerCertId(), clientSettings.getServerServiceUuid(), null, oldeSyncedVersion);
                SyncRequest sentSyncRequest = new SyncRequest()
                        .setOldVersion(oldeSyncedVersion);
                sentSyncRequest.setIntent(FileSyncStrings.INTENT_SYNC);
                Request<SyncAnswer> request = mvp.request(clientSettings.getServerServiceUuid(), sentSyncRequest);
                request.onDone(syncAnswer -> {
                    boolean staging = false;
                    try {
                        syncFailures.set(0);
                        syncAnswer.setStageSet(stageSet);
                        //server might have got a new version in the meantime and sent us that
                        stageSet.setVersion(syncAnswer.getNewVersion());
                        stageSet.setBasedOnVersion(syncAnswer.getOldVersion());
                        stageDao.updateStageSet(stageSet);
                        Promise<Long, Void> promise = this.sync2Stage(syncAnswer);
                        staging = true;
                        promise.onDone(nil -> {
                            try {
                                if (syncListener != null)
                                    syncListener.onSyncDone();
                            } finally {
                                done.countDown();
                            }
                        }).onError(result -> {
                            // also when there is nothing new
                            try {
                                N.r(() -> stageDao.deleteStageSet(stageSet.getId().v()));
                            } finally {
                                done.countDown();
                            }
                        });
                    } catch (Exception e) {
                        Lok.error("could not stage what the server sent: " + e);
                        pullFailed = true;
                    } finally {
                        if (!staging)
                            done.countDown();
                    }
                }).onError(result -> {
                    pullFailed = true;
                    syncFromServerFailed(result);
                    N.r(() -> stageDao.deleteStageSet(stageSet.getId().v()));
                    done.countDown();
                });
                handedOn = true;
            } catch (Exception e) {
                Lok.error("could not ask the server: " + e);
                pullFailed = true;
            } finally {
                if (!handedOn)
                    done.countDown();
            }
        }).onError(result -> {
            pullFailed = true;
            syncFromServerFailed(result);
            done.countDown();
        });
        if (!done.await(PULL_WAIT_MS, TimeUnit.MILLISECONDS)) {
            Lok.error("the pull did not end in time");
            pullFailed = true;
        }
    }

    private void insertWithParentId(Map<Long, Long> entryIdStageIdMap, GenericFSEntry genericFSEntry, Stage stage) throws SqlQueriesException {
        if (entryIdStageIdMap.containsKey(genericFSEntry.getParentId().v())) {
            stage.setParentId(entryIdStageIdMap.get(genericFSEntry.getParentId().v()));
        }

//        // todo debug
//        if (stage.getNamePair().equalsValue("sub1.txt"))
//            Lok.debug();
////            appendPathAndDepth(genericFSEntry,stage);
//        if (stage.getFsIdPair().notNull()) {
//            FsEntry dp = fsDao.getDepthAndPathAndName(stage.getFsId());
//            if (dp != null) {
//                stage.setDepth(dp.getDepth().v());
//                stage.setPath(dp.getPath().v());
//                // todo debug
//                if (stage.getDepthPair().isNull())
//                    Lok.debug();
//            }
//        }
//        if (stage.getDepthPair().isNull() && stage.getFsParentIdPair().notNull()) {
//            FsEntry dp = fsDao.getDepthAndPathAndName(stage.getFsParentId());
//            if (dp != null) {
//                stage.setDepth(dp.getDepth().v() + 1);
//                stage.setPath(dp.getPath().v() + dp.getName().v() + File.separator);
//                // todo debug
//                if (stage.getDepthPair().isNull())
//                    Lok.debug();
//            }
//        }
//        if (stage.getDepthPair().isNull() && stage.getParentIdPair().notNull()) {
//            Stage dp = stageDao.getDepthAndPathAndName(stage.getParentId());
//            stage.setDepth(dp.getDepth() + 1);
//            stage.setPath(dp.getPath() + dp.getName() + File.separator);
//            // todo debug
//            if (stage.getDepthPair().isNull())
//                Lok.debug();
//        }
//        if (stage.getDepthPair().isNull() && stage.getParentIdPair().notNull()) {
//            Stage dp = stageDao.getDepthAndPathAndName(stage.getParentId());
//            stage.setDepth(dp.getDepth() + 1);
//            stage.setPath(dp.getPath() + dp.getName() + File.separator);
//            // todo debug
//            if (stage.getDepthPair().isNull())
//                Lok.debug();
//        }
//        if (stage.getDepthPair().isNull()){
//            long parentStageId = entryIdStageIdMap.get(stage.)
//        }

        // first try recently added entries
        if (stage.getDepthPair().isNull() && stage.getFsParentIdPair().notNull()) {
            Long parentStageId = entryIdStageIdMap.get(stage.getFsParentId());
            if (parentStageId != null) {
                Stage parent = stageDao.getDepthAndPathAndName(parentStageId);
                stage.setDepth(parent.getDepth() + 1);
                stage.setPath(parent.getPath() + parent.getName() + File.separator);
            } else {
                // if that did not work, try connecting to fs. first via parent
                FsEntry parent = fsDao.getDepthAndPathAndName(stage.getFsParentId());
                stage.setDepth(parent.getDepth().v() + 1);
                stage.setPath(parent.getPath().v() + parent.getName().v() + File.separator);
            }
        }


        // then directly
        if (stage.getDepthPair().isNull() && stage.getFsIdPair().notNull()) {
            FsEntry fsEntry = fsDao.getDepthAndPathAndName(stage.getFsId());
            stage.setDepth(fsEntry.getDepth().v());
            stage.setPath(fsEntry.getPath().v());
        }
        // todo debug
        if (stage.getDepthPair().isNull())
            Lok.debug();
        stageDao.insert(stage);
        entryIdStageIdMap.put(genericFSEntry.getId().v(), stage.getId());
    }

    /**
     * USES FSWRITE
     *
     * @param fsEntry
     * @param stage
     */
    private void appendPathAndDepth(FsEntry fsEntry, Stage stage) throws SqlQueriesException {
        String path = "";
        if (fsEntry.getParentId().notNull()) {
            FsEntry parent = fsDao.getGenericById(fsEntry.getParentId().v());
            path = parent.getPath().v() + parent.getName().v() + File.separator;
        } else if (stage != null) {
            Lok.debug("Error1");
        } else {
            Lok.debug("Error2");
        }
        fsEntry.setPath(path);
    }

    /**
     * FS-delta goes in here.
     * Note: the delta comes ordered by depth, essentially Breadth First Search.
     * All algorithms working with Stages do Depth First Search which is more handy when it comes to hierarchy things like conflict dependencies on folders and files.
     * Therefore a BFS-to-DFS conversion takes place here.
     *
     * @param syncAnswer contains delta
     * @return stageSetId in Promise
     * @throws SqlQueriesException
     */
    private Promise<Long, Void> sync2Stage(SyncAnswer syncAnswer) throws SqlQueriesException, InterruptedException {
        PromiseImpl<Void, Void> communicationDone = PromiseImpl.withoutThrowingOnException();
        PromiseImpl<Long, Void> finished = PromiseImpl.withoutThrowingOnException();
        Map<Long, Long> entryIdStageIdMap = new HashMap<>();
        Order order = new Order();
//        syncAnswer.setCacheDir(melDriveService.getCacheDirectory());
        Iterator<GenericFSEntry> iterator = syncAnswer.iterator();
        if (!iterator.hasNext()) {
            syncAnswer.cleanUp();
            finished.resolveError(null);
            return finished;
        }
        StageSet stageSet = syncAnswer.getStageSet();
        syncAnswer.setStageSetId(stageSet.getId().v());
        // stage first
        while (iterator.hasNext()) {
            GenericFSEntry genericFSEntry = iterator.next();
            Stage stage = GenericFSEntry.generic2Stage(genericFSEntry, stageSet.getId().v());
            stage.setOrder(order.ord());
            // find depth value
            insertWithParentId(entryIdStageIdMap, genericFSEntry, stage);
        }
        syncAnswer.cleanUp();
        // check if something was deleted
        List<Stage> stages = stageDao.getDirectoriesByStageSet(stageSet.getId().v());
        Map<Long, FsDirectory> fsDirIdsToRetrieve = new HashMap<>();
        for (Stage stage : stages) {
            FsDirectory fsDirectory = fsDao.getDirectoryById(stage.getFsId());
            // if dir is not new check if something has been deleted
            if (fsDirectory != null) {
                String oldeHash = fsDirectory.getContentHash().v();
                List<Stage> subStages = stageDao.getSubStagesByFsDirectoryId(fsDirectory.getId().v(), stageSet.getId().v());
                List<GenericFSEntry> subFsEntries = fsDao.getContentByFsDirectory(fsDirectory.getId().v());
                Map<String, GenericFSEntry> fsNameMap = new HashMap<>();
                for (GenericFSEntry gen : subFsEntries) {
                    fsNameMap.put(gen.getName().v(), gen);
                }
                Map<String, Stage> stageNameMap = new HashMap<>();
                for (Stage s : subStages) {
                    stageNameMap.put(s.getName(), s);
                }
                for (GenericFSEntry genSub : subFsEntries) {
                    Stage subStage = stageNameMap.get(genSub.getName().v());
                    if (subStage != null) {
                        stageNameMap.remove(genSub.getName().v());
                        if (!subStage.getDeleted()) {
                            if (subStage.getIsDirectory() && genSub.getIsDirectory().v()) {
                                fsDirectory.addSubDirectory((FsDirectory) genSub.ins());
                            } else if (!subStage.getIsDirectory() && !genSub.getIsDirectory().v()) {
                                fsDirectory.addFile((FsFile) genSub.ins());
                            }
                        }
                    } else {
                        if (genSub.getIsDirectory().v()) {
                            fsDirectory.addSubDirectory((FsDirectory) genSub.ins());
                        } else {

                            fsDirectory.addFile((FsFile) genSub.ins());
                        }
                    }
                }
                for (String key : stageNameMap.keySet()) {
                    Stage newStage = stageNameMap.get(key);
                    if (newStage.getIsDirectory() && !newStage.getDeleted()) {
                        FsDirectory newDir = new FsDirectory();
                        newDir.getName().v(newStage.getName());
                        fsDirectory.addSubDirectory(newDir);
                    } else if (!newStage.getDeleted()) {
                        FsFile newFile = new FsFile();
                        newFile.getName().v(newStage.getName());
                        fsDirectory.addFile(newFile);
                    }
                }
                fsDirectory.calcContentHash();
                String hash = fsDirectory.getContentHash().v();
                if (!hash.equals(stage.getContentHash())) {
                    Lok.debug("ClientSyncHandler.syncFromServer.SOMETHING.DELETED?");
                    fsDirIdsToRetrieve.put(fsDirectory.getId().v(), fsDirectory);
                }
            }
        }
        if (fsDirIdsToRetrieve.size() > 0) {
            Promise<List<FsDirectory>, Exception> promise = melDriveService.requestDirectoriesByIds(fsDirIdsToRetrieve.keySet(), clientSettings.getServerCertId(), clientSettings.getServerServiceUuid());
            promise.onDone(directories -> runner.runTry(() -> {
                // stage everything not in the directories as deleted
                for (FsDirectory directory : directories) {
                    Set<Long> stillExistingIds = new HashSet<>();
                    for (FsFile fsFile : directory.getFiles())
                        stillExistingIds.add(fsFile.getId().v());
                    for (FsDirectory subDir : directory.getSubDirectories())
                        stillExistingIds.add(subDir.getId().v());
                    List<GenericFSEntry> fsDirs = fsDao.getContentByFsDirectory(directory.getId().v());
                    for (GenericFSEntry genSub : fsDirs) {
                        if (!stillExistingIds.contains(genSub.getId().v())) {
                            // genSub shall be deleted
                            Stage stage = GenericFSEntry.generic2Stage(genSub, stageSet.getId().v());
                            stage.setDeleted(true);
                            stage.setOrder(order.ord());
                            stage.setSynced(false);
                            insertWithParentId(entryIdStageIdMap, genSub, stage);
                            recursiveDeleteOnStage(entryIdStageIdMap, order, genSub, stage);
                        }
                    }
                }
                communicationDone.resolveOk(null);
            }));
        } else {
            communicationDone.resolveOk(null);
        }
        communicationDone.onDone(nul -> {
            /**
             * BFS -> DFS
             */
            new BfsToDfsConverter(stageDao).convert(stageSet);
            stageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED);
            N.r(() -> stageDao.updateStageSet(stageSet));
            reorderStageSet(stageSet);
            finished.resolveOk(stageSet.getId().v());
        }).onError(nul -> {
            finished.resolveError(null);
        });
        return finished;
    }

    private void bfsToDfs(StageSet stageSet) {

    }

    private void reorderStageSet(StageSet stageSet) {
        Lok.debug("reordering stageset: " + stageSet);
        try {
            // insert missing parent ids first
            stageDao.repairParentIdsByFsParentIds(stageSet.getId().v());
            // find stuff that is in the wrong order
            UnorderedStagePair unorderedStagePair = stageDao.getUnorderedStagePair(stageSet.getId().v());
            while (unorderedStagePair != null) {
                // swap if found
                Lok.debug("swapping (" + unorderedStagePair.getSmallId().v() + "," + unorderedStagePair.getSmallOrder().v()
                        + ") and (" + unorderedStagePair.getBigId().v() + "," + unorderedStagePair.getBigOrder().v() + ")");
                stageDao.updateOrder(unorderedStagePair.getSmallId().v(), unorderedStagePair.getBigOrder().v());
                stageDao.updateOrder(unorderedStagePair.getBigId().v(), unorderedStagePair.getSmallOrder().v());
                unorderedStagePair = stageDao.getUnorderedStagePair(stageSet.getId().v());
            }
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        }
        Lok.debug("reordering done");
    }

    private void recursiveDeleteOnStage(Map<Long, Long> entryIdStageIdMap, Order order, GenericFSEntry generic, Stage stage) throws SqlQueriesException {
        List<GenericFSEntry> content = fsDao.getContentByFsDirectory(generic.getId().v());
        for (GenericFSEntry subGen : content) {
            Stage subStage = GenericFSEntry.generic2Stage(subGen, stage.getStageSet());
            subStage.setDeleted(true);
            subStage.setOrder(order.ord());
            subStage.setParentId(stage.getId());
            if (!subStage.getIsDirectory())
                subStage.setSynced(false);
            stageDao.insert(subStage);
            entryIdStageIdMap.put(subGen.getId().v(), subStage.getId());
            if (subGen.getIsDirectory().v()) {
                recursiveDeleteOnStage(entryIdStageIdMap, order, subGen, subStage);
            }
        }
    }

    public void onStageSetsMerged(Long lStageSetId, Long rStageSetId, StageSet mergedStageSet) {
        for (ConflictSolver solver : conflictSolverMap.values()) {
//            solver.checkObsolete(lStageSetId, rStageSetId);
        }
    }

    public Map<String, ConflictSolver> getConflictSolverMap() {
        return conflictSolverMap;
    }
}
