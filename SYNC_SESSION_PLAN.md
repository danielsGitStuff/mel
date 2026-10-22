# Sync sessions: the plan for the client sync race

Written 2026-10-22 on branch `rework2026`, agreed in discussion with the author. It replaces the solutions in `CLIENT_SYNC_RACE.md` section 4 (the analysis there still holds, with the corrections in section 1 below).

## 1. What was found in addition to CLIENT_SYNC_RACE.md

- **The pull does not block either (seen).** `WaitLock` is a `Semaphore(1)`. `syncFromServer` calls `unlock()` right after `deleteServerStageSets()`, so `waitLock.lock().lock()` returns before the server has answered. Neither pull nor commit holds the worker; three pulls ran at the same time in a trace (server stage sets 8, 9 and 10).
- **How the file got lost on both sides (seen in an in-memory trace):** merged set 13 is sent; a second round takes 13 as new work, merges it into 14 and deletes it; the server accepts 13 (version 3), the answer finds the set gone, the client stays at version 2; the next pull brings the client's own file back as "changed on the server, not here yet"; `SyncHandler.commitStage` (around line 385) bins the local file without comparing hashes; the server waits for the upload of a file the client just binned.
- **Tests that are not disabled fail too** in cold runs: `differentFilesChangedOnBothSidesAreMerged`, `directoryWhereBothAddedFilesAndChangedOneTheServersVersionWins`, `anAnswerSurvivesThatTheServerChangesAgainBeforeTheSyncGoesOn`. `ConflictSyncTest` failed 3 of 6 cold cycles twice.
- **Cold cycles need a content change**: `touch` alone lets Gradle skip the compile. The task is `:filesync:integrationTest`.

## 2. The constraint

On the client, incoming requests and messages (also `INTENT_PLEASE_TRANSFER`, `INTENT_HASH_AVAILABLE`, new version) run on the service worker. File transfers run in `TManager` and the isolated file process. **Neither may be blocked by the sync.** So the sync gets a thread of its own, and only that thread waits.

## 3. The session (client)

