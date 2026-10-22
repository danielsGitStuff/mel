# Rework log (unattended run, branch rework2026)

## Final summary (2026-10-21)

**Definition of done (REWORK_RUNBOOK.md): reached.**
1. Sockets reworked: all 10 steps of `SOCKET_REWORK_PLAN.md` section 4 are ticked, no `@Disabled` test is left (`grep -rn "@Disabled" auth/src/test` prints nothing).
2. No JDeferred: `grep -rn "org.jdeferred" --include=*.java --include=*.kt --exclude-dir=build .` prints nothing; `api libs.jdeferred` and `jdeferred` in the version catalog are gone.
3. `./gradlew build` is green (all modules, unit + integration tests, Android assembles/lint), last full run 5 min.
4. Everything is committed on `rework2026`, nothing pushed, no other branch touched, `fun.with` and `~/tools` unchanged.

**Decisions taken alone ("needs the user" first):**
- needs the user: none that contradicts a decision of the user.
- `suspend()`/`resume()` of `MelAuthService` stay. The plan/brief say "no callers", but `AndroidPowerManager` calls both (power saving). Their test stays; the services and the worker get `restart()`.
- A message above 1 MiB is not sent (logged) instead of the old corrupting chunking; file blocks are 1 MiB now (were 4 MiB). If bigger service messages exist they have to use the cached-data mechanism.
- A duplicate connection that loses is told `ProtocolError(DUPLICATE)` and closed on both roles; a connect job that lost gets the winner as soon as it is registered (`whenConnected`, 10 s).
- `FileTransferDetail.readFile` was fixed (it skipped the offset again on every call: every file above one block was corrupt); `sendingNotification.cancel()` null check.
- `DeferredRunnable.run()` no longer resets `stopped`; `restart()` is explicit.
- Runbook deviation: no `PromiseAdapters` (the rewritten socket code was small and was ported in P4 anyway).
- Android: new string `coupleNotificationCertTextKnown` (en/de) and the FX dialog label for "partner already known".

**Remaining risks / not done:**
- Android was only compiled/assembled/linted, nothing was run on a device (foreground service, TLS client certificates on Android with the new partner-from-TLS check, the register dialog with the known-partner text).
- `ConnectWorker`/`MelRegisterProcess` etc. block a thread per connect job; unchanged in spirit.
- Boot failures still never reach the caller of `MelBoot.boot()` (problem 7 of the brief, pinned by `BootFailureTest`), not part of this run.
- Certificates still travel unencrypted at pairing (users compare nothing); a fingerprint comparison string is still a later idea.
- No keep-alive in ESTABLISHED (decided): a half open connection is only noticed when the next send fails or the partner reconnects (the "same initiator, newer wins" rule then replaces it).
- Timing: the integration tests were run 5 times in a row after steps 2/3, 6, 7, 8, 9 and after P4; earlier flaky tests were test races, each explained in its step.

**Follow-up after the run (2026-10-21): file transfer, resume, tests** - see the last section of this file.

**Verify:**
```
. ~/tools/env.sh
./gradlew build                               # about 5 min
./gradlew :auth:test :auth:integrationTest    # about 3.5 min
grep -rn "org.jdeferred" --include=*.java --include=*.kt --exclude-dir=build .
git log --oneline 05afb263..HEAD
```

Started 2026-10-21. Driven by `REWORK_RUNBOOK.md`. One entry per step. Final summary goes to the top at the end.

## Decisions taken alone ("needs the user" first)

(none yet)

## Step 1: independent bugs

Done 2026-10-21 (first hours). Changes: `DeferredRunnable.stopped` volatile, `run()` does not run a runnable that was stopped
before it started, explicit `restart()` (used by `startUpCommunications`, `Indexer.resume`); request timeouts: one daemon
`ScheduledExecutorService` per `MelAuthService`, `MelRequest.startTimeout(service)` only when sent, timeout configurable
(`setRequestTimeoutMs`, default 30 s); `WatchDogTimer` gone from `MelRequest`; `getAllowedServices` without connection returns a
rejected `Request`; `sockets` is a concurrent set, every `MelSocket` removes itself on close; `ConnectedEnvironment` keeps the connect
job in its "connecting" maps (no placeholder socket), `removeValidationProcess` removes by identity; `BinarySocket.BUFFER_SIZE`
(updater + miniserver) instead of `MelSocket.BLOCK_SIZE`.
Tests: `DeferredRunnableTest`, `MelRequestTest` (unit), `InstanceBookkeepingTest` (integration).

## Step 2: close is an event, exactly once

`SocketWorker` is now the single consumer of the socket's event queue (`SocketEvent`: message, block, isolate confirmed, peer closed,
close requested). The reader never calls listeners. `MelSocket.close(CloseReason)` enqueues; the worker closes the java socket and
calls `onClose(reason, remote)` once (`finishClose` is guarded by a CAS; a never started socket is closed directly). Reader
backpressure by a weighted semaphore (64 permits, a block weighs 32). `MelProcess.onSocketClosed` rejects pending requests at once.
`MelCertRetriever` closes its socket after handling the answer; delivery closes with `FINISHED`.
Pairing retries removed from `TwoInstances.pair`, `AuthHandshakeTest.pair`, `RegistrationTest.startPairing`.
Decision taken alone: `suspend()`/`resume()` of `MelAuthService` are NOT deleted (plan step 10 and brief problem 8 say "no callers", but
`AndroidPowerManager` calls both for power saving). They stay, with `restart()` calls for the services and the worker, and their test
stays. (They were deleted by mistake in commit 9442dfb7 and restored right after.) Deleted `SocketTest` (did not compile with the
new listener; step 10 deletes it anyway).
Observed before step 2 (old code, in the first full run): `RegistrationTest.serverTrustsNobodyWhoRejectedBeforeItsUserAccepted` failed
once ("server never heard about the rejection") = the old lost-message-on-close bug.
Tests: `CloseEventTest` (pending request fails at once, `onIsolatedConnectionClosed` exactly once); enabled
`RegistrationCommitTest.theLastDecisionCountsAlthoughThePartnerHangsUpRightBehindIt`.

## Step 3: send lock
`MelSocket.send` and `MelAuthSocket.sendBlock` hold `sendLock`. Enabled `ConcurrentSendTest.longMessagesFromSeveralThreadsArriveWhole`.

(Resolved in step 6, see there.) `AuthHandshakeTest.pairedClientCanConnectAgainAfterTheServerWasSuspendedAndResumed` failed in about 2 of 8 class runs
since step 2 (passes alone). Cause (from the log): after `resume()` the discovery makes both instances connect to each other, and
the test's own `client.connect(...)` is the third connection: the server drops it as a duplicate without answering (problem 2),
the client's connect job stays pending. Same cause as the disabled `DuplicateConnectionTest`s; step 6 must make it stable
(5 runs in a row). Not disabled, not loosened.

## Step 4: typed frames
`FrameCodec` (type 1 | length 4 | payload, MAX_FRAME 1 MiB, violation before allocation), `MelSocket` reads/writes frames (message = UTF-8,
block = binary; `sendBlock` moved into `MelSocket`), a violation closes with `PROTOCOL_VIOLATION`. Removed chunking, `MAX_CHARS`,
`msgBuffer`, `BLOCK_SIZE` mode, `isIsolated`, `allowIsolation`. `MODE_ISOLATE` is a plain message handled by `MelIsolatedProcess.onIsolateMessage`
(until step 8). File blocks are 1 MiB (`MelIsolatedFileProcess.BLOCK_SIZE = MAX_FRAME`, was 4 MiB). A message above 1 MiB is not sent (logged),
instead of the old corrupting chunking. Tests: `FrameCodecTest`, `FrameLimitTest`, non-ASCII test in `ConcurrentSendTest`. `:auth:test`, `:auth:integrationTest`,
`:filesync:test` green (3 min).

## Step 5: the state type
`SocketState` (New, Registering, Authenticating, Established, Isolated, Closed(reason, remote); immutable) in an `AtomicReference` on `MelAuthSocket`,
`Transitions` (pure table: state x role x message kind -> verdict) with `TransitionsTest` (all 108 combinations). `MelAuthSocket.onMessage` classifies and
dispatches; no lazy process creation, no `process` field, no `setProcess` (processes no longer register themselves in their constructor; the socket's state
is set explicitly: client before connecting, server on the first message, Established/Isolated by `MelAuthProcess` after the duplicate check).
Violation = `ProtocolError` message then close `PROTOCOL_VIOLATION`; unparsable JSON included. `whenClosed()` future and `getState()` for tests.
`MelValidationProcess`/`MelProcess` no longer `synchronized`, cache maps concurrent. A duplicate on the server now closes the new connection with `DUPLICATE`
(was: silent, client hung); on the client the connect job gets the existing process. All 7 `ProtocolViolationTest`s enabled and also check the reason.
Isolated: `Isolated` state is entered by the auth answer on both sides already (the MODE_ISOLATE hello stays a message until step 8).
Timeouts per state are step 9 (NEW/AUTHENTICATING) and step 7 (REGISTERING). `:auth:test` + `:auth:integrationTest` green.

