# Socket/process rework: implementation plan

Companion to `SOCKET_PROCESS_REWORK.md` (the brief: problems, decisions, wire format, state table, tests). The brief says
**what** we want; this file says **where and how**, class by class, plus what a code analysis on 2026-10-21 found on top.
Read the brief first. Nothing in here is implemented.

**For the session that does the work:** read the brief, then sections 1 and 3 here, then work through section 4 step by step
(checkboxes; tick them as you go and commit the ticks with the work).

Start state: branch `rework2026`, commit `05afb263` and later. `./gradlew :auth:test` (fast, ~12 s),
`./gradlew :auth:integrationTest` (~3 min). The `@Disabled` tests are the to-do list; each names what it waits for.

## 1. New findings (not in the brief before)

"Read" = found by reading the code, not reproduced by a test yet. Each one gets a test in the new session (section 5).

| # | Finding | Where | Severity | How found |
|---|---|---|---|---|
| F1 | **The client never checks which certificate the server presented.** `connectSSL(certId, ...)` sets `partnerCertificate` from the database by id, and `MelAuthProcess.authenticate` only falls back to the TLS session when that is null. So the client takes whoever answers at the partner's address for that partner, as long as TLS accepts its certificate (see F2). The isolated path's check `partnerCertificate.getId() == job.getCertificateId()` compares the id with itself. | `MelAuthSocket.connectSSL`, `MelAuthProcess.authenticate` | security | read |
| F2 | **TLS trusts every imported certificate, trusted or not.** `importCertificate` puts the certificate into the keystore right away (`storeCertInKeyStore`), and every `createSocket()` builds a fresh TLS context from that keystore. Certificates imported for a pairing that never completed (or was rejected after the import) are accepted as servers until the next restart. Together with F1: any device whose certificate was ever imported can pose as any partner it can intercept. | `CertificateManager.importCertificate`, `getSSLContext` | security | read |
| F3 | **`writeUTF` limits bytes, not characters.** `MAX_CHARS` (64509) counts characters; a piece with more than about 1000 non-ASCII characters (umlauts in file names, CJK) exceeds 65535 bytes, `writeUTF` throws, `send` logs and drops it. Earlier pieces of the same message are already on the wire, so the receiver's reassembly buffer is left with a fragment that corrupts the next message. Typed frames remove this. | `MelSocket.send` | data loss | read |
| F4 | **Close is reported several times, from different threads, and pending requests are not failed.** The reader calls `listener.onClose(42)` when it stops, `SocketWorker.onShutDown` calls `onClose(100)`, and `MelAuthSocket.onSocketClosed` calls `melAuthService.onSocketClosed` again. So `IMelService.onIsolatedConnectionClosed` and the isolated listeners may run twice. `MelProcess.onSocketClosed` is empty: requests waiting for an answer on a dead connection only fail by their 30 s timeout. | `MelSocket.runImpl`, `SocketWorker`, `MelAuthSocket`, `MelProcess` | correctness, latency | read |
| F5 | **Socket bookkeeping leaks.** `ConnectedEnvironment.connect(Certificate)` creates a `MelAuthSocket` only as a placeholder for its connect job; it is never started, but the `MelSocket` constructor already added it to `MelAuthServiceImpl.sockets`, and nothing removes it: one leaked object per connect by certificate. Plain `MelSocket`s (certificate delivery and retrieval) are never removed either (only `MelAuthSocket` reports its close to the service). `sockets` is a plain `HashSet` written from many threads. | `ConnectedEnvironment`, `MelSocket` constructor, `MelAuthServiceImpl` | leak, races | read |
| F6 | **`ConnectedEnvironment` has dead and wrong lookups.** `removeValidationProcess` removes from the "currently connecting" maps with the socket as key, but the keys are strings and ids: both removals do nothing. `addressValidateProcessMap` is keyed by `address:port`, and for incoming connections the port is **our** listening port: two partners on the same host overwrite each other. | `ConnectedEnvironment` | correctness | read |
| F7 | **`ConnectWorker` reuses one field for all its sockets.** `melAuthSocket` is overwritten by the register socket and again by the auth socket, so the register socket is never stopped (problem 6). `workWork` calls `shutDown()`, which stops `melAuthSocket`, when the connect job is already resolved: if the answer comes back before `workWork` returns, a fresh connection is closed (narrow window, not observed). After a registration, `secondJob` is queued but `workWork` then connects with the original `connectJob`, not `secondJob`. | `ConnectWorker` | correctness | read |
| F8 | **No timeouts on connect or read, and the certificate delivery serves one client at a time.** `socket.connect(addr)` without a timeout (`connectSSL`, `MelCertRetriever`) blocks for the OS default (minutes) on unreachable hosts and blocks other connects to that certificate meanwhile. No socket has `SO_TIMEOUT`. `MelAuthCertDelivery` reads the request with a blocking `readUTF` in its accept thread: **one silent TCP client stops all certificate deliveries** until it goes away. | `MelAuthSocket`, `MelCertRetriever`, `MelAuthCertDelivery` | availability | read |
| F9 | **Isolated connections: the peer picks the class, and the service keeps only one per key.** The server instantiates the class named in `IsolationDetails.processClass` (`Class.forName`, any `MelIsolatedProcess` with the right constructor). `MelService` keeps one isolated process per `(partner, partner service, class)` in `isolatedDeferredMap`; a second connection overwrites the entry, the first one keeps running untracked. That contradicts "isolated connections are exempt from the duplicate rule". | `MelAuthProcess` server side, `MelService.getIsolatedProcess` | design, security (minor) | read |
| F10 | **Every request starts two threads.** `MelRequest` creates a `WatchDogTimer` at construction, also for every incoming request that is only deserialized. `WatchDogTimer extends Timer` and also holds a `new Timer(...)`: two `java.util.Timer` threads per request, until garbage collected. A timer that was stopped cannot be restarted (`Timer.cancel()`), which `MelValidationProcess` tries for cached parts (`request.startTimeout()`). | `MelRequest`, `WatchDogTimer` | resources | read |
| F11 | `MelValidationProcess`'s cache maps (`cachedForSending`, ...) are plain `HashMap`s, written by `send()` from any thread and read by the worker. | `MelValidationProcess` | race | read |
| F12 | `MelSocket.BLOCK_SIZE` is used as a buffer size by the updater (`update/BinarySocket`) and the miniserver (`SendBinarySocket`), which have their own sockets. Changing the block/frame size must not change them by accident. | `update`, `miniserver` | coupling | read |
| F13 | `getAllowedServices(certId)` throws a `NullPointerException` when there is no connection to that certificate. | `MelAuthServiceImpl` | minor | read |
| F14 | Discovery (`connectAndCollect`) makes both instances connect to each other when they see each other: the simultaneous-connect case of the duplicate rule is the normal case on a LAN, not an edge case. | `MelAuthServiceImpl`, brotcaster | design input | read |
| F15 | `DeferredRunnable.stopped` is not `volatile`, and `run()` sets it back to `false`: a `stop()` that comes before the thread has started is lost and the runnable runs anyway. Every worker and socket inherits this. (`MelAuthServiceImpl.startUpCommunications` relies on the reset to restart a stopped worker.) | `auth/src/main/java/de/mel/DeferredRunnable.java` | race | read |
| F16 | `MelWorker` drops every queued job when it is stopped (the loop just ends). This is the mechanism behind "a close cuts off queued messages". **Decided: fine for `MelWorker` in general** (a deliberate stop ends the party). The bug is only that a *peer close* stops the socket worker; step 2 makes that an event at the end of the socket's own queue, so `MelWorker` stays as it is. | `MelWorker.runImpl` | correctness | read + tests |