A session has one goal: **commit one version** (or, without local changes, take over the server's version). Only one session exists at a time. It runs on its own thread and owns the whole exchange: stage data back and forth with the server, and asking the user about conflicts. It may wait for the server and for the user.

```
session:
  1. merge all waiting local stage sets (STAGED, from the file system) into ONE: the input, fixed for this session
  2. pull the server's changes into ONE server stage set
  3. find conflicts; apply remembered decisions (rule in 3.2)
     open questions left -> ask the user, wait for the answer
  4. resolve -> result set
  5. commit the result, wait for the answer (on this thread, not on the network callback)
       accepted -> apply locally, delete the input and the result
       TooOld   -> throw the result away, keep the input
       no input -> nothing to commit: apply the server stage set, done
  6. waiting local stage sets, or a request that arrived meanwhile, or TooOld -> set up the next session to run after this one
```

### 3.1 Rules

- **The input stays until the commit was accepted; results are never kept.** A result can always be made again from the input and the decisions. So the only local stage sets that ever exist are the ones the indexer made, and their `created` order is the real order of the edits: the merge (newer wins, pairs found by path) stays correct without ordering tricks.
- **The input is fixed.** Changes made during the session (also while a dialog is open) wait for the next session.
- **Triggers only ask for a session** (new version propagated, indexer done, connection authenticated, certificate spotted, user answered, retry timer). If a session runs, the request is noted and the next session starts after it. Several requests make one next session.
- **The server stage set belongs to the session.** Only the session pulls, only the session deletes server stage sets.
- **The answer of the server is handled on the session thread.** The network callback only hands the result over. Nothing of the sync runs on callback threads.
- **TooOld ends the session**; the next one starts with a back-off (bounded, like `syncFromServerFailed`), so a busy server does not cause a hot loop.
- **Shutdown and stop** interrupt the session. Decisions that were not sent are lost; that is accepted. The stage sets in the database are the state a new session starts from.
- **Boot level**: requests that arrive before boot is complete are kept and run then, not dropped (the dropping is a suspect for the rare 5 minute hangs).

### 3.2 Decisions that carry over

A decision carries over to a later session **only for the same versions on both sides**: path, name, content hash and deleted flag of the local and of the remote side are equal (`ConflictSolver.decisionKey`, as now). For a directory the content hash is made of the names below it, so a directory decision only carries over while neither side added, removed or renamed something in it. If the user changed the file again meanwhile, they are asked again. This rule gets a test of its own.

### 3.3 Asking the user

The session waits on "the open questions of this solver are answered". The GUI (filesyncfx, Android) and the tests now queue a `CommitJob` after answering; at first that `CommitJob` is the signal for the waiting session, so the GUI stays unchanged. Check the callers before changing it.

## 4. Merging local stage sets

- Only as step 1 of a session, nowhere else.
- Fold pairwise until one set is left (now: two at a time, `TOO MANY!!!1` and another `CommitJob` for more).
- Tested now by 3 small unit tests (`SingleServiceTest`); `MergeTest.mergeLocalStageSets` is empty. **Write tests first** for what the plan relies on: more than two sets; a directory deleted in the newer set with changes in it in the older one; a file changed in both (the newer edit wins); a file deleted in the newer set; a new directory with content in the newer set. Fix the merge where they fail.

## 5. The server: same mechanics

Today the server changes its version on two threads: its own stage sets are committed on the indexer thread (`MelFileSyncServerService` lines 163 and 191), client commits on the service worker (line 99, `handleCommit`). Both take the `fsDao` lock, so it may be safe in practice; it was not seen failing. Same mechanics as on the client: **one version worker** on the server, sessions run one after another:
- from its own changes: merge its waiting local stage sets, apply, new version, propagate;
- from a client commit: version check (TooOld otherwise), apply, new version, answer, propagate.
The server never asks anyone, so its sessions are the short form.

## 6. What goes away

- `WaitLock` in `syncFromServer`, and the jobs that call each other: `execCommitJob`, `syncFromServer`, `syncToServerLocked` become steps of the session.
- The version guess in the TooOld handler (replaced by "next session").
- `mergeStageSets` at the start of `execCommitJob` (moves into step 1).
- Turning a merged result into a stage set "from the file system" (`ConflictSolver.after()` sets source FS / STAGED): results are not kept as input any more.

## 7. Tests

1. Merge tests (section 4).
2. Decision carry-over: same versions carry over, a changed file or a changed directory content is asked again.
3. Deterministic session test with `RecordingServerService.holdBackCommit`: while the commit answer is held back, messages are still handled, no second pull and no second commit happen, a change on the client waits; after the release the end state is right and the next session commits the waiting change.
4. TooOld during a session: the input survives, the next session resolves again with the remembered decision.
5. Re-enable the seven disabled tests. Cold cycles (content change, `:filesync:integrationTest`, 6 or more, no extra logging), then one full `./gradlew build`.

## 8. Order of work

0. ~~Shorter waits in the integration tests (7 min 44 s to 5 min 14 s), timers settable for tests.~~ Done, commit `58fc0508`.
1. ~~Merge tests, fix the merge.~~ Done, commit `784f8afe`.
2. ~~Wastebin (section 9): tests, one lock, entries from the disk, restore updates the fs entry.~~ Done.
3. ~~Client session worker; the old job paths go; look in the bin first when applying.~~ Done (and the transfer fix it needed, `6d3ae12c`).
4. ~~Tests 2 to 4, re-enable the seven, cold cycles.~~ Done (plus the transfer, auth and version fixes they needed).
5. ~~Server version worker.~~ Done as far as it makes sense: client commits on a thread of their own; the version paths already take turns under the fs table's lock (REWORK_LOG.md).
6. ~~Full build; update `REWORK_LOG.md`, mark `CLIENT_SYNC_RACE.md` as solved.~~ Done: full build of `d0a2ce7b` green, 466 tests, none skipped.

## 9. The wastebin

Decided: binning a file that is needed again is fine, the file is taken back out of the bin when it is wanted. But bin and restore must be synchronized so that no race breaks them.

Seen in cold-run traces (2026-10-22), with the client's own file:

```
sync thread  bin same.bin                         (commitStage of a server set: the client's own file, as "not here yet")
TManager     restoreFsFiles: same.bin back        (correct)
sync thread  bin same.bin again                   (commitStage of a duplicate server set from a parallel pull)
             -> stored as wastebin/<hash>.null
server asks for the hash: findFileByHash finds nothing, four times, until the test gives up
```

Defects behind it:
1. **A bin entry without an id.** `WasteDao.fsToWaste` fills the entry from the fs table (inode, modified, size) and swallows a failed insert (`waste.inode` is `NOT NULL UNIQUE`). `Wastebin.deleteFsFile` then moves the file to `<hash>.null`; `getByHash`, `restoreFsFiles` and `findFileByHash` look for `<hash>.<id>` and never find it. The file is in the bin directory, but lost to the program.
2. **A restore does not update the fs entry from the disk.** `restoreFsFiles` sets `synced` and moves the file (`moveBlocking` ignores the fs id); inode and modified stay what the server's stage said. The next decision about that file (`asInTable`, bin entry by inode) works on wrong values. `FileDistributor.updateFs` does the right thing and is not used here.
3. **Bin and restore run on two threads** (sync thread inside `commitStage`, `TManager.placeKnownFiles`) and nothing makes "move the file + write the row" one step. The double binning above came from a duplicate server set, which the sessions remove; the bin has to be safe on its own anyway.
4. **`FileDistributor.moveFile` ignores the result of `renameTo`**: a failed move goes unnoticed.
5. Not explained yet: in one earlier run the client's file was gone with no bin entry at all (bin directory held only `deferred`); that run had no rename trace yet. The answer of a commit was applied on the callback thread while the sync thread worked on the same set: the sessions remove that, but it should be checked with the rename trace once more.

**Done 2026-10-22, commit `605dadc4`:** points 1 to 4 below are fixed in `Wastebin` and `FileDistributor.moveBlocking`, with `WastebinTest` (5 tests; 4 failed before the fix, the concurrent one fails 3 of 3 without the lock in `restoreFsFiles`). Full `:filesync:test` (74) and `:filesync:integrationTest` (68, 6 skipped) green. `ConflictSyncTest` passed 6 of 6 cold cycles afterwards (before: 3 of 6 failed, twice). The race itself is still there (duplicate sends, parallel pulls in the trace): the bin no longer turns it into a lost file. "Look in the bin first when applying" is left for the session work.

What the bin needs:
- **One lock for the bin**: bin, restore and `rm` each are one step "move the file + write the row" under it.
- **Bin entries from the disk**, never from fs table values (inode, modified, size, hash of what is there), and **fail loudly**: no swallowed insert, check `renameTo`.
- **A restore updates the fs entry from the disk** (as `updateFs` does).
- **Look in the bin first when applying**: when the session applies a server set and the bin has the hash, the file is restored right there, instead of creating a transfer and waiting for `TManager`. `TManager` keeps its restore for files that arrive later, under the same lock.
- Tests: bin + restore + bin of the same file; a bin entry whose fs values are stale; a restore that is read back by `findFileByHash`.

## 10. Open

- Back-off values and the number of TooOld tries before a session waits for the next trigger.
- A temporary in-memory trace (`SyncTrace`) is used while the work goes on; it is never committed.