## Step 6: authentication and duplicates
Both sides take the partner from the TLS session (trusted row by hash), `connectSSL` no longer sets it from the id. Client: untrusted -> close `UNTRUSTED`,
job fails with `UntrustedPartnerException` (the ConnectWorker then still tries a registration when allowed and no id was given); another trusted
partner -> the connection is kept as one to that partner, only its address/port are updated (`propagateAuthentication`), the job fails with
`WrongPartnerException` (isolated jobs: close `WRONG_PARTNER` at once). `ConnectedEnvironment.registerValidationProcess` is the atomic duplicate decision
(different initiators: lower initiator certificate hash wins; same initiator: newer wins); the loser gets `ProtocolError(DUPLICATE)` and closes
(`MelAuthSocket.duplicateLost`); a connect job that lost with `DuplicateConnectionException` is resolved with the winner as soon as it is registered
(`whenConnected`, 10 s). The address map is gone; `getValidationProcess(address, port)` looks the address up in the certificate rows.
Tests: `PartnerIdentityTest` (untrusted at the address: refused, nothing stored, all rows compared; other trusted partner: `WRONG_PARTNER`, only its
address/port changed; uses `FakePartner`), `DuplicateConnectionTest` x2 enabled (5 repetitions). Loop of 5 full `:auth:test :auth:integrationTest` runs:
4 of 5 green, the failing one was `AuthHandshakeTest...SuspendedAndResumed`: the test connected before the resumed server listened again (connection
refused, nobody retries). That is a race in the test, not in the code (found in the log); the test now waits until the server accepts connections again.
That test then ran 12 times in a row green.
Decision taken alone: a duplicate loser on the server side is told `DUPLICATE` and closed (before: silently left open, the client hung).

## Step 7: registration
New messages `RegisterDetails` (request payload, no certificate), `RegisterDecision`, `RegisterFinal`; `MelRegisterConfirm` and `MelRequest.certificate` are gone.
`MelRegisterProcess` rewritten as the REGISTERING handler for both roles: the partner of the server is the TLS client certificate; nothing is stored before
both users accepted; S persists (`CertificateManager.registerTrusted`: INSERT of a trusted row or UPDATE of the known one, one statement) before sending
`Final(true)`; C persists on `Final(true)` (`trustCertificate`), then closes with `REGISTERED`. A rejection stores nothing; the client deletes the entry it fetched only
if it was untrusted before. Decisions of the users come in on other threads, so the process guards its state with a lock and tells the handlers outside it.
No `DefaultDeferredManager().when(...)` any more. REGISTERING timeout: 5 min, `MelAuthService.setRegisterTimeoutMs` for tests (1 s). `importCertificate` reuses
the entry of a known certificate (refreshes name/address/ports). The client checks that the TLS certificate of the register socket is the one it fetched.
`Certificate.isKnownPartner()` (not stored) tells the handlers that the partner is known; `RegisterHandlerFX` and `AndroidRegHandler` show it (new Android string).
`ConnectWorker.register` returns the promise that `register` resolves/rejects (it used to never reject).
Tests: `RegistrationCommitTest` (known partner registers again and keeps access, enabled; the other direction: server forgot the client; pairing by mistake with a
trusted partner: nobody asked, only a connection; registration timeout leaves nothing), `TransitionsTest` extended (REGISTER_FINAL).
Bug found by `PartnerIdentityTest` (hung once in about 8 runs): `MelAuthProcess.authenticate` closed the socket before settling the job, and the close reported
`connection closed` to the same job first, so the second `reject` threw. Jobs are now settled first and only once (`reject` helper). 25 runs green after that.
5 full runs of `:auth:test :auth:integrationTest` green.
Not done yet from the plan text for this step: "the caller learns 'already paired with X'" (step 9).

## Step 8: isolated connections
ISOLATED is reached by the auth answer on both sides (server: state set, answer sent, then `onIsolatedConnectionEstablished`; client: state set on the answer,
service told, job resolved). `sendIsolate`, `onIsolated`, `isolatedPromise`, `MODE_ISOLATE`, the isolate hello and `ISOLATE_HANDSHAKE` are gone; only block frames
fit ISOLATED. The service creates the process: `IMelService.createIsolatedProcess(details, socket, partnerId)` (null = refuse); `MelService` implements it with an
allow-list (`allowIsolatedProcess(Class)`, `MelIsolatedFileProcess` always allowed, `MelTestService` allows `TestIsolatedProcess`); the class name from the partner is a
lookup key only. `MelService` tracks established isolated processes by `isolatedUuid`, `getIsolatedProcess` reuses an open one for the same partner service and class
(also one the partner opened) or connects; closing one removes only that one. `TManager.onIsolatedProcessEnds` forgets only its own process.
`MelIsolatedFileProcess.transfer()` sends one record per block frame (size as needed, no padding, no 0 terminator, no packing), `onBlockReceived` reads one record.
Bug found while testing (nothing in the build tested file transfers): `FileTransferDetail.readFile` skipped `offset` bytes from the current stream position on every
call, so every record after the first lost data (files above one block were corrupt, ranges too); it now tracks the stream position, limits reads to the end of the range
and fills the buffer completely. `sendingNotification.cancel()` no longer throws when the service has no notification.
Tests: `SeveralIsolatedConnectionsTest` (two connections both usable, closing one leaves the other and `getIsolatedProcess` hands it out),
`FileTransferTest` (empty, small, exactly one record, 5 MiB + 123, both directions, several files). 5 full runs green; filesync tests green (they have no transfer tests).

## Step 9: connecting
`ConnectWorker` rewritten as a straight sequence per job (plain `DeferredRunnable`, one local variable per socket, no shared field, no `shutDown()` that stops a handed-over
socket): authenticate first (also for "pair with this address": a partner both sides trust just connects, `MelValidationProcess.isAlreadyPaired()` tells the caller),
and only if that fails, nobody was named and registration is allowed: fetch the certificate (existing entries are reused), register on a socket of its own, wait for its
close, authenticate on a new one. Exceptions that say something (`WrongPartner`, `Untrusted` when a partner was named, `Duplicate`, `ConnectException`) reach the caller unchanged.
Timeouts (all settings on `MelAuthService` with short values in tests): connect and TLS handshake 10 s (`connectSSL`, `MelCertRetriever`), NEW (accepted socket
says nothing) and AUTHENTICATING 30 s, certificate retrieval answer 30 s. `MelAuthCertDelivery` serves every client in a thread of its own with a read timeout
(a silent client no longer stops the others). `shutDownCommunications` also closes every socket (`SHUTDOWN`). Retriever closes its socket after the answer.
Found by the tests: since step 5 a closed socket was never removed from the registry of normal connections (the state was already CLOSED when `ConnectedEnvironment.onSocketClosed`
looked at it, so `isConnectedTo` stayed true and a stale entry was only replaced by the next connection). `onClose` now tells the instance before the state becomes CLOSED, and
`getValidationProcess` ignores closed processes. Test helpers: `pair()` now waits for the connection the client makes after the registration (it belongs to pairing, and a
raw test peer that authenticated before it used to lose against it by the "same initiator: the newer wins" rule), `closeNormalConnections` for tests that want to start without one.
Tests: `ConnectingTest` (silent client on the delivery port, unroutable address within the connect timeout, mute connection closed by the NEW timeout, communications off
closes connections), `isAlreadyPaired` in `RegistrationCommitTest`. 5 full runs green.

## Step 10: cleanup
Deleted `LargeJson`, `LotsOfTests` (nothing but comments), `SocketTest` (step 2), `ReceivedJob`, `RequestTimer.kt`/`RequestTimerMain` (dead prototype of a request timer).
`BLOCK_SIZE`, `MODE_ISOLATE`, `MelRegisterConfirm` were removed with their steps. `suspend()`/`resume()` stay (see step 2). The brief has a status section.
P2 (socket rework) is complete: full `./gradlew build` to be run at the start of P3.

