# Rework log (unattended run, branch rework2026)

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
