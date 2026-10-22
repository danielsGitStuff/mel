# The seven disabled conflict tests: what is known, what is guessed, what could be done

> **Solved (2026-10-22).** The client syncs in sessions, one at a time (`SYNC_SESSION_PLAN.md`); the seven tests run again and pass in cold cycles. What was done, step by step, is in `REWORK_LOG.md` (from "Wastebin: a binned file is always found again" on). This document is the analysis as it was before; two of its assumptions were wrong: the pull did not block either (the `WaitLock` returned at once), and `holdBackCommit` holds back the server's own index, not its answers to commits (`RecordingServerService.holdCommits` does that now).

Written 2026-10-22 on branch `rework2026` (commit `0bd7d222`). Everything marked **seen** was observed in a log, a trace or a test result. Everything marked **guess** is reasoning from the code and has not been shown.

## 1. The tests

Disabled with `@Disabled("intermittent after a cold start ...")`:

| Test | Class | Cold runs failed (of 6 without any logging) |
|---|---|---|
| `directoryWhereBothAddedFilesAndChangedOneTheClientsVersionWins` | ConflictSyncTest | 5 |
| `anAnswerSurvivesThatTheClientChangesAgainBeforeTheSyncGoesOn` | ConflictSyncTest | 2 |
| `sameFileChangedOnBothSidesTheClientsVersionWins` | ConflictSyncTest | 2 |
| `clientDeletesWhileServerChangesTheDeletionWins` | ConflictDeleteTest | 1 (a 5 minute hang, before the one minute cap) |
| `serverDeletesDirectoryWhileClientChangesAFileInItTheChangeWins` | ConflictDeleteTest | 1 |
| `serverDeletesDirectoryWhileClientChangesAFileInItTheDeletionWins` | ConflictDeleteTest | 1 |
| `serverDeletesWhileClientChangesTheChangeWins` | ConflictDeleteTest | failed in the full build once, and in several earlier cold runs |

"Cold" means: the first run after a source file changed, so Gradle compiled and then ran the tests in a fresh JVM. **Seen:** run again and again without a recompile, the same tests passed about 30 of 30 times, and the two directory tests passed 5 of 5 cold when run alone. They only fail in a class run right after a recompile.

The tests are right as far as I can tell: the failing runs end in states that are wrong (a file gone on one side and the other side holding the rejected version, or the two sides never agreeing), not in states that are merely slow. The failures cluster on the *client wins* and *client changes again* paths, which are the paths where the client sends a merged result to the server.

## 2. What the failing runs have in common (seen)

An in-memory trace of one failing run (no I/O, because logging hides the bug):

```
handleConflict local 7 remote 8     -> solved by a carried-over answer, merge 7/8 -> merged set 11
commit remote 8
send commit of 11 basedOn 2         <- the merged set goes to the server
handleConflict local 11 remote 9    <- a second round starts on the SAME merged set 11 (against a second server stage set, r9)
solver l11//r9 conflicts=3 solved=false
send commit of 11 basedOn 2         <- the same set is sent a second time
commit answered 11                  <- (callback thread) first send accepted, server at 3
commitStage 11                      <- (callback thread) the client applies the answer
too old, server at 3                <- second send rejected
commitStage 12 ...
```

Also **seen** in the cold runs:
- At client start the same stage set (`send commit of 7 basedOn 1`) is sent **three times at once**. Three triggers fire together: the end of the boot index, the authenticated connection, and the spotted certificate. All three get `TooOldVersion`.
- Two pulls produce two server stage sets (r8 and r9) that exist side by side.
- A read of the merged stage set at the start of `ConflictSolver.after()` (a debug dump), a 50 ms `Thread.sleep` there, or a few log lines in the sync steps each made every cold cycle pass (13 of 13, against 1 of 6 without). That is the signature of a race with a narrow window, not of a wrong result of some computation.

## 3. Probable causes

1. **The commit does not block the worker, the pull does (guess, strongly supported by the code).**
   `syncFromServer` waits on a `WaitLock` until its answer arrived. `syncToServerLocked` returns right after the request is sent. The answer is handled later by a promise callback on a network thread (`commit answered 11` and the following `commitStage 11` ran on a thread named `-> localhost:...`, not on the service worker). So the worker thread takes the next job while the commit is in flight.
2. **The set that is in flight still looks like work (seen).** After the merge `ConflictSolver.after()` sets the merged set to source FS and status STAGED. That is exactly what `execCommitJob` looks for (`getStagedStageSetsFromFS`). While the answer is on its way, the next `CommitJob` finds the same set and either sends it again or resolves it against another server stage set.
3. **Nothing says "one pull at a time" (seen, partly guess).** Triggers add jobs from everywhere: `SyncClientJob` from `onSyncReceived`, from the propagate-new-version message, from a spotted certificate, from `TooOldVersion`, from the retry timer; `CommitJob` from the end of the index, from an authenticated connection, from the GUI and from `execCommitJob` itself. `SyncClientJob` is thinned out by a counter (`syncJobCount`), `CommitJob` is not. `syncFromServer` starts by deleting the server stage sets, so two overlapping pulls can leave a second set behind (r8 and r9 in the trace).
4. **Callbacks change the file system tables concurrently (guess).** The answer handler runs `commitStage(...)` on the callback thread while the worker may be inside `commitStage(remote)` of the next round. They are protected by `BunchOfLocks`, but only per DAO and not as one transaction of the sync cycle.
5. **Jobs are dropped before boot is complete (seen, effect unknown).** `CommitJob turnend down. required boot level not reached yet.` appears on every start. If the only trigger that would have run is dropped, the client waits for the next event. This probably causes the 5 minute hangs seen once or twice (a **guess**: the hang itself was never caught in a log).
6. **Why only after a recompile (guess).** The first run is slower (class loading, cold caches). The three start triggers and the first pull then overlap more.