## P3: tests for the JDeferred replacement
Deviation from the runbook rule for P2, logged here: rewritten socket code (ConnectWorker, MelRegisterProcess) still uses JDeferred types inside, there is no
`PromiseAdapters`; the pieces are small and P4 ports them anyway.
Added: `DeferredManagersTest` (MelDeferredManager: empty resolves, settled promises call back, fail fast; ShutDownDeferredManager: nulls, rejection, waits for the last),
`PromiseSemanticsTest` (callbacks on the settling thread / at once on the registering thread, settled once for `Request`, `MelRequest.answerDeferred`, `ConnectJob`,
`IsolatedConnectJob`), `RequestAnswerTest` (answer resolves with the payload, error answer rejects with the `ResponseException`, a service that throws is answered and the connection
stays, no answer -> timeout, callback threads), `SettleOnceTest` (runs the flows that settle jobs and fails if a stack trace/log line says a promise was settled twice; mutation checked),
more in `DeferredRunnableTest` (started promise, settle once, shutdown with a null `onShutDown`), `SeveralIsolatedConnectionsTest` (`getIsolatedProcess` reuses an open one, opens a
new one after a close and after a failed attempt).
Double settles found and fixed: `DeferredRunnable.resolveStarted()/rejectStarted()` settle the started promise once; `MelAuthWorker` (resolved in the catch block and again right after it),
`BrotCaster` (resolve after resolve/reject when the network goes down), the services and `IndexerRunnable` use them. In `MelAuthProcess`/`ConnectWorker` jobs are settled through guarded helpers.
filesync baseline on the result of P2: `./gradlew :filesync:test` green: BashToolsStuffModifiedTest 6, BashCommandsTest 6, ConflictTest 15, SingleServiceTest 4, SqlEnumTest 5, BashToolsTest 7,
ObservableTest 2 and 8 more single-test classes (IndexerTest, TransferManagerDummy, SerTest, FreeSpaceTest, BashToolsTestOlde, InsertTransactionTest, FsTest, MergeTest); `:filesync:integrationTest`
has nothing tagged. There are no filesync tests that exercise the transfer or the services; the file transfer is now covered by `FileTransferTest` in auth.
Modules without automated tests (android, authfx, fxbundle, serverparts, miniserver, contacts): compile-only safety net, `./gradlew build` includes the Android assemble.
Full `./gradlew build` after P3 tests: see the line below at the end of P3.

## P4: the JDeferred replacement
Done in one atomic step (the public types are shared by all modules, so there is no green state in between): a script (`scratchpad/port/port.py`, not kept) did the mechanical part in
the 76 files that imported `org.jdeferred` (imports, `Promise<D,F,P>` -> `Promise<D,F>` by a bracket-matching parser, `DeferredObject` -> `PromiseImpl`,
`new DeferredObject<>()` -> `PromiseImpl.withoutThrowingOnException()`, `done/fail/resolve/reject/isResolved/isRejected` -> `onDone/onError/resolveOk/resolveError/hasResult/hasError`),
a compiler-driven fixer renamed the same calls in files that only use `Request`/`Job`/`connect()` (exactly where javac/kotlinc said "cannot find symbol"), the rest by hand.
By hand: `DeferredRunnable` (started promise is a `PromiseImpl`, `ResolvedDeferredObject()` = `Promise.resolvedOk()`), `Job<R,F>`/`AConnectJob<R>`/`Request<T>` extend `PromiseImpl`
(public no-arg constructors calling `super(false)`), `MelRequest.answerDeferred`, `MelBoot` (Kotlin, `Promise.fusedAnd`), `MelAuthWorker` (`fusedAnd`), `MelAuthServiceImpl.resume` (dead branch
with an always empty list removed), shutdown sites (`ShutDownDeferredManager` -> `Promises.allSettled(...)`, a 10 line helper on top of `Promise.fused`), `.always(...)` -> onDone + onError,
Android `FailCallback` -> `ActionConsumer`, catches of `IllegalStateException` for double settles -> `AlreadyResolvedOkException | AlreadyResolvedErrorException`.
Kotlin needs ``import `fun`.with.promise.X`` (fun is a keyword). Deleted: `MelDeferredManager`, `ShutDownDeferredManager`, `DeferredManagersTest` (replaced by `PromisesTest`). `PromiseAdapters` was never needed.
Removed `api libs.jdeferred` from `auth/build.gradle` and `jdeferred` from `libs.versions.toml`. `grep -rn "org.jdeferred" --include=*.java --include=*.kt --exclude-dir=build .` prints nothing.
`fun.with` itself was not changed.
Test race found on the way (not a code bug): `AuthHandshakeTest...SuspendedAndResumed` probed the port right after `suspend()`, while the old listener was still open; it now waits for the port to close, resumes, then waits for it to open.
The 96 tests of `:auth:test :auth:integrationTest` and `:filesync:test` green, full `./gradlew build` green.

## Follow-up: file transfer, resume, tests (2026-10-21, after the unattended run)

Asked by the user: tests for file sync/transfer, a sound resume logic, and a test that transfers several files at the same time without mixing them up.

**What was there:** no running test for sync or transfer in `filesync` (every `@Test` of `DriveTest`/`FileSyncTest`/`TransferTest` is commented out), only the record level `FileTransferTest` in auth.
Resume was not implemented (`// todo resume downloads` three times): `transferred` was written to the database but every attempt asked for byte 0.

**Bugs found on the way (all fixed):**
- `TManager` "locked" with `P.confine(x)` and never took the lock (`confine` only creates the lock object, `run`/`access` takes it), and its loop did not wait for the connections it started.
  Several `TransferFromServiceRunnable`s ran for one connection, asked for the same files, finished them twice, the second finish found the file already moved away and started the file over: a file with the
  wrong content was found in the client directory (`manyFilesAtOnceDoNotMixUp` failed with `big/b2.bin(wrong content)`). `TManager` is rewritten: real synchronization, the connect phase blocks, the loop waits for `research()`
  (a flag) when every partner with work has a retriever, at most one retriever per partner service, a failed or lost attempt is retried with backoff (`retryBaseDelayMs` 2 s doubling up to `retryMaxDelayMs` 5 min).
- `StandardFileWriter` opened a `FileOutputStream` (truncates) and ignored the `length` argument; so a resumed write would have destroyed the beginning. New `IFile.writer(position)`: continues a file, cuts what is behind `position`,
  null if the file is too short (or, on Android, not exactly `position` long: SAF/`AndroidFileWriter` can only append; they now use "wa" for a continued file and "wt" for a new one).
- `FileTransferDetail.onReceived` took any offset and checked the size with `assert`. Now the pieces have to follow each other from `start` to `end`, otherwise the transfer fails (`IOException`, reported once to the failed listener, file closed, what came stays).
  A last piece (`t`) before the end, an `E` record, a record longer than its frame also fail the transfer. `readFile` throws if it cannot get to the offset instead of reading from the wrong place; missing bytes are counted against the range, not the buffer.
- The sender sent one file at a time (head of the queue, keyed by hash: two requests for one hash overwrote each other). Now keyed by stream, the pieces of all queued files take turns (a small file does not wait for a huge one).
- `MelFileSyncService.handleSending`: files from the wastebin were sent without hash, a missing file was announced as error and then used anyway (NPE), the requested range was not checked. Now `sendRequested` checks `0 <= start <= end <= file length` and announces what it cannot send.
- The request for files was a fire-and-forget message. When the connection that carried it was the loser of the duplicate rule (it happens right after the client restarts) the request was lost and the client waited for ever. It is a request now (answered when
  the files are queued, rejected when the connection is closed or after the request timeout), and the retriever has a stall watchdog (`TManager.stallTimeoutMs`, 120 s without any piece: the connection is closed, the files are suspended and retried).
- A file was handed to the `SyncHandler` without looking at its content. Now size and md5 (the hash it is stored under) are checked first; a file that does not match is deleted and fetched again from 0 (3 attempts per run, then suspended and retried with the backoff).

**Resume (how it works):** the retriever writes to `<transfer dir>/<hash>`. While pieces arrive `transferred` is updated after each piece (after it was written). When the connection breaks, `suspendUnfinished` stores the position and sets SUSPENDED; at boot
`FileSyncDatabaseManager` resets every state to NOT_STARTED and keeps `transferred`. The next attempt starts at `min(bytes on disk, transferred)` (the database is behind the disk when the app dies between writing and noting down, the disk is behind the
database after `PRAGMA synchronous=OFF` and a crash), tells the server where to start (`FileTransferDetail.start`), opens the file with `writer(start)` (cuts the rest) and asks for `start..size`. A file that cannot be continued (writer says no) starts at 0. All bytes there
-> only the hash is checked. The database or the files can be wrong or missing in any combination, the final hash check is what guarantees the content.

**Tests added:**
- auth unit: `FileTransferDetailTest` (13: order, gaps, overlaps, beyond the end, continue/cut/hole, sender range and start, short files, writer length).
- auth integration, `FileTransferTest` (5 -> 17): many files in both directions at once (same size/different content, empty, 1-byte, around the record size), several connections at once with the same stream ids, a small file passes a huge one,
  the transfer starts where asked (only the rest is sent), every kind of broken transfer fails only that transfer (gap, twice, beyond the end, early last piece, record longer than it says, error record, short file) and the connection and others go on.
- filesync integration, new `FileSyncTransferTest` (12) on top of a new harness (`FileSyncInstances` on `TwoInstances`; `auth` exposes its test classes as `authTestOutput`): files arrive (also empty, twins), 122 files in more batches than fit one request,
  connection cut at 6 MiB of 24 MiB -> retried by itself and continued (sent 6341364, continued at 6291354), continue exactly where the file ends, the client is restarted half way (continues), rubbish behind the noted position (cut off), file shorter than
  the database says, file gone (starts over), wrong beginning (detected by the hash, started over), a server that sends other content (never delivered, 3 attempts, delivered when fixed), a server that does not answer, a server that sends nothing.
- Mutation checked (change the code, the test must fail): sender ignores start, no hash check (2 tests), resume trusts only the database / only the disk, never resumes, resume does not cut the rubbish, no order check, demux ignores the stream id, sequential sender, no truncate in `writer(position)`.
  Survivors that are equivalent: the two guards against a second retriever on one connection (either one is enough now that the loop is sequential).

