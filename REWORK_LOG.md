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
Decision taken alone: `suspend()`/`resume()` of `MelAuthService` and their test were deleted already here (step 10 item), because
the F15 change made them need extra restart code for something with no callers. Deleted `SocketTest` (did not compile with the
new listener; step 10 deletes it anyway).
Observed before step 2 (old code, in the first full run): `RegistrationTest.serverTrustsNobodyWhoRejectedBeforeItsUserAccepted` failed
once ("server never heard about the rejection") = the old lost-message-on-close bug.
Tests: `CloseEventTest` (pending request fails at once, `onIsolatedConnectionClosed` exactly once); enabled
`RegistrationCommitTest.theLastDecisionCountsAlthoughThePartnerHangsUpRightBehindIt`.

## Step 3: send lock
`MelSocket.send` and `MelAuthSocket.sendBlock` hold `sendLock`. Enabled `ConcurrentSendTest.longMessagesFromSeveralThreadsArriveWhole`.