Already in the brief, now confirmed by tests: the lost last message on a hang-up, and the pairing "flake" that is the same bug in
`MelCertRetriever`.

### Decisions on the findings (user, 2026-10-21)

- **F1/F2:** under all circumstances **both sides check that the other side is trusted**, by the certificate from the TLS
  session (hash -> trusted row). Untrusted -> close. Reaching a *different* trusted instance than intended (the IP now belongs
  to another of our partners) is fine: the connection belongs to whoever is really there and is kept in memory
  (`ConnectedEnvironment`) as a connection to **that** partner, never to the expected one. The `connect(X)` that reached Y fails
  with `WRONG_PARTNER`; the connection to Y may stay. **Database: only Y's last known address/port is updated** (what
  `propagateAuthentication` does today). No other update, no insert, no delete, and no registration: Y is trusted already.
  With this check on both sides, F2 is harmless: TLS may accept any imported certificate, trust is decided above it.
- **F3:** typed frames (`type 1 byte | length 4 bytes | payload`), as decided in the brief.
- **F4, F7, F8:** fix. `ConnectWorker` gets rewritten (it is needed: something has to run retrieve -> register -> auth), as a
  straight sequence.
- **F5, F6, F10:** bugs, fix.
- **F9 / isolated connections:** there may be **several** isolated connections between the same two services (file transfer).
  No duplicate rule for them, and nothing may assume there is only one: `MelService` tracks them by `isolatedUuid`.
- **Register trust:** stays as today: the fetched certificate is imported (untrusted) into database and keystore, the register
  socket connects with it. Trusting is the users' decision; **the commit is setting `trusted`**, nothing else.
- **`shutDownCommunications`:** also closes established and isolated connections (reason `SHUTDOWN`). Today it only stops
  listening and discovery and leaves open connections running, which is not what "communications off" suggests.
- **Fingerprints:** the GUIs already show certificate hashes and keys; comparing them is the protection against a certificate
  swapped on the unencrypted retrieval. A short comparison string (words or emoji derived from both hashes) would make users
  actually compare. Later, not part of this task.