**Not done / risks:**
- The old `DriveTest`/`FileSyncTest`/`TransferTest` (conflicts, merge, restart scenarios) are still disabled; they use fixed ports and directories and `sleep`. They are not about the transfer and were not touched.
- Android was only compiled (`AndroidFile.writer(position)`, `SAFFile`), not run. Android can only continue a file that is exactly as long as the noted position; otherwise the file starts over (correct, slower).
- Retrying forever (every <= 5 min) for a file the server cannot send (E record) is deliberate: the next reconnect would do that anyway.
- The hash check reads every finished file once more.
- A transfer that fails because the partner has no such file stays SUSPENDED until the next retry; the `available` flag logic of the sync is unchanged.

### Live changes do not sync (analysed, tests written, FIXED - see the end of this section)

After the first sync, changes do not reach the other side. This is not the transfer, and not the rework: it is the indexing/commit part of `filesync`, which was left half rewritten in June 2022.
It was analysed in a second session (the user asked to get to the bottom of it, write tests and not fix yet).

**When it broke.** Commit `2bd0369b` "quicksave, transfer" (2022-06-15) rewrote `AbstractIndexer`: new `initStage`/`examineStage` (`examineDirContentHash`, `examineDir`) and the stage set ids were renamed
(`stageSetId` -> `initialStageSetId` + `examinedStageSetId`). The old algorithm was kept as `initStageOlde`/`examineStageOlde`. `8e2352a3` "minimize stageset" (2022-06-16) and `b4a4b9d3` (2022-11-19, adds the STAGED status) adapted
`IndexerRunnable` (the index at boot), the file watcher's `StageIndexerRunnable` was never adapted. The commit itself carries `Lok.debug("asd")`, `"nuvng"`, "TRANSACTION DISABLED". The `path` column of the stage table is from `1fa2e765` (2022-05-31).
The first sync works because everything is new then.
**Evidence that it worked before:** switching both indexers back to `initStageOlde`/`examineStageOlde` (experiment, reverted, nothing of it is committed) makes the 4 `LiveChangeIndexingTest` and all 7 `BootIndexTest` pass,
the `ChangesWhileDownTest` cases for a file changed or deleted on the client and made or changed on the server pass, and so do 2 of 4 `FileSyncRoundTripTest` (file created and changed on the server). So commit, propagate, merge and transfer work with the old indexer; two more defects remain (B, C below).
I did not run the 2019-2022 code itself (the build files were reworked since), only the old algorithm in today's tree.

**A. The new indexer (`AbstractIndexer`, `StageIndexerRunnable`)**, found with `BootIndexTest`/`LiveChangeIndexingTest`:
1. `StageIndexerRunnable` hands `initialStageSetId` to the listener, but `examineStage()` deletes that stage set and continues in `examinedStageSetId` (as `IndexerRunnable` does). Server: "deleted stageset 3", client: `CommitJob` finds nothing.
2. `StageIndexerRunnable` does not call `hashFiles()`/`minimizeStageSet()` and does not set STAGED (`IndexerRunnable` does). Without the hash the commit dies on `fswrite.contenthash NOT NULL`; the examined stage set stays behind in status "staging" (orphan: the client leaves one after every sync, `SyncedStateTest`).
3. `deleted` stays NULL for every stage that exists on disk (the root directory, files reached by their own event): `StageDao.getNotDeletedStagesByStageSet` asks `deleted=false`, NULL does not match, so such a stage is never committed.
   Consequence: the root's fs entry keeps the content hash of an empty directory for ever (`eff7d5db...`), the root always looks changed, and every index restages the whole tree (`nothingChangedMeansNothingToCommit`: the delta is the entire tree).
4. Stages made while walking a changed directory (`examineDirContentHash`) never get their `fsId` (the condition `fsEntry != null && !fsEntry.getIsDirectory().v()` tests the directory entry and is never true; subdirectories are not linked at all). So unchanged files and
   directories are committed as new; an unchanged subdirectory is staged without content hash and the commit dies with `fswrite.contenthash NOT NULL` ("dir"). On the client the same re-stages the sync's own writes.
5. Deletions are never detected: the Unix watcher only reports the directory (`onTimerStopped` uses `find -newer`, `analyze()` adds the deleted path to a collection that `onTimerStopped` does not use), and the new walk only lists what exists (`todo recursive delete` in `examineDir`). The old code staged what the fs table has and the disk lacks.
6. Files reached by their own event get no size (`stage.size` null); only the directory walk sets it.

