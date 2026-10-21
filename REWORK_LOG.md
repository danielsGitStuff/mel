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

Known until step 6: `AuthHandshakeTest.pairedClientCanConnectAgainAfterTheServerWasSuspendedAndResumed` fails in about 2 of 8 class runs
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