## 2. Target in one paragraph

One `MelAuthSocket` per TCP connection. A reader thread reads typed frames (`type | length | payload`, max 1 MiB) and puts
events into the socket's queue; one worker consumes them and is the only one that changes the state
`NEW -> REGISTERING | AUTHENTICATING -> ESTABLISHED | ISOLATED -> CLOSED(reason)`. The state is an immutable object in a
volatile field or an `AtomicReference` and carries its handler and, from AUTHENTICATING on, the partner certificate **taken
from the TLS session** (both sides). Every event either fits the state or closes the connection with a reason. End of stream,
local close, timeouts and "lost the duplicate rule" are events in the same queue, so a close never overtakes a message
received before it, and CLOSED is entered exactly once. `send` holds the only lock. Partners are identified by certificate
only.

## 3. Class by class

### Transport

**`MelSocket`** (289 lines) -> the framed transport.
- Today: `writeUTF` text with `MAX_CHARS` chunking and a reassembly buffer; after `MODE_ISOLATE`, raw fixed-size blocks with
  the reader waiting on `queueLock` until each block is handled; `isIsolated`/`allowIsolation` are plain fields written by other
  threads; `send` has no lock; `sendBlock` lives in `MelAuthSocket` and has none either.
- Change: reader loop = read 5 header bytes, check `length <= MAX_FRAME`, read the payload, enqueue
  `Frame(type, bytes)`. On EOF or an I/O error enqueue `PeerClosed`/`Failed` and end the loop; never call listeners from the reader
  thread. One `send(type, bytes)` under a lock (`ReentrantLock` or `synchronized` on the output stream), used for messages and
  blocks alike. Delete `MAX_CHARS`, `TOO_LONG_APPENDIX`, `msgBuffer`, `BLOCK_SIZE` mode, `queueLock`, `isIsolated`,
  `allowIsolation`.
- Backpressure for blocks: today one block at a time (the reader waits). Keep that property with a bounded queue (the reader
  blocks on `put` when the worker is behind), otherwise a fast sender fills the receiver's memory.
- Socket options: connect timeout (e.g. 10 s), TLS handshake timeout, `SO_TIMEOUT` only where a state has a timeout (NEW,
  AUTHENTICATING, REGISTERING); ESTABLISHED and ISOLATED read without timeout (no keep-alive for now, decided).
- F12: give the updater and the miniserver their own buffer constant before touching `BLOCK_SIZE`.

**`SocketWorker`** (51 lines): the single consumer. Events: `Frame`, `PeerClosed`, `LocalClose(reason)`, `Timeout`,
`DuplicateLost`. Its shutdown must not call `onClose` itself any more (F4); CLOSED is entered by handling a close event.