**B. `ServerSyncHandler.executeCommit`:** a stage from a client without `fsId` and `fsParentId` gets no `path`/`depth` (they are derived from the fs entries only), `stage.path NOT NULL` fails. This is every new directory made on the client, and everything in it
(a new file next to the others or in an existing directory is fine). With the old indexer: `aFileMadeOnTheClientInANewDirectory...` and `aNewDirectoryInANewDirectory...` fail, `...NextToTheOthers`/`...InAnExistingDirectory` pass. The path has to be derived from the parent stage that came with the same commit.
**C. A file deleted on the server is not deleted on the client** (also with the old indexer; found with `aFileDeletedOnTheServer...`, both as live change and as change while down). The server stages and commits the deletion correctly, the client asks the server for the directory
(`syncFromServer.SOMETHING.DELETED?`, `requestDirectoriesByIds`), but the stage set it gets (`src=server`, 5) holds only the root directory, no stage for the file. Not traced further. (`stillExistingIds` compares server fs ids with the client's own fs ids; they only coincide by luck.)
The client then commits its own stage set 6 back to the server (version 2 -> 3).

**Tests** (all in `filesync/src/test/java/de/mel/auth/service`, harness additions in `FileSyncInstances`: `Snapshot`, `RecordingServerService.indexed`/`holdBackCommit`, `restartServer`; `TwoInstances.restartServer`):
- `LiveChangeIndexingTest` (4, disabled): the file watcher's indexer on a server whose commit is replaced by a recorder: changed file, new file in a new directory, deleted file, "there is a stage set behind the id". Fail on A1-A3, A5, A6; pass with the old indexer.
- `BootIndexTest` (7, disabled): the boot index of a restarted server, commit held back: nothing changed, changed content, new file in an existing directory, new directory chain, deleted file, deleted directory, file<->directory. Fail on A3, A4 (and A5); pass with the old indexer. Written to the contract in the class comment.
- `ChangesWhileDownTest` (9, disabled): the whole way (index at boot, commit, propagate, merge, transfer) for changes made while one side was down, in both directions. Fail on A3-A5, B, C.
- `FileSyncRoundTripTest` (4, disabled, from before): the same for live changes through the watcher.
- `SyncedStateTest`: 2 enabled (the fs table of server and client equal their disks after the first sync: size, hash, inode, modified: this part is right), 1 disabled (the client must not stage its own sync writes; A2-A4).
Re-enable the classes when the matching defect is fixed; the order that makes sense: A1-A3 -> A4 -> A5/A6 -> B -> C.

**Fixed (third session, all tests of this section enabled and green):**
- A1-A3, A6: `AbstractIndexer.finishStaging()` (hash, minimize, delete the stage set if empty, else STAGED) is shared by `IndexerRunnable` and `StageIndexerRunnable`, which now hands over `examinedStageSetId` (null if nothing changed; before, a deleted id was also given to the listener at boot).
  Every stage gets `deleted` set explicitly. Minimizing never drops a stage that is deleted.
- A4, A5: `examineStage` was rewritten (the old `examineDirContentHash`/`examineDir` are gone) as `examinePath`/`examineDirectory`: each reported path is compared with the fs table (changed file = created/modified/inode/size differ), parents are examined first, so a stage hangs below its parent stage or the fs entry of the parent;
  an unchanged file is not staged at all (no insert, no hashing: boot index of an unchanged tree stages nothing). A directory always gets its content hash; if it equals the one in the fs table nothing was added or removed, otherwise the new names are examined (a new directory down to its leaves) and the names the fs table has but the disk lacks are staged as deleted.
  Files that the fs table still waits for (`synced=false`) count as present for the hash and are never staged as deleted. The Unix watcher no longer drops what the events reported (`FileWatcher.takePathCollection`).
  The old algorithm (`initStageOlde`, `examineStageOlde`, ...) is still in `AbstractIndexer`, unused.
- B: `ServerSyncHandler.executeCommit` derives path and depth of a stage with neither fs id nor fs parent id from its parent stage of the same commit.
- C: it was not the client. `Wastebin.deleteFsFile` deleted the entry from the `fs` table, but `commitStage` works on `fswrite` and replaces `fs` with it at the end, so the entry came back (the server never deleted the file in its fs table, so every directory request still listed it). It deletes in both tables now.
- D (found because the server-restart tests were flaky, 50% in a class run): the boot index of the server committed what changed while it was down but, unlike the file watcher's listener, never called `propagateNewVersion()`. The clients only learned of it if they happened to sync. It does now.
- E (same tests): the client's `SyncRequest` can go out on a connection that the duplicate rule closes at that moment (known race, see the socket rework); the request fails and nothing asked again. `ClientSyncHandler` now schedules a new `SyncClientJob` when connecting or the request fails
  (300 ms doubling up to 10 s, 10 attempts, counter reset by an answer or a new try from a message of the server). Note `syncFromServer()` returns when the stage sets are cleaned up, not when the request is done (`WaitLock` is unlocked early), that was not changed.
- F (hint from the author: Android truncates file times): the check "file unchanged" (`AbstractIndexer.sameAsInFs`) compares the modified time by the second, not exactly. An exact comparison would take a file whose time was stored with other precision for changed on every scan (the hash then shows it is not, the stage is dropped, but the fs table keeps the old time and the file is hashed again and again).
- G (found on linux): the created time is not compared any more, only modified time, inode and size (author: only what works on all platforms; they are hints, the md5 decides). Linux cannot set the birth time of a file it received (`BashTools.setCreationDate` silently does nothing there, Android has no created time), and `FileDistributor.updateFs` does not store the created time of the disk. So the fs table of a receiving machine held the sender's created time, the disk its own, and every received file looked changed and was hashed again at each index. Test: `SyncedStateTest.aFileTheClientReceivedLooksTheSameAsInItsFsTable...` (fails with the created comparison).
- H (like rsync -t): a received file gets the modification time of the sender. The modified time was `@JsonIgnore` in `FsEntry` and `Stage`, so it never crossed the wire; now it does (wire format change, author: no running instances to care about). `FileDistributor.updateFs` sets it on the file (`IFile.setLastModified`, best effort: android shared storage / SAF may refuse, then the file keeps the download time and nothing breaks) and stores what the disk says afterwards. Tests: `SyncedStateTest.aReceivedFileKeepsTheModificationTimeOfTheSender`, `...ServerReceivedFromTheClientKeepsItsModificationTimeToo`. Only linux was run.
  Unit test `SameAsInFsTest`. The Unix `stat` already gives whole seconds, so no integration test can show this on Linux. Not tried on Android/Windows.

## File dump

`filedump` (a one way backup: `SourceService` is a file sync client, `TargetService` a server with `TargetSyncHandler`) builds, but its only test (`DumpTest.test`) has an empty body ("todo deal with the deadlock"). `DumpFlowTest` (3 tests, disabled) is the first one that runs a source and a target: files of the source arrive; a file made later arrives; a file already at the target is kept and the new one gets another name. **All three fail: nothing arrives at the target.** Observed in the log:

1. `TargetSyncHandler.commitStage` logs "this is either the root dir or an error:" for the source's root stage (name ""). The root of the source is never mapped to the root of the target, so `stageIdFsIdMap` has nothing for it.
2. The fs entries it inserts have no `depth` and no `path`; `fsentry.depth`/`path` are `NOT NULL` (since 2019-11, `CreationScripts.kt`). Insert fails: `NOT NULL constraint failed: fsentry.depth`, the commit is lost.
3. By reading (not observed): `ServerSyncHandler.executeCommit` takes the fs ids in a commit as ids of the server's fs table (`fsDao.getFsEntryById(stage.getFsId()/getFsParentId())`). For a file sync client that is true, the ids are shared. For a dump source they are the source's own ids and mean nothing at the target; it only works by luck when both start with the same numbers.

Not checked whether it ever worked: `TargetSyncHandler` has not set depth/path since the columns became NOT NULL (2019-11), and the tests of the dump were never more than the empty one. Nothing of the rework (socket, promises, transfer, live changes) is involved in 1 and 2.

**Repaired (same session).** Design as the author describes it: a one way backup that keeps everything. The content hash counts, the names less.
- `TargetSyncHandler` rewritten. A commit is read by path and name; the ids, parents and versions of the source are not used and not answered (the answer is empty, so the source keeps its own fs ids). A file whose hash is stored already (`FsDao.getFilesByHash`) is not stored and not transferred. A new hash gets the path of the source as far as it is free, directories are made as needed (fs table and disk); a name taken by other content becomes `name_<created>.ext` (then `_1`, ...); a directory name taken by a file gets `_dir`. Deleted stages are ignored. Fs stage sets (the target's own disk) are committed as in file sync.
- `Stage.path` crosses the wire now (it was `@JsonIgnore`): a commit holds only the changed file when its directory did not change, so the target needs the path.
- `SourceSyncHandler`: the source does not ask the target for its changes (`syncFromServer` does nothing). Without that a source with nothing to push gets the files of the target (and, with several sources, of the others).
- `DumpFlowTest` (7, enabled): files arrive; a later file arrives; a name taken keeps both; the same content under another name is stored once; a deleted file stays; a changed file leaves both versions; nothing of the target comes back to the source. Mutation checks: without the hash lookup the "stored once" test fails, with the pulling client handler the "nothing comes back" test fails.
- Open: Several sources at once, dump on Android and a directory that is a file on the other side were not tried. Empty directories of the source are not taken.

## Watcher scan by time (no time files)

`UnixReferenceFileHandler` (used by `FileWatcherUnix` and android's `RecursiveWatcher`) asks `find` what changed since the last scan. If `BashTools.canFindByTime()` (tried once: `find -newerct <ISO 8601 UTC, whole seconds>`, accepted by GNU find and bfs; GNU also takes `@epoch`, bfs does not) it keeps one number, the start of the last scan, and asks for changes since that minus 2 s. No reference files, so nothing depends on which volume they are on, and the margin is arithmetic (`-newercc` is strict: a change in the same clock tick as the reference was missed for good). `-newerct` compares ctime, so files that come with an old modified time (cp -p, rsync -t, received files) are found. Where `find` cannot do it (android's was not tried) the two reference files are used as before. Found twice is no problem, the indexer drops what did not change. `find` stays: events get lost on every platform. Tests: `UnixReferenceFileHandlerTest` (mutation check: with `-newermt` the old-modified-time test fails).

## FsConvenience

`de.mel.filesync.nio.FsConvenience` (one per service, `MelFileSyncService.getFsConvenience()`, platforms override `createFsConvenience()`): what the file system of the root can do on top of the minimum, applied where it can be, nothing may depend on it.
- `applyModified(file, fsFile)`: sets the modified time of the fs entry on the file (rsync -t). `FileDistributor` does it on the download before it is moved (so the file arrives in place with its time) and on a copy after it is made. `updateFs` stores what the disk says afterwards, so a platform that refuses (android shared storage, SAF) keeps a consistent table.
- `copy(source, destination, physicalAllowed)`: `UnixFsConvenience` tries `cp --reflink=always --preserve=timestamps` for plain files (never a physical copy by the back door; found out by trying, not remembered after a "not supported"), else a physical copy if allowed, else `NOT_DONE`. The dump does not use it yet (clone-based dedupe is undecided).
- **Bug found**: the old `FileDistributor.rawCopyFile` wrote every chunk at position 0 (`AbstractFileWriter.append(data, offset, length)` takes the position in the target file), so the copy of a file larger than 64 KiB (same content in a second place) was only its last chunk, and a failed copy was swallowed. Replaced; test `SyncedStateTest.sameContentInTwoPlaces...` (fails with the old position).
- **Deadlock found by the transfer tests** (timeouts in the restart tests, 2 of 12 runs): `getFsConvenience()` was `synchronized` on the service, which `MelService.getIsolatedProcess` holds while it waits for connections. Now a private lock. With it: 45 of 46 runs of `FileSyncTransferTest` pass; the failure was `manyFilesAtOnceDoNotMixUp` once (cause found below).
- **Cause of the "mix up" failures**: `FileDistributor` was started twice. `createJob` checked `if (!running ...)` and called `fileSyncService.execute(this)`, but `running` is only set when the thread has started: two jobs in a row ran `run()` of the one distributor object on two threads, which loaded the same READY tasks and copied, moved and deleted the same files at the same time (seen in the kept reports of 2 of 30 runs: `empty/e0.bin` and `copies/c4.bin`, created and deleted within ms, never again). It was always there; the old in-process copy was too fast to hit it often, the process per reference copy is not. Now an `AtomicBoolean active` lets one thread work; the end of a run checks for jobs that came meanwhile (not after a failure). `FileSyncTransferTest.manyFilesAtOnceDoNotMixUp`: 2 of 30 failed before, 0 of 40 after. `FsConvenienceStressTest` (2000 copies) shows the copy code alone is not at fault.

## Dump: another name for stored content, dead code removed

- **Default (author's decision)**: another name for content the target has (stored or on its way) is kept as a reference if the file system can have them, else it is left out. A physical copy is never made. `TargetService.createFsConvenience()` = `ReferencesOnly(...)` (`FsConvenience.copiesAreOptional`). `TargetSyncHandler.storeFile` makes the entry (unsynced) and the transfer as for new content; the file distribution places it: with the content at hand (after the download, or `TManager.placeKnownFiles` for content that is there) `FileDistributor.copyFile(..., mayBeLeftOut = true)` makes the reference or drops the fs entry again. The same name with the same hash is not made twice. Directories made for a name that is dropped stay (empty). Tests: `DumpFlowTest` (9): the real file system (reference or left out, never a copy; checked by what `cp --reflink=always` can do next to the target), a stand-in that pretends to reference (all three names, entries synced), a file system without references (one entry remains). Mutation check: with `ReferencesOnly` letting physical copies through, the last one fails. The fs table knows files by md5 (the tests' `writeFile` returns sha-256).
- **Removed** (unused): `AbstractIndexer.initStageOlde/examineStageOlde/roamDirectoryStage/updateFileStage/isValidSymLink/buildPathFromStage` and their imports (about 570 lines), `DumpSettings` (its only setting, `eliminateDoubleHashes`, was never read).
- **Not done**: periodic full reconciliation (the boot index repeated as a safety net for lost events) - the author asked what it is, see the answer in the conversation; undecided.

## Creation time is not sent

Author: the creation time cannot be set on Linux (and Android has none), so it is not transferred; only the modified time is. `Stage.created` and `FsEntry.created` are `@JsonIgnore` now. The receiver stores what its own disk says (`FileDistributor.updateFs`; directories: `SyncHandler.updateInodeModified`), so table and disk agree for the creation time as for the modified time. `FileDistributor.setCreationDate` (which set the creation time to itself) is gone; `BashTools.setCreationDate` and its platform versions are unused now and still there. The dump names a conflicting file with the date of its last modification (it used the creation date). Test: `SyncedStateTest.afterTheFirstSyncTheClientsFsTableAgreesWithItsDisk` checks created as well (fails without the line in `updateFs`).
- Decided against (author): a periodic re-scan. The levers are modified time, inode, size and, for directories, their entries; a full scan only on request. Done below.

## Incremental start, full scan by hand

Author: no heavy I/O on old phones, full scans only on request.
- The index that runs when a service starts (`IndexerRunnable`) looks at what changed since `FileSyncSettings.lastScanTime` (`find -newerct` through `BashTools.stuffModifiedAfter`, minus a 2 s margin) instead of listing and stat-ing every file. Deleted files are found through their directory (its entries are compared). The time is the beginning of the last index that went through (saved after the listeners were told; after a failure the old one stays, so the next run does more, never less). Only the start and the full scan save it; the watcher does not, so after a long run the next start looks back that far, which costs the changed paths of that time and no more.
- Every file is looked at instead if: the setting `fastBoot` is false (this is what the setting means now), there is no saved time (first start), the saved time is in the future (the clock went back), or the platform's `find` cannot take a time (`BashTools.canFindByTime()`, android's fallback).
- `MelFileSyncService.fullScan()` / `Indexer.fullScan()`: the index over every file, started by hand (the button is not there). It re-runs the indexer runnable with `requestFullScan()`.
- A user who changes a file so that mtime, size and inode stay and then hides it from ctime (clock) deserves that it is not noticed (author).
- Removed: `AbstractIndexer.fastBooting` and `IndexHelper.fastBoot` (the old way, dead).
- Tests: `IncrementalBootTest` (4: the start only looks at what changed and the full scan finds the hidden change; `fastBoot=false`; a saved time in the future; no saved time). Mutation checks: always-full fails the first, no clock check fails the third. `ChangesWhileDownTest`/`BootIndexTest` now run their restarts on the incremental path.

## Conflicts: same file changed on both sides

Tests (`ConflictSyncTest`, 3): both sides change while apart. (1) the same file, the user keeps the server's version, (2) the same file, the user keeps the client's version, (3) different files: no conflict, both changes meet. The test answers whatever question is open (the client replaces a question by a newer one about the same conflict, see below) and tells the sync to go on with a `CommitJob`, as the conflict dialog of the GUI does. It waits until the server has indexed its change before the client starts: else the client's commit can come first and there is nothing to collide.

Found and fixed: **keeping the client's version lost the file on both sides.** `ClientSyncHandler.handleConflict` committed the whole stage set of the server after the decision, so the version the user rejected was put in place on the client (the old file goes to the wastebin first), then the client's merged set was sent and the server deleted its file too. Now the stages of the server that lost against a local decision are removed from that set before it is committed. Without the line `sameFileChangedOnBothSidesTheClientsVersionWins` fails (mutation check, final test code). Also `deleteStageById(<id of the merged stage set>)` is `deleteStageSet(...)` now (it deleted a stage row that happened to have that number; no test for it).

Seen in the logs, **not changed** (no test shows a difference, the author should decide):
- `handleConflict`, no conflict: `solver.getMergedStageSet()` is read but `merge()` was never called (only the solved-conflict path calls it) -> `UninitializedPropertyAccessException` in `ClientSyncHandler.handleConflict` after `commitStage`, so the cleanup behind it (`deleteStageSet(local)`, `minimizeStage`, `addJob(CommitJob)`) does not run. The sync converges anyway through other paths. Calling `merge()` there is the obvious repair, but it changes the flow of the most common case, so it needs a test that shows the difference first.
- `syncFromServer` deletes the server stage sets and pulls new ones each time, so every pull while a conflict waits for the user replaces the question (new stage set ids -> new solver, `deleteRelated` drops the old one, "conflict solver cleanup missing" is logged). An answer given to the old question is lost. A GUI that opens a question and gets the solver by its identifier can find nothing. Not pulling while a conflict is open would avoid it.
- `ConflictSolver.foundRemote`: the branch that adds a deleted stage when "local was chosen" has no test that reaches it (`remoteStage` is flagged merged by then).
- `ClientSyncHandler.deleteObsolete` is empty.
- Not tried: a file deleted on one side and changed on the other, directories with conflicting content, several conflicts at once.

## Conflicts: second round (deleted vs changed, decisions that survive, directories)

Tests: `ConflictDeleteTest` (9: the client or the server deletes a file or a directory while the other side changes it; either side wins; both delete) and, in `ConflictSyncTest`, directories with a conflicting file and added files, and "an answer given once survives a second pull / a change of the client". Every scenario waits for its expected end state, not only for equal directories (two directories that both lack a file are equal on the way). Everything the loser gives up must be in the bin of that side, and the bin names a file by what it holds. All waits are halved (30 s, 15 s, 10 s), a test may take one minute.

Found and fixed (each has a test that fails without the line, unless noted):
- `ConflictDao.getRemoteDeletedByLocal` returned the columns of the conflict swapped: the "local" stage of a conflict "we deleted, they changed" was the server's. "Keep local" and "keep remote" meant the opposite in the dialog and in the merge.
- Keeping a change against a deletion of the other side: the merged stage carried the fs id of an entry the server had deleted, `ServerSyncHandler.executeCommit` threw a null pointer exception and never answered the request. A stage with an unknown fs id (or fs parent id) is a new entry there now. The client drops the stale fs row when the server answers with another id (no test shows a difference).
- Keeping a change below a directory the other side deleted: our stage set holds only the changed file, not its directory (a directory's hash is made of names). The directories down to the file are made again in the merged set (`ConflictSolver.recreateDirectories`) and survive `minimizeStage` (`mustBeSent`: they look like what the client has, not like what the server has).
- Keeping our deletion of a directory against a changed file below it: `foundLocal` returned without putting the deletion into the merged set.
- `foundLocal` threw a null pointer exception for a stage that only exists on our side in a conflict (`rejection!!`), which ended the pass over our stages silently: the changed file of the same directory was never merged and the server deleted its copy.
- `Wastebin.deleteFsFile` named a binned file by the hash of the fs table (the content when it was synced), also when the user had changed it since; it did not bin a file that was not synced at all. It names the file by what it holds now.
- `ClientSyncHandler`: a `TooOldVersionException` counted the highest version of any stage set, also our own merged set (it gets `remote.version + 1`), as "the news are here". `StageDao.getLatestStageSetVersion` counts stage sets of the server only. When the news are here as a stage set, a `CommitJob` is queued (nothing looked at it).
- Decisions (item 1 of the analysis): answers are kept by what is in conflict (path, name, hash and deleted flag of both stages), not by stage ids (`ConflictSolver.decisions/applyDecisions`). A pull makes new stage sets and with them a new question; the answer to the old one counts for the new one. Questions that were replaced are kept and read, a dialog may still show one. They stay after the commit: while the result travels, another pull can bring the old state of the server back and the same two versions meet again. `deleteObsolete` clears the solver map.
- `deleteStageById(<id of a stage set>)` is `deleteStageSet(...)` now (no test).
- When local was chosen, the server's rejected stages are removed from the stage set that is committed (before: the first round).

Not changed:
- No guard against pulling while a question waits: not needed with the carried-over answers.
- Conflicts about directories take the content with them (`Conflict.decideLocal` cascades): keeping the client's directory deletes files only the server has (they go to the bin of the server). The unit tests (`ConflictTest`) require directory conflicts, so I did not change it. Author's call.
- `handleConflict` without a conflict still reads `getMergedStageSet()` before `merge()` ran (exception, cleanup behind it skipped).

**Disabled (7 tests, intermittent after a cold start)**: `sameFileChangedOnBothSidesTheClientsVersionWins`, `directoryWhereBothAddedFilesAndChangedOneTheClientsVersionWins`, `anAnswerSurvivesThatTheClientChangesAgainBeforeTheSyncGoesOn` (ConflictSyncTest), `clientDeletesWhileServerChangesTheDeletionWins`, `serverDeletesDirectoryWhileClientChangesAFileInItTheChangeWins`, `serverDeletesDirectoryWhileClientChangesAFileInItTheDeletionWins`, `serverDeletesWhileClientChangesTheChangeWins` (ConflictDeleteTest). They pass about 30 of 30 when run again and again, and fail right after a recompile in about one of three runs (6 cycles: 5, 2, 2, 1, 1, 1 failures). Cause, as far as the evidence goes: the steps of the client (pull, resolve, commit, the answer of the server) interleave over the same stage sets. A trace of one failing run (an in-memory list of events, because logging closes the window; also a read of the merged stage set at the start of `after()` or a 50 ms delay there made 13 of 13 cycles pass):
  - the client pulled twice, so there were two server stage sets (r8, r9);
  - r8 was resolved and merged (merged set 11), 11 was sent to the server;
  - while that was on its way `handleConflict local 11 remote 9` started a second round over the same merged set, and the answer of the first commit ran on a callback thread at the same time;
  - the set was sent twice, the second got `TooOldVersionException`.
  Making this right means one place that decides what the client does next (pull, resolve, commit) and does it one at a time. That is a change of the sync cycle, not of the conflict code. The other 11 tests of the two classes passed in 4 of 4 cold cycles; in the full build the 8th test above failed once, so it is disabled as well.

The open problem behind the seven disabled tests is described in `CLIENT_SYNC_RACE.md` (what was seen, probable causes, solutions worth considering, how to test it deterministically). It is meant to be solved in its own session.

## Wastebin: a binned file is always found again

Binning a file the sync wants gone is fine, it is taken back out when it is wanted again (`restoreFsFiles`, `findFileByHash`). A trace of the client sync race showed the client's own file binned, restored and binned again, and then not found by anybody: the file lay in the bin as `<hash>.null`. Test: `WastebinTest` (5) works on the bin of a synced, stopped client; 4 failed before, the one that bins and restores at the same time fails 3 of 3 without the lock in `restoreFsFiles`.

Found and fixed:
- `WasteDao.fsToWaste` filled the bin entry from the fs table and swallowed a failed insert (`waste.inode` is `NOT NULL UNIQUE`; an fs entry that came from the server has no inode of this disk, a stale one may have the inode of a file in the bin). The file was moved anyway, to `<hash>.null`, where no lookup finds it. The entry is made from what the disk says (inode, time, size; the hash of the fs table only if the fs entry matches the disk), a stale entry with the same inode goes first, a failed insert is not swallowed.
- `restoreFsFiles` set `synced` but left inode and time of the fs entry as they came from the server, so the next decision about the file worked on wrong values. The fs entry gets what the disk says. A restore does not replace something that is in the way.
- Bin and restore run on two threads (the sync in `commitStage`, the transfer manager). One lock in `Wastebin` makes "move the file and write the row" one step for binning, restoring, `rm` and lookups; the restore reads its fs entries again under it. Taken after the lock on the fs table, never the other way round.
- `FileDistributor.moveBlocking` ignored a failed rename. It throws when the file is not at the target afterwards or still at the source (also for the Android distributors, which override `moveFile`). Inside `commitStage` that makes the step fail instead of recording what is not on the disk.
- The branch "a bin entry with this inode exists but the file changed: do not bin" is gone; it left the file in place and deleted its fs entry anyway.

`ConflictSyncTest` passed 6 of 6 cold cycles afterwards (before: 3 of 6 failed, twice). The race itself is still there (duplicate sends, parallel pulls); it no longer loses the file. The plan for the race: `SYNC_SESSION_PLAN.md`.

## Shorter waits in the integration tests

The two integration suites (`:filesync:integrationTest`, `:auth:integrationTest`) took 7 min 44 s; now 5 min 14 s, with the same tests. Measured first: a cold run boots two instances, pairs and syncs in 1 to 3 s, the slowest single step that passes takes about 7 s. The tests run one after the other, so the wall time is the sum of the tests.

- **Waiting for something that should happen**: 10 s (`TwoInstances.TIMEOUT_SECONDS`, was 30, and literal 15 to 60 s), big transfers 20 s (were 60 and 90). `@Timeout` 30 s per test, 60 s for the transfer classes (were 1 to 5 min). A failure shows within seconds instead of minutes.
- **No idle wait where there is an event**: refused connections and isolated connections wait for the promise to fail (were `assertFalse(await(5 to 8 s))`); `TwoInstances.pairAgain` returns when connecting settled (it waited 10 s for a registration that a rejection never brings); the late user in `RegistrationTest` is waited for (`RecordingHandler.decided`, was a 3.5 s sleep).
- **"Nothing happens" checks** are sized by what they watch: 500 ms after a settled pairing or close over loopback (were 1 to 1.5 s), 2 s for a boot that must not settle (a healthy one takes a few hundred ms, was 6 s), 300 ms after the server answered a refused partner.
- **Setup**: one key pair for the 130 filler certificates of `isolatedConnectionWorksWithLargeCertificateIds` (25 s to 7 s).
- **Production timers, settable for tests, defaults unchanged** (author's decision): `FileWatcher.quietRepetitions/quietPeriodMs` (quiet time before indexing, about 3 s), `HashAvailTimer.quietRepetitions/quietPeriodMs` (about 10 s before the server tells about available files), `IndexerRunnable.marginMs` (2 s the start looks back). `FileSyncInstances` sets 300 ms, 300 ms and 200 ms; `SyncedStateTest` waits a few quiet periods (was 15 s), `IncrementalBootTest` margin plus rounding (was 3.5 s).

What is left is structural: every test boots two instances (1 to 2 s). Faster would be running test classes in parallel (each uses its own ports and directories) or sharing instances within a class.

## Merging local stage sets

The indexer makes one stage set per run; before a sync they are merged into one (first step of a sync session, see `SYNC_SESSION_PLAN.md`). The merge was a private method of `ClientSyncHandler` (two sets at a time, another `CommitJob` for more) with three weak tests (`SingleServiceTest`: nearly identical sets). It is `LocalStageSetMerger` now. Test: `LocalStageSetMergerTest` (9, database only); 6 failed with the old merge, moved unchanged into the class first.

Found and fixed:
- **A deletion in the newer set dropped unrelated older changes.** `ConflictDao.flagLocalChildrenDeletedInRemote` compared `path LIKE path||'%'` with the path of the *parent* of the deleted entry: deleting `/a.txt` dropped every older change in `/`, deleting `/d/` every older change next to it. Those changes were never sent.
- **New directories lost their tree.** For an entry only the newer set has, the parent was looked up with `Stage.toIFile`, which gives the entry's own path (with the name twice); the entry was inserted without a parent.
- **The result counted as newer than a set made meanwhile.** It was a new stage set, and `getStagedStageSetsFromFS` sorted by `created`, which has seconds only. The older set is merged *into* the newer one now (the result keeps its id), and the sets are sorted by id, the order they were made in.
- All sets are merged at once, oldest first.

Not changed: the content hash of a directory is the one of its newest entry, not computed again from what the merge put in it (as before).

Fix (same day): the short timers were set when `FileSyncInstances` was loaded, so the dump tests got them too (`DumpFlowTest` writes its files with `FileSyncInstances.writeFile`). With the watcher's quiet time at 300 ms, the three tests after `nothingOfTheTargetComesBackToTheSource` got nothing at the target, every time (with only the margin or only the `HashAvailTimer` short they pass). The timers are set when instances are made now. ~~**Open**: a dump whose watcher indexes after 300 ms loses what the source has~~: wrong description, the content arrived; the entries of the other names for it stayed "not synced". Solved, see "File distribution: writes wait for a commit".

## Transfer: a file goes on the connection it was asked on

Seen in a trace of `ConflictSyncTest.differentFilesChangedOnBothSidesAreMerged` (more often with the sync sessions, where both sides fetch at the same time): client and server each opened an isolated connection to the other to fetch a file. The server sent the client's file on *its own* connection (`handleSending` took any open connection to the partner, `getIsolatedProcess`), then ended that connection when its own fetch was done (`TManager.onTransferFromServiceFinished` stops the process). The file was cut off; the client waited on its own connection, which stayed open, and gave up only after the stall timeout (120 s).

Fixed: the request names the connection the asking side waits on (`FileTransferDetailSet.isolatedUuid`, set by `TransferFromServiceRunnable`), and the files go there (`MelFileSyncService.connectionToSendOn`). If that connection is gone, the request fails and the asking side asks again. Partners that do not name one get any connection, as before. Test: `FileSyncTransferTest.aFileGoesOnTheConnectionTheClientAskedOn` (with two connections open, the one asked on is chosen, ten times; a closed one is not replaced by another); 4 of 4 runs fail with the old choice. An end-to-end test that cuts the server's own connection could not force the case (which open connection either side reuses is not under the test's control).

## Client: one sync session at a time

Replaces the jobs that called each other (`execCommitJob`, `syncFromServer`, `syncToServerLocked`, the TooOld handler, `handleConflict`) by a sync session on a thread of its own, see `SYNC_SESSION_PLAN.md`. The service worker does not wait for anything of it; it handles messages and transfer requests as before.

- `CommitJob` and `SyncClientJob` (GUI, Android, tests, all triggers) only call `ClientSyncHandler.requestSession()`, which returns at once. Several requests make one session; a request during a session makes the next one. Requests before the service has booted are kept (they were dropped: `turnend down. required boot level not reached yet`), the session runs at `onBootLevel2Finished`.
- A session: merge the local stage sets into one (`LocalStageSetMerger`), pull (blocking: the `WaitLock` returned at once, it is gone), find conflicts, apply remembered answers, ask the user and wait for the answers (woken by the next `CommitJob`, looks again every second), merge, commit (blocking; the answer is handled on the session thread, the network threads only hand it over).
- **Nothing is applied before the server accepted the commit.** Accepted: the server's stage set is applied (without what the user decided against), then our result with the fs ids of the answer, then the local stage sets are gone. TooOld: the result and the server's set are thrown away, the local sets stay, the next session follows after 100 ms (doubling up to 5 s) and resolves again with the remembered answers. Failed: the same, and a retry as for a failed pull.
- Results get the status `result` (not `staged`): never the input of a session, deleted at the start of the next one if one is left over (the service stopped in the middle).
- Before the commit the result loses files the server has like that already (the user chose the server's version) and what is identical to the fs table. Only local changes and no news from the server: the local set is sent as it is, on top of the version we have.
- A session that applies the server's set takes wanted files out of the bin right away (`Wastebin.restoreFsFiles`).
- The conflict solver map is a `ConcurrentHashMap` (the GUI and tests read it from other threads).
- `InitialIndexConflictHelper.onDone` asked `handleConflict` with a stage set that is always empty (`check()` is never called): it asks for a session now.
- `SingleServiceTest` merged through a `CommitJob`; it calls the merger now.

Known limits: the pull still asks for everything since `lastSyncedVersion` (the version of the fs table, as before). A directory's content hash in the result is computed by `ConflictSolver.after` as before.

## Auth: no new connections while an instance shuts down

`MelAuthServiceImpl.shutDown()` stops the services (asynchronously) and then closes the sockets from a copy of the set. A connection a service started meanwhile (a pull, a transfer) was not in the copy and stayed open; in one process (the tests restart instances in the same JVM) the partner kept it as its connection to us, also against the connections of the next instance. Seen in the logs of the conflict tests that restart client and server ("a socket started while this instance shuts down"). Now: `connect` and `connectToService` refuse once the shutdown began, and a socket that starts after that is closed at once. It was not the cause of the failures that led there (see "Client: versions and parent ids from the answer"), but it happens.

## Transfer: a request waits for the connection it names; a restart resumes what it cut off

Two more transfer defects found while re-enabling the conflict tests:
- **The side that accepted an isolated connection registers it after it answered** (`MelAuthProcess`, on purpose: the service may use it at once). The partner can ask for files on it a moment before. Since "a file goes on the connection it was asked on" (`6d3ae12c`) such a request failed; the side asking then suspended the transfer. `MelService.whenIsolatedEstablished(uuid, timeout)` hands over the connection as soon as it is registered; `MelFileSyncService.connectionToSendOn` waits up to 5 s for it.
- **`commitStage` stops and starts the transfer manager every time it applied a stage set** (to clean up transfers that are not needed any more). The stop suspends the fetches that run, also while the partner fetches from us or we from it, and their retry is not scheduled while stopped (`retryLater` returns when stopped). `TManager.start()` sets suspended transfers back to "not started" (`TransferDao.resumeAllSuspended`). A partner that is really not reachable is tried once more per applied stage set, with the usual back-off after that.

## Client: versions and parent ids from the answer of a commit

Found with `ConflictDeleteTest.serverDeletesDirectoryWhileClientChangesAFileInItTheChangeWins` (failed 3 of 6 cold runs, after the sessions):
- **The server kept old versions for entries that existed before.** `SyncHandler.commitStage` gave new entries the version of the commit, but an entry that was there before kept the version of the stage, which is the old one from the client. A commit that only changed existing entries did not move the server's version, and a pull since the last version (another client) would not find the change. Every entry of an applied stage set gets its version now.
- **The client did not know the version its commit made.** The result was put in place with the old versions of its stages, so `lastSyncedVersion` stayed behind and the next pull brought our own commit back as news of the server, marked "not here yet" (the server waits for the content): the client binned its own file at the moment the server asked for it. `CommitAnswer.newVersion` says the version (servers that do not say: as before), the client applies the result as of it. (A first try with "the version it was based on, plus one" was wrong exactly because of the item above: TooOld for ever.)
- **An entry below a directory that got a new id kept the old parent id.** When the server made the directory new (it had deleted it, the user kept a file in it), the client deleted its stale row of the directory, and `commitStage` skipped the file because its parent was gone ("skip if parent was deleted"): the file never got into the fs table and could not be sent. The parent id comes from the answer as well now.

After this and the transfer fixes: the seven conflict tests that were disabled pass 6 of 6 cold cycles (with `SyncSessionTest`, 108 of 108 test runs).

## Sessions proven; the seven conflict tests are on again

- `SyncSessionTest` (2): the test server holds back its answers to commits (`RecordingServerService.holdCommits`/`releaseCommits`, counts `pullsReceived`/`commitsReceived`), so the window of the old race is as long as the test wants. While a commit waits: another change, `SyncClientJob`, `CommitJob`s and a request to the client's worker (answered at once), and no second pull or commit (before the sessions: 4 commits). TooOld while the user's answer waits for the server: the next session resolves again with the same answer, without asking (before the sessions the client did not even ask: it committed before it pulled).
- `ConflictDecisionsTest` (5, database only): an answer counts again only for the same two versions; a file changed again on either side, or a directory with other names in it, is asked again. With a key of path and name only, 3 fail.
- The seven `@Disabled` tests of `ConflictSyncTest` and `ConflictDeleteTest` are on again (and the author's check that answering every conflict of a question solves it). Cold cycles: 6 of 6.

## Server: client commits on a thread of their own

The plan said "one version worker" on the server. What was found: its version changes on three paths (its own index at boot, its live changes, commits of clients), and all three hold the lock of the fs table (`P.confine(fsDao)`, a write lock, bound to the lock object, not to a thread) while they check and change the version: they take turns already. One thread for all three would deadlock (the indexer holds the lock while it calls its listeners). What was left of the goal: client commits ran on the service worker, so messages and transfer requests waited while a commit was put in place. They run one after another on a thread of their own now (`ServerSyncHandler.queueCommit`, also for the dump target's `handleCommit`).

`SyncSessionTest.aCommitThatIsTooOldIsResolvedAgainWithTheAnswerGivenBefore` waits 20 s for its end state: several rounds (the commit that is too old, another session, its commit, files both ways) took up to 9.5 s against a wait of 10 s.

## File distribution: writes wait for a commit

Found by the dump tests with the short test timers (the "open" item of the timer fix): with the target's watcher at 300 ms, `whereReferencesAreHadEachNameIsKept` and `whereNoReferencesAreHadTheOtherNamesAreLeftOutAndNothingIsCopied` failed now and then (2 of 2 at 58fc0508, 1 of 2 at 8d234e6f): the content was there, the entries of its other names stayed "not synced", with the reference on the disk or after it had been left out.

Cause: `SyncHandler.commitStage` works on a copy of the fs table (`FsWriteDao.prepare`: the whole table into `fswrite`) and puts that copy in place of the table at its end (`FsWriteDao.commit`), holding the lock of the fs table. Whatever another thread writes into the fs table in between is undone. `FileDistributor.copyFile` (every place of a download but one, also in plain file sync: one download, the same content in several places) wrote its update ("synced", inode, time) and the deletion of a name that was left out under `P.confine(fsId)`, a lock on the id, which does not wait for a commit. With the watcher at 300 ms the target indexes the directories it just made while it places the files, and the commit of that stage set overlaps the distribution; with 3 s it comes after.

Fix: `copyFile` writes under the lock of the fs table, as the move of the last place did already; the copy itself is made without it. `updateFs` leaves an entry alone that is gone or wants other content by then. The bin's `deleteFsFile` deletes from both tables for the same reason (it runs inside commits).

Test: `DistributionDuringCommitTest` holds the lock and the copy of the table as a commit does while the distribution makes a copy; the entry must be synced afterwards. Fails with `P.confine(fsId)`. `DumpFlowTest` runs with the short timers now (`FileSyncInstances.useShortTimers()`): 5 of 5 runs green with the fix (about 48 s a run).

Noticed, not changed: `FileDistributor.stop()` sets `stopped = false`, so the `stop()` at the start of `commitStage` does nothing. Making it stop would leave tasks lying until the next job starts the distributor; with the lock it is not needed.