What I could **not** show: that the hangs have the same cause, and which of 1 to 4 matters most. The trace points at 1 and 2 together.

## 4. Solutions worth considering

### A. Make the commit block like the pull (smallest change)
`syncToServerLocked` waits (same `WaitLock` pattern as `syncFromServer`) until the answer was handled or the request failed.
- Fixes cause 1 for the worker thread. The next job cannot start while a commit is in flight.
- Cost: the worker is busy while the network answers. A commit to a server that is gone must time out (the request has a timeout; check what it is).
- Risk: a deadlock if the answer handler needs something the worker holds (`bunchOfLocks`). The answer handler uses the same `bunchOfLocks` that the caller opened, so the call has to be restructured, not only wrapped.
- Does not remove the three start triggers or the two pulls.

### B. Mark a set as "in flight"
Give the stage set a status (`COMMITTING`) while it is sent. `execCommitJob` and `handleConflict` skip such sets. The answer handler deletes the set; an error (`TooOldVersion`, no connection) sets it back to STAGED. On service start every `COMMITTING` set goes back to STAGED (a crash during a commit must not lose it).
- Fixes causes 2 and the duplicate send directly, also if the worker does continue.
- Needs a new status value and a migration of the start-up code, but the table already has a status column.
- Works together with A.

### C. One state machine for the whole cycle
Replace "jobs that call each other" by one state in `ClientSyncHandler`: IDLE, PULLING, ASKING (waits for the user), COMMITTING. Triggers only set a "again" flag. When the cycle leaves a state it looks at the flag. The answer of the server is posted as a job (`CommitAnsweredJob`) to the worker, so *nothing* is done on callback threads.
- Fixes causes 1 to 4 at once and is easy to reason about and to test (the states are the log).
- Biggest change: touches `execCommitJob`, `syncFromServer`, `syncToServerLocked`, the TooOld handling and every `addJob(new ...)`. This is where the long-term quality is; it is also where a regression is most likely. It needs the conflict tests (re-enabled) as its safety net.

### D. Make the server idempotent for a repeated commit
Give each commit an id (the `Commit` already has a random cache id). The server remembers the last N ids with their answers and answers a repeat with the same answer. A duplicate send then does no harm.
- Helps with duplicates from any source, also network retries.
- Does not stop the client from starting a second *round* (cause 2). It hides the symptom for the commit, not for the resolve.
- Small, local to `ServerSyncHandler`.

### E. Fewer triggers
Count `CommitJob` like `SyncClientJob` (one waiting job is enough), and let the three start triggers become one. Reduces the pressure on the race. Not a fix on its own: the race stays, only rarer. Useful in addition to B or C.

### F. Loop until accepted instead of separate retries
After `TooOldVersion` do not queue a `SyncClientJob` and a `CommitJob` separately. The cycle itself goes pull, resolve, commit again (bounded number of tries, with a back-off). This replaces the version guess in the `TooOldVersion` handler (which was wrong once already: it counted our own merged set). Natural part of C.

### Not worth it
- Sleeps or delays to close the window. They worked as a diagnosis (13 of 13), but they only move the window.
- Disabling more tests or giving them retries. They found real bugs; a retry would hide the next one.

### My recommendation
B first (the in-flight status): it is local, it removes the duplicate send and the second round on the same set, it survives a crash, and it is testable. Then A (block on the commit) if the trace still shows an overlap. C is the right target if you want the sync cycle to stay maintainable, but I would do it as its own step after B, with the seven tests running again.

## 5. How to test it deterministically (instead of by cold runs)

- `FileSyncInstances.RecordingServerService` already has `holdBackCommit` (used before in the transfer tests): the server holds back the answer to a commit. Use it to *make* the window long: answer a conflict, hold back the commit answer, trigger another `CommitJob` and `SyncClientJob`, and assert that the client sends nothing new and starts no second round; then release and assert the end state. That turns the flake into a test that fails every time without the fix.
- To confirm any change against the real flake: touch a source file (forces a recompile), run the two classes, repeat. The loop script I used is in the scratchpad (`cold.sh`, `/tmp/claude-1000/-project/83bdb939-ae78-4120-bd73-641c528d59fe/scratchpad/cold.sh`). Six cycles without any extra logging are the minimum: the plain client-wins case failed 2 of 6, the directory case 5 of 6.
- Do not add logging to look: use an in-memory event list and print it from the failing assertion (as done for the trace above), or compare cycles with and without the line.

## 6. After the fix

Re-enable the seven tests (remove the `@Disabled` lines in `ConflictSyncTest` and `ConflictDeleteTest`), run at least six cold cycles, and run the full `./gradlew build` once.