**`MelAuthSocket`** (230 lines) -> holds the state.
- Today: `process` field (non-volatile, set by `MelProcess`'s constructor and lazily in `onMessage`), blind casts in
  `onIsolated`/`onBlockReceived`, `partnerCertificate` set from the database (F1), `getTrustedPartnerCertificate` by hash on the
  server only, `disconnect()` closes the raw socket behind everybody's back.
- Change: `AtomicReference<SocketState> state`; `onEvent` dispatches to the current state, which returns the next state and
  actions (send, close, notify). Partner certificate: from `getSession().getPeerCertificates()[0]`, on both sides, looked up by
  hash among the trusted rows; not trusted -> close `UNTRUSTED` (F1). If it is trusted but not the one the client meant to
  reach, the connection belongs to the partner actually there (in memory; in the database only its address/port is updated);
  the original connect job fails with `WRONG_PARTNER`. `getProcess()`
  callers (`ConnectedEnvironment`, tests) move to `getState()`. `disconnect()`/`stop()` become `close(reason)` = enqueue
  `LocalClose`.

**New: `SocketState`** (sealed type / small class hierarchy) `New`, `Registering`, `Authenticating`, `Established`,
`Isolated`, `Closed(reason, remote)`, and a pure transition function `(state, event) -> (state, actions)` without sockets, so
the transition table from the brief gets a fast unit test (`:auth:test`, not integration).

### Processes

**`MelProcess`** (96 lines): base of the state handlers. Remove `melAuthSocket.setProcess(this)` from the constructor (the
socket decides the state). `handleAnswer` is not `synchronized` any more (one consumer). New: on CLOSED, reject every pending
request in `requestMap` at once with the close reason (F4).

**`MelAuthProcess`** (250 lines) -> the AUTHENTICATING handler.
- Server: certificate from the TLS session (already so), trusted? -> kind of connection -> **duplicate rule before the
  transition** (via `ConnectedEnvironment`, atomically) -> answer -> ESTABLISHED or ISOLATED, or close with `DUPLICATE` /
  `UNTRUSTED` / `NO_ACCESS`. No `new MelValidationProcess(...)` before the decision (problem 2).
- Client: before anything else, the server's TLS certificate must be trusted (F1); then on `AuthResponse(ok)` the same
  duplicate rule, then the state, kept as a connection to the certificate actually seen.
- Isolation: the service creates the isolated process (e.g. `IMelService.createIsolatedProcess(details)`), not
  `Class.forName` on a name from the peer (F9).
- `propagateAuthentication` (address update + `connectionAuthenticated` to services) stays, after the transition.

**`MelValidationProcess`** (435 lines) -> the ESTABLISHED handler. Keep `request`/`message`/`getPartnerCertificate`, they are
the API other modules use. Remove `synchronized` from `onMessageReceived` (problem 5). Cache maps -> `ConcurrentHashMap` (F11).
Service errors are answered, the connection stays (decided). Unknown/unfitting messages -> protocol violation.

**`MelRegisterProcess`** (219 lines) -> the REGISTERING handler, rewritten.
- Messages: `RegisterRequest`, `Decision`, `Final` (brief). The commit is setting `trusted` in a single statement: S before it
  sends `Final` (INSERT of a trusted row, or UPDATE of a known one), C on `Final` (UPDATE of the row imported by the retriever),
  then C closes. A rejection on C deletes the untrusted imported row, as today, but never a row that was trusted before.
- Partner certificate = TLS client certificate of the register socket, not the one in the payload (decided).
- Known certificate: `UPDATE` the row (name, address, ports, trusted) instead of `importCertificate`; a rejection touches
  nothing (decided). `IRegisterHandler.acceptCertificate` needs to tell the user "known already": add a parameter or a field on
  the passed `Certificate` (callers: `RegisterHandlerFX`, `AndroidRegHandler`, tests).
- The `DefaultDeferredManager().when(confirmed, accepted)` construction goes; with one consumer a plain "both decisions known?"
  check after each event is enough. (This also removes two `when(...)` sites from the later JDeferred port.)
- C connects the register socket with S's certificate imported into database and keystore by `MelCertRetriever`, as today
  (decided). That is TLS-level acceptance only; trust is the `trusted` flag.

**`MelIsolatedProcess`** (123 lines) / **`MelIsolatedFileProcess`** (295 lines) -> the ISOLATED handler.
- `sendIsolate`/`onIsolated`/`isolatedPromise`/`MODE_ISOLATE` go: ISOLATED is reached by the auth answer.
- Blocks: `sendBlock(record)` = one binary frame per record, sized as needed (no 4 MiB padding, no 0 terminator, no packing of
  several files). `transfer()` and `onBlockReceived` get simpler; the record layout `[T|t|E][stream][offset][length][data]` stays
  the file process's business. Fix the javadoc (8 vs 4 byte length).
- `onSocketClosed` -> called exactly once on CLOSED (F4).
- Several isolated connections between the same two services are allowed (decided). `MelService.isolatedDeferredMap` keys by
  `partner.service.class` today and overwrites; key the established ones by `isolatedUuid`. `getIsolatedProcess` may still
  hand out an open one for reuse, but closing one must not drop the others.
- Users: `filesync` (`TManager`, `TransferFromServiceRunnable`, `MelFileSyncService`), `android` `DirectoryChooserDialog`,
  `contacts` `ContactsService`: they use `getIsolatedProcess`, the listener and `onIsolatedConnectionEstablished/Closed`;
  keep those signatures.

### Around the sockets

**`ConnectedEnvironment`** (302 lines) -> the registry and the duplicate rule.
- One map `partner certificate id -> Connection` (the established one) plus "connecting" per certificate; drop the address map
  (F6) or keep it only as a hint for "is somebody at this address", never as identity.
- `decideDuplicate(newSocket)` under one lock or with compare-and-set: different initiators -> lower certificate hash initiator
  wins; same initiator -> newer wins; the loser gets `DuplicateLost` in its queue. Normal connections only: isolated connections
  may be several (decided).
- `connect(Certificate)` must not create a placeholder socket (F5); keep the job in the "connecting" map directly.
- `onSocketClosed` is called once, by the CLOSED transition.

**`ConnectWorker`** (241 lines) -> a straight sequence per job: (retrieve certificate) -> register socket -> wait for its
CLOSED -> auth socket -> done. One local variable per socket, no shared field (F7). Connect timeouts (F8). No `shutDown()` that
stops a socket it handed over.
- Keep today's order for "pair with this address": **try a normal connection (auth) first**, register only if that fails. A
  pairing sent by mistake to an instance that is already a trusted partner on both sides then just connects: no registration,
  only its address/port updated, and the caller learns "already paired with X" (decided 2026-10-21).
- The certificate retrieval before a registration must reuse an existing row for that certificate instead of importing it again
  (today: `UNIQUE (certificate)` fails). That is the case when only one side deleted the other.

**`MelAuthSocketOpener`** (67 lines): small. Handshake timeout; the accept loop hands the socket to a new `MelAuthSocket` as
today.

**`MelCertRetriever`** (152) / **`MelAuthCertDelivery`** (168): outside the state model (decided), but they ride on `MelSocket`
and suffer from the close bug (the pairing flake). Minimum: the new framed `MelSocket` with close-as-event fixes the retriever.
The delivery must not read in its accept thread: one short-lived thread or task per client plus a read timeout (F8). Out of
scope but worth a sentence to the user: certificates travel in plain text before pairing, so the users' accept is the only
protection against a substituted certificate (showing a fingerprint on both screens would close that).

**`MelAuthServiceImpl`** (678 lines): `sockets` -> concurrent set or gone, closed sockets always removed (F5);
`getAllowedServices` without NPE (F13); delete `suspend()`/`resume()` and their test (problem 8, no callers);
`shutDownCommunications` (Android power saving) also closes all connections with `SHUTDOWN` (decided).

**`MelRequest` / `WatchDogTimer`**: replace the per-request timers with one `ScheduledExecutorService` per instance; create the
timeout only for requests we send, not for deserialized ones (F10).

**Database**: no schema change needed for the decisions. Untrusted rows exist between "certificate fetched" and the commit (and
for rejected pairings until they are cleaned up), as today.

### Tests (helpers exist)

`RawPeer` changes with the frame format (one place). `TwoInstances.openNormalConnections`/`environmentOf` use reflection; replace
with the new `getState()`/registry API. Remove the pairing retries (`RegistrationTest.startPairing`, `AuthHandshakeTest.pair`,
`TwoInstances.pair`) once close-as-event is in. Delete `SocketTest`, `LargeJson`, and the suspend/resume test with the code.

## 4. Work breakdown

For the session that does the work. Steps in this order; each one is a commit (or a few) on `rework2026`.

### Rules for every step

- Before: read the step, write or enable its tests first, see them fail for the expected reason.
- Done when: `./gradlew :auth:test` and `./gradlew :auth:integrationTest` are green, the step's `@Disabled` tests are enabled
  and pass, nothing else got disabled. From step 4 on also `./gradlew build -x test -x integrationTest` (all modules compile,
  including `android`, `filesync`, `contacts`, `filedump`, `authfx`, `miniserver`).
- Integration tests that are about timing (duplicates, hang-up) run 5 times in a row before the step counts as done
  (`--rerun` in a loop); a flake is a failure.
- After: tick the step here, update the brief's "Problems found"/test table if something changed, commit (identity and trailer:
  see the brief's practical notes). Ask the user before deviating from a decision in the brief or in section 1.
- Language: Java, as the surrounding code. Pattern-matching `instanceof` is already used in `auth` and builds for Android;
  check `android` still assembles before relying on newer features (records, sealed types).
- Keep the public API other modules use (section 3, "Users") unless a step says otherwise.
- No new JDeferred code: rewritten code uses `fun.with.promise` (or plain state); the public API stays JDeferred behind the
  temporary `PromiseAdapters` until P4 of `REWORK_RUNBOOK.md`.

### Step 1: independent bugs (small)

- [x] F15: `DeferredRunnable.stopped` -> `volatile`; `run()` must not resurrect a runnable that was stopped before it started
      (today such a worker or socket runs until somebody stops it again, possibly forever).
      Keep restartability explicit (`startUpCommunications` restarts the worker): e.g. a `restart()` that resets, used there.
      Test (unit): `stop()` before `run()` -> `runImpl` is not entered.
- [x] F12: `update/BinarySocket` and `miniserver/.../SendBinarySocket` get their own buffer constant (same value as today).
- [x] F10: one `ScheduledExecutorService` per `MelAuthService` (daemon threads) for request timeouts; `MelRequest` creates its
      timeout only when it is sent (`MelProcess.send`), not in the constructor; restart = cancel + schedule. Remove
      `WatchDogTimer` from `MelRequest` (other users of `WatchDogTimer` stay). Test (unit): constructing/deserializing 1000
      requests starts no threads (compare `Thread.activeCount()` or a thread name prefix).
- [x] F13: `getAllowedServices(certId)` without a connection -> rejected promise, not an NPE.
- [x] F5 (part): `MelAuthServiceImpl.sockets` -> `ConcurrentHashMap.newKeySet()`; every `MelSocket` removes itself on close (not
      only `MelAuthSocket`); `ConnectedEnvironment.connect(Certificate)` keeps the connect job without a placeholder socket.
      `TwoInstances.openNormalConnections` loses its CME retry loop.
- [x] F6 (part): fix the two no-op removals in `ConnectedEnvironment.removeValidationProcess`.

### Step 2: close is an event, exactly once (F4, F16)

- [x] Replace `SocketWorker extends MelWorker` with a socket-owned loop on a `BlockingQueue<SocketEvent>`:
      `MessageReceived(String)`, `BlockReceived(byte[])` (until step 4), `PeerClosed(Exception cause)`, `CloseRequested(CloseReason)`.
      The worker drains events in order; a close event ends the loop **after** the events before it are handled.
      Bounded queue (e.g. 64) so the reader blocks when the worker lags (replaces `queueLock` for blocks).
- [x] New `enum CloseReason { PEER_CLOSED, SHUTDOWN, PROTOCOL_VIOLATION, DUPLICATE, UNTRUSTED, WRONG_PARTNER, REFUSED,
      NO_ACCESS, REJECTED, REGISTERED, TIMEOUT, IO_ERROR }` (extend as needed).
- [x] The reader thread never calls listeners; on EOF/error it enqueues `PeerClosed` and ends.
- [x] `stop()`, `disconnect()`, `removeThyself()`, `ConnectWorker.shutDown`, `refuse` -> `close(reason)` = enqueue
      `CloseRequested`. Only the worker closes the Java socket.
- [x] Exactly one close callback: `MelSocketListener.onClose(reason, remote)` once, from the worker. Remove the
      `onClose(42, ...)`/`onClose(100, ...)` paths and the duplicate `melAuthService.onSocketClosed` call.
- [x] `MelProcess.onSocketClosed`: reject every pending request (`requestMap`) at once with the close reason.
- [x] `MelCertRetriever` and `MelAuthCertDelivery` use the same `MelSocket`, so they get the fix too.
- Enable: `RegistrationCommitTest.theLastDecisionCountsAlthoughThePartnerHangsUpRightBehindIt`.
- New tests: a pending request fails right after the partner closes (not after 30 s); `onIsolatedConnectionClosed` called
  exactly once.
- Then remove the pairing retries (`RegistrationTest.startPairing`, `AuthHandshakeTest.pair`, `TwoInstances.pair`) and run the
  integration tests 5 times: the old "flake" must be gone.

### Step 3: send lock (problem 4)

- [x] One lock around everything that writes to the stream (`send`, `sendBlock`), e.g. `synchronized (out)` or a
      `ReentrantLock` field. Still the old chunking; frames come next.
- Enable: `ConcurrentSendTest.longMessagesFromSeveralThreadsArriveWhole`.

### Step 4: typed frames (F3)

- [ ] `FrameCodec` (new, `de.mel.auth.socket`): `write(out, type, bytes)` and `read(in) -> Frame(type, bytes)`; header
      `type (1) | length (4, big endian, DataOutputStream.writeInt)`; `MAX_FRAME = 1 MiB` (constant); length < 0 or > MAX ->
      `ProtocolViolation` before allocating. Types: `MESSAGE = 1` (UTF-8 JSON), `BLOCK = 2`. Unit tests with
      `PipedInput/OutputStream` or byte arrays: round trip, empty payload, exactly MAX, MAX+1, negative length, truncated stream.
- [ ] `MelSocket`: reader = `FrameCodec.read` loop, `send(String)` = MESSAGE frame (UTF-8), `sendBlock(byte[])` = BLOCK frame.
      Delete `MAX_CHARS`, `TOO_LONG_APPENDIX`, `msgBuffer`, the `MODE_ISOLATE` handling in the reader, `isIsolated`,
      `allowIsolation`, `BLOCK_SIZE`-sized reads. A BLOCK frame on a socket whose state does not take blocks -> step 5; until
      then hand it to the listener as today.
- [ ] Isolation handshake for now: keep `MODE_ISOLATE` as a normal MESSAGE that the process handles (the echo goes away in
      step 8). `MelIsolatedFileProcess.transfer()` keeps its packing for now but sends blocks via `sendBlock` (one frame each).
- [ ] `RawPeer`: `sendText`/`receive` via frames, `sendBlock` = BLOCK frame, `sendUnparsable` = MESSAGE frame with junk;
      add `sendRawHeader(type, length)` for the max-size test.
- [ ] Certificate delivery/retrieval: same framing on both ends (`MelAuthCertDelivery` reads its request with `FrameCodec`).
- New tests: long message with many non-ASCII characters arrives whole; a header announcing MAX+1 closes the connection.
- Done also: `filesync` transfer tests still pass (`./gradlew :filesync:test :filesync:integrationTest` if tagged, otherwise
  `:filesync:test`).

### Step 5: the state type (problems 1, 3, 5; the state table)

- [ ] `SocketState` (new): `New`, `Registering(MelRegisterProcess)`, `Authenticating(MelAuthProcess)`,
      `Established(MelValidationProcess, Certificate partner)`, `Isolated(MelIsolatedProcess, Certificate partner)`,
      `Closed(CloseReason, boolean remote)`. Immutable. `MelAuthSocket` holds it in an `AtomicReference`; only the worker
      sets it; other threads read it or request a close.
- [ ] `Transitions` (new, pure, no I/O): `(state, event, role) -> Result(next state, actions)`, actions like `Send(entity)`,
      `Close(reason)`, `Notify...`. The table is the one in the brief. **Unit test it exhaustively** (every state x every
      message kind -> expected next state or `PROTOCOL_VIOLATION`); this is the fast replacement for most socket-level
      violation tests.
- [ ] `MelAuthSocket.onMessage` dispatches through `Transitions`; remove the lazy process creation, the `process` field,
      `setProcess`, the blind casts in `onIsolated`/`onBlockReceived`; `getProcess()` callers move to `getState()`.
      `MelProcess` constructor no longer calls `setProcess`.
- [ ] Protocol violation = send `ProtocolError(reason)` (new small `SerializableEntity`) then close with
      `PROTOCOL_VIOLATION`. Unparsable JSON is a violation too.
- [ ] Remove `synchronized` from `MelValidationProcess.onMessageReceived` and `MelProcess.handleAnswer`; cache maps ->
      `ConcurrentHashMap` (F11).
- [ ] Expose the close reason for tests: e.g. `MelAuthSocket.getState()` returning `Closed(reason, remote)` after the end, and
      a listener/`CompletableFuture<Closed>` `whenClosed()`. Replace the reflection in `TwoInstances` with it.
- Enable: all 7 `ProtocolViolationTest`s; make each also assert the reason (`RawPeer` can read the `ProtocolError` before the
  hang-up). Consider moving most cases to the `Transitions` unit test and keeping 2-3 at socket level.

### Step 6: authentication and duplicates (F1, problem 2, F14)

- [ ] Both sides: partner = certificate from the TLS session, looked up by sha256 among **trusted** rows
      (`getTrustedCertificateByHash`). Not trusted -> `UNTRUSTED`. Client: if it is trusted but not the expected one ->
      connection kept as one to that partner, the original connect job rejected with `WRONG_PARTNER`. Database: only the
      partner's address/port (`propagateAuthentication`), nothing else.
- [ ] `MelAuthSocket.connectSSL` no longer sets `partnerCertificate` from the id; `getTrustedPartnerCertificate` is the only
      source, on both sides.
- [ ] `ConnectedEnvironment`: one `Map<Long, MelAuthSocket>` of established normal connections by partner id plus
      "connecting" by partner id; `decideDuplicate(socket)` under one lock: different initiators -> the connection initiated by
      the side with the lower certificate hash wins; same initiator -> the newer wins; the loser gets
      `CloseRequested(DUPLICATE)`. Called on S when the `AuthRequest` arrives, on C when `AuthResponse(ok)` arrives (brief).
      Drop `addressValidateProcessMap` as identity (F6); `connect(address, ...)` checks "connected to the certificate at this
      address" via the certificate row, not via the map.
- [ ] `MelAuthProcess`: no `new MelValidationProcess(...)` before the decision. Client side `if (job instanceof ConnectJob)`
      branch: register only after `decideDuplicate` said "keep".
- New tests first (section 5): untrusted certificate answers -> closed, nothing stored; a different trusted partner answers ->
  counts as that partner, `WRONG_PARTNER`, only its address changed (compare all certificate rows).
- Enable: `DuplicateConnectionTest.bothSidesConnectAtTheSameMoment` (5 repetitions) and
  `reconnectWhileThePartnerStillHoldsTheOldConnection`.

### Step 7: registration (problem 6, the commit, re-registration)

- [ ] New messages (`de.mel.auth.data` or a `reg` subpackage): `RegisterRequest(name, port, deliveryPort)` (no certificate
      field: the TLS client certificate is the certificate), `RegisterDecision(boolean)`, `RegisterFinal(boolean)`.
      Remove `MelRegisterConfirm` and the `certificate` field on `MelRequest` if nothing else uses it.
- [ ] `MelRegisterProcess` rewritten as the REGISTERING handler: S persists (single statement setting `trusted`: INSERT of a
      trusted row, or UPDATE of a known row) **before** sending `Final(true)`; C persists on `Final(true)` (UPDATE of the row the
      retriever imported, or of its known row), then closes with `REGISTERED`. A rejection persists nothing; C deletes the
      untrusted imported row as today, never a row that was trusted before. No `DefaultDeferredManager().when(...)`.
- [ ] Register timeout: REGISTERING closes with `TIMEOUT` after 5 min; make it a setting so tests can use 1 s.
- [ ] `IRegisterHandler.acceptCertificate`: tell the user when the partner is known already (extra parameter or flag);
      adapt `RegisterHandlerFX`, `AndroidRegHandler`, the test handlers.
- [ ] The handler callbacks (`onRemoteAccepted`, `onLocallyAccepted`, `onRegistrationCompleted`, rejections) keep their
      meaning; `RegistrationTest` pins them.
- Enable: `RegistrationCommitTest.aKnownPartnerCanRegisterAgainAndKeepsItsAccess`; `serverAlreadyTrustsTheClientWhenTheClientIsDone`
  must now hold by construction (run 5 times).
- New tests: one side deleted the other, both directions; pairing by mistake with an already trusted partner -> no
  registration, nobody asked, only address/port changed; REGISTERING timeout with a short setting.

### Step 8: isolated connections (F9)

- [ ] ISOLATED is reached by the auth answer: S switches after sending `AuthResponse(ok)`, C on receiving it. Remove
      `sendIsolate`, `onIsolated`, `isolatedPromise`, `MODE_ISOLATE` (and `MelStrings.msg.MODE_ISOLATE`).
- [ ] In ISOLATED only BLOCK frames are allowed; anything else -> `PROTOCOL_VIOLATION`.
- [ ] `MelIsolatedFileProcess.transfer()`: one record per BLOCK frame, sized as needed (no padding, no 0 terminator, no packing);
      `onBlockReceived` reads exactly one record. Fix the javadoc (8 vs 4 byte length).
- [ ] The service creates the isolated process: `IMelService.createIsolatedProcess(IsolationDetails, MelAuthSocket, partnerId)`
      or a factory registered per process class; no `Class.forName` on a name from the peer. Adapt `filesync`, `contacts`,
      `android` (`DirectoryChooserDialog`), `TestIsolatedProcess`/`IsolationRecordingService`.
- [ ] `MelService`: track established isolated processes by `isolatedUuid`; closing one removes only that one;
      `getIsolatedProcess` may reuse an open one.
- New test: two isolated connections between the same two services at once, both usable; closing one leaves the other.
- Done also: `filesync` transfer tests pass.

### Step 9: connecting (F7, F8)

- [ ] `ConnectWorker` rewritten as a straight sequence per job with one local variable per socket: auth first; if the partner
      is unknown and registration is allowed: retrieve certificate (reuse an existing row) -> register socket -> wait for its
      CLOSED(`REGISTERED`) -> auth socket. Pairing by mistake with an already trusted partner: the first auth succeeds, report
      "already paired".
- [ ] Timeouts: connect 10 s (`connectSSL`, `MelCertRetriever`), TLS handshake, NEW 30 s, AUTHENTICATING 30 s (settings, short
      in tests).
- [ ] `MelAuthCertDelivery`: no reading in the accept thread; one short task per client with a read timeout.
- [ ] `shutDownCommunications` closes all connections with `SHUTDOWN` (decided).
- New tests: a silent TCP client on the delivery port does not stop a pairing; connecting to an unroutable address fails within
  the connect timeout (use a non-routable address such as `10.255.255.1`; skip if the CI network behaves differently).

### Step 10: cleanup

- [x] NOT done on purpose: `suspend()`/`resume()` have a caller (`AndroidPowerManager`), they and their test stay.
- [ ] Delete `auth/src/test/.../socket/SocketTest.java`, `LargeJson.java`; `LotsOfTests` if it only holds commented tests.
- [ ] Remove leftovers: `MelSocket.BLOCK_SIZE`, `MelStrings.msg.MODE_ISOLATE`, `MelRegisterConfirm`, unused exceptions.
- [ ] Brief: mark the problems fixed, move the open items to the JDeferred task; memory: mark the rework done.

Then P3/P4 of `REWORK_RUNBOOK.md` (JDeferred tests and port), in the same unattended run. During these steps: new code uses
`fun.with.promise`, the public API stays JDeferred behind `PromiseAdapters` until P4 (runbook).

## 5. Tests to add (also listed in the steps)

- **F1:** the client connects to partner X's address and an *untrusted* certificate answers (e.g. one only imported, never
  trusted) -> closed, nothing stored. A *different trusted* partner Y answers -> the connection counts as one to Y,
  `connect(X)` fails with `WRONG_PARTNER`, and the only database change is Y's address/port (compare all certificate rows
  before and after). (Write these first; they are the most important new ones.)
- **F9:** two isolated connections between the same two services at once both work; closing one leaves the other usable.
- **Pairing by mistake:** pairing with the address of an instance that both sides already trust connects without any
  registration (no user asked) and changes nothing in the database but the address/port.
- **One side deleted the other:** pairing again works in both directions (the `RegistrationCommitTest` covers the server side;
  add the client side: C still trusts S, S deleted C).
- **F3:** a long message with many non-ASCII characters arrives whole.
- **F4:** a request on a connection that the partner closes fails at once, not after 30 s; `onIsolatedConnectionClosed` is called
  exactly once.
- **F8:** a silent TCP client on the certificate delivery port does not stop a pairing; connecting to an unroutable address fails
  within the connect timeout.
- Transition table unit tests; timeouts with short test settings; a frame header above the maximum closes the connection
  before allocating.

## 6. Open questions

None; the user's decisions are in section 1.
