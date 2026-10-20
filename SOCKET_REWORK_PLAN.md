# Socket/process rework: implementation plan

Companion to `SOCKET_PROCESS_REWORK.md` (the brief: problems, decisions, wire format, state table, tests). The brief says
**what** we want; this file says **where and how**, class by class, plus what a code analysis on 2026-10-21 found on top.
Read the brief first. Nothing in here is implemented.

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

## 4. Order of steps

Each step ends with `:auth:test` and `:auth:integrationTest` green and the listed `@Disabled` tests enabled.

1. **Small and independent:** F12 (own buffer constants), F10 (shared scheduler), F13, `sockets` thread-safe.
2. **Close as an event, exactly once, pending requests rejected** (F4) -> enable
   `RegistrationCommitTest.theLastDecisionCountsAlthoughThePartnerHangsUpRightBehindIt`; remove the pairing retries.
3. **Send lock** -> enable `ConcurrentSendTest.longMessagesFromSeveralThreadsArriveWhole` (works even before frames).
4. **Typed frames + max frame size** (+ `RawPeer`) -> F3 gone; block tests can now be expressed.
5. **State type + transition unit test; dispatch by state; protocol violations close with a reason** -> enable the
   `ProtocolViolationTest`s, make them check the reason.
6. **Auth: partner certificate from TLS and the trusted check on both sides (F1), wrong-but-trusted partner handled, duplicate
   rule in `ConnectedEnvironment`** -> enable the `DuplicateConnectionTest`s.
7. **Registration rewrite** (Final, single-statement commit, re-registration, TLS certificate) -> enable
   `RegistrationCommitTest.aKnownPartnerCanRegisterAgainAndKeepsItsAccess`.
8. **Isolated as a state, blocks as frames, file process one record per frame, service creates the process, several isolated
   connections per service pair (F9).**
9. **`ConnectWorker` rewrite, timeouts (connect, NEW, AUTHENTICATING, REGISTERING), certificate delivery per client (F7, F8).**
10. **Cleanup:** suspend/resume, address map, placeholder sockets, `SocketTest`/`LargeJson`, update the brief.

Then the JDeferred port (separate task).

## 5. Tests to add in the new session

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
