# Task: rework the socket / process part of `auth`

Goal of this task, and nothing else: get sockets, the processes running on them, their states and the concurrency
around them straight. Breaking changes are fine everywhere (wire format, database, API), there are no running instances.
The JDeferred replacement (`fun.with.promise`) is a separate, later task, see "Order of work".

Everything below was found by reading the code and by tests on branch `registration-tests`
(based on `gradle-fixes`, not merged yet). Nothing in this document is implemented.

## How it works today

A `MelAuthSocket` (extends `MelSocket`) has one reader thread (`MelSocket.runImpl`) that pushes messages into a queue.
One `SocketWorker` per socket consumes that queue, so incoming messages of one socket are handled one at a time.
What handles them is `MelAuthSocket.process`, a `MelProcess`:

| Process | Purpose | Ends how |
|---|---|---|
| `MelRegisterProcess` | pairing (users on both sides accept) | failure: `stop()`. Success: nothing closes the socket, see open question 2 |
| `MelAuthProcess` | mutual TLS client certificate -> trusted partner, 1 request + 1 response | replaced by one of the two below |
| `MelValidationProcess` | normal connection, service requests | final, lives until the socket is closed |
| `MelIsolatedProcess` (+ `MelIsolatedFileProcess`) | isolated connection to one service; the socket switches to raw block mode (`MODE_ISOLATE` in `MelSocket.runImpl`) | final |

The process is assigned by `MelProcess`'s constructor (`melAuthSocket.setProcess(this)`). On the client that happens
before the socket is connected (`ConnectWorker`). On the server it happens lazily in `MelAuthSocket.onMessage`, when the
first message is a request with the register or auth intent. Auth-to-validation/isolated is done inside
`MelAuthProcess` (about lines 81, 100 and 190, 215).

Related: `ConnectedEnvironment` (one validation process per certificate: `registerValidationProcess`), `ConnectWorker`
(client side connect/register/isolate), `MelAuthSocketOpener` (server socket), `MelCertRetriever` and `MelAuthCertDelivery`
(certificate exchange before pairing, also sockets with their own little processes).

## Problems found

1. `MelAuthSocket.process` is a plain, non-volatile field. Written by a `ConnectWorker` thread (client) and by the socket
   worker thread (lazy creation, transitions), read by the reader thread (`onIsolated`, `onBlockReceived` cast it blindly to
   `MelIsolatedProcess`) and by other threads (`ConnectedEnvironment.removeValidationProcess`, `getProcess()` casts).
2. The transition happens before its guard: `new MelValidationProcess(...)` sets itself as the socket's process in the
   constructor, before `registerValidationProcess` decides that the connection is a duplicate. A duplicate leaves the socket with an
   unregistered validation process ("waiting for the other side to close connection").
3. Messages that arrive while the socket has no process are dropped. That was the registration race (confirmation overtook its request),
   found and fixed in `MelRegisterProcess`; `MelAuthSocket.onMessage` now warns, but the general policy is missing.
4. `MelSocket.send` has no lock. Messages over `MAX_CHARS` are written in several `writeUTF` chunks and the receiver
   reassembles them in one shared buffer. Two threads sending long messages on the same socket can interleave chunks.
   Suspected, not tested.
5. `MelValidationProcess.onMessageReceived` and `MelProcess.handleAnswer` are `synchronized` although incoming messages are
   already serialized by the worker. The first holds the monitor during the whole service interaction.
6. The register socket is not closed after success (`//todo send done, so the other side can connect!`). The same missing
   "done" leaves a small window: if the server's user accepts first and the client confirms later, the client is finished at once
   and may connect on a new socket while the server is still processing the client's confirmation (the server trusts the client
   only then). Practically hidden by the TCP+TLS handshake, but not guaranteed. `RegistrationTest.serverAlreadyTrustsTheClientWhenTheClientIsDone`
   measures it (0 of 10 rounds).
7. `MelAuthWorker`/`MelBoot`: a part of the worker that does not start (taken port, brotcaster) never reaches the caller of
   `MelBoot.boot()` (pending forever), pinned in `BootFailureTest`. Related but small, take it along if it fits.
8. `suspend()` / `resume()` of `MelAuthService` have no callers anywhere. Candidates for deletion.

## Direction (to be challenged, not decided)

- Explicit state per socket: `NEW -> REGISTERING | AUTHENTICATING -> ESTABLISHED | ISOLATED -> CLOSED`.
  States are immutable objects that carry their process and the authenticated partner certificate, so "established implies
  authenticated" holds by construction.
- Transitions only on the socket's worker thread (it is the single consumer already), the state is published with a volatile or an
  `AtomicReference`. Other threads use compare-and-set only for cross-thread events (close, shutdown, the duplicate decision).
  Steady state is one volatile read, no locks. Performance is not the reason for this (an uncontended monitor costs nothing next to
  TLS and JSON); correctness and blocking are.
- Every state decides what to do with every message type: handle, reject and close, or queue. Nothing is dropped silently.
- The guard before the transition: decide duplicate/not duplicate first, then switch the state. A duplicate ends in `CLOSED`.
- A lock only where several threads really write: `send`.
- Close the register socket after a completed registration (decided), probably with the "done" message so that the partner
  knows when it may connect.

## Decisions from the user

- Duplicate connections (same partner certificate): keep the first, close the new one.
  Both sides can connect to each other at the same moment, and each side may see a different "first". Needs a deterministic
  tie-breaker that both sides compute equally. Do NOT use certificate ids: they are local to each database. Something both sides
  know, e.g. the sha256 hash of the certificates: the connection initiated by the side with the lower hash wins.
- Register socket: closing it after success is fine.
- No "done" message. The mutual confirmation is the commit (a "done" round would only move the unavoidable window, two generals).
  C and S are roles per connection (C opened it, S accepted it), not kinds of instances: every instance can be either,
  a "server" also connects to its clients to push messages. S has the final say:
  1. C -> S `RegisterRequest` (name, ports; the certificate is the TLS client certificate)
  2. S -> C `Decision(accepted)`, only if S's user decides first (informational)
  3. C -> S `Decision(accepted)`
  4. S -> C `Final(accepted)` once S has both decisions: S persists first, then sends it
  5. C persists, then C closes the register socket, then connects for auth. S closes only on timeout.
  A rejection: `Decision(false)` (or `Final(false)` from S), nothing persisted, the rejecting side closes.
  Window that cannot be closed: S persisted and `Final` is lost -> re-registration repairs it (see below).
- The persistence must never be cut off by a close: the side that persists last (C) closes, and a close by the peer is an event in
  the socket's queue behind the messages already received, never an immediate stop. Today it is: the reader thread calls
  `listener.onClose` directly on EOF and `MelSocket.stop()` shuts the worker down, so a message still queued may never be handled.
  Persisting a registration is a single statement, no transaction: one INSERT of the already trusted row (new partner) or one
  UPDATE (known partner), written only after the outcome is final, never during the wait for the users. Nothing is written before.
  (`SQLQueries.beginTransaction` only switches autocommit off on the one shared connection, so it would not isolate anything from
  other threads.)
- Register socket timeout: about 5 minutes waiting for the users, then `CLOSED(TIMEOUT)`, nothing persisted.
- A message that does not fit the state is a protocol violation: send an error with a reason, then close. No queueing, no
  recovery. Errors inside a valid state (a service refuses a request) are answered and the connection stays. Every close carries a
  reason (`CLOSED(PROTOCOL_VIOLATION | DUPLICATE | REJECTED | TIMEOUT | PEER_CLOSED | ...)`), visible to tests on both sides.
- Duplicate validation connections (isolated ones are exempt): exactly one survives, the same one on both sides. Rule, independent
  of the local state of either side: different initiators -> the connection initiated by the lower certificate hash wins; same
  initiator -> the newer one wins (the initiator gave up the old one, e.g. a half-open TCP connection; MQTT session takeover).
  A side never opens a validation connection to a partner it already has one to, in either direction: a connection carries
  messages both ways, whoever opened it. Restricting the rule to pending
  connections does not work: the two sides see "established" at different moments and could close both.
- Registration of an already known certificate (two-generals leftover, partner deleted us, or a needless re-pairing): runs the
  normal flow, the user is told the partner is known already. Accept: keep the row (id and service approvals stay), refresh
  name/address/ports, trust it. Reject: leave the existing entry untouched (today's reject path deletes `partnerCertificate`,
  which must never hit an existing entry). Today such a registration fails on `UNIQUE (certificate)`.
- The registering certificate must be proven: the partner certificate is the TLS client certificate of the register socket,
  not a certificate carried in the request payload (otherwise anyone could re-register a known partner's public certificate
  and redirect its address).
- Isolated connections: `ISOLATED` becomes a state of its own (see the state table below).
- `MelCertRetriever` / `MelAuthCertDelivery`: left out of the model for now.
- A full re-implementation of the socket/process part is allowed, including wire format and database.

## Wire format and state table (decided)

Typed frames replace `writeUTF` chunks and the `MODE_ISOLATE` mode switch: `type (1 byte) | length (4 bytes) | payload`.
JSON frames for messages, binary frames for blocks. No mode switch in the reader thread, no reassembly buffer; `send` keeps a
lock so whole frames do not interleave.

Maximum frame size: a protocol constant, 1 MiB to start (measure later). A header announcing more is a protocol violation and
closes the connection before anything is allocated (the receiver holds a whole frame in memory; phones, hostile peers).
This limits frames, not files: a file is sent as many records, each with an 8 byte offset, so file size stays unlimited
in practice (videos > 2 GB must work). The file process cuts its pieces to fit the maximum frame size.

Blocks (today: `MelIsolatedFileProcess`, fixed `BLOCK_SIZE` = 4 MiB + 21, records `[T|t|E][stream id][offset][length][data]`
packed into it, a 0 byte ends them, the rest is padding, so every block is sent full size): a binary frame carries exactly one
record and has the size it needs. The record layout stays the file process's business, the socket only carries frames.
(The javadoc of `transfer()` says the last record has an 8 byte length, the code writes 4.)

States: `NEW -> REGISTERING | AUTHENTICATING -> ESTABLISHED | ISOLATED -> CLOSED(reason)`. C/S are roles per connection.

| Role | State | Allowed | Effect |
|---|---|---|---|
| C | NEW | (C sends first) | `RegisterRequest` -> REGISTERING, `AuthRequest` -> AUTHENTICATING |
| C | REGISTERING | `Decision(S)` | store, inform the user |
| | | `Final(true)` | persist, close (`REGISTERED`) |
| | | `Final(false)` | close (`REJECTED`) |
| | | local user decides | send `Decision`; rejection: close (`REJECTED`) |
| C | AUTHENTICATING | `AuthResponse(ok)` | validation: duplicate rule -> ESTABLISHED or close (`DUPLICATE`); isolation: -> ISOLATED |
| | | `AuthResponse(refused)` | close (`REFUSED`) |
| S | NEW | `RegisterRequest` | ask the user, start timeout -> REGISTERING |
| | | `AuthRequest` | TLS certificate not trusted: `Error(UNTRUSTED)`, close. Validation: duplicate rule, `AuthResponse` -> ESTABLISHED. Isolation: check service access, `AuthResponse` -> ISOLATED |
| S | REGISTERING | `Decision(C)` | S decided already: persist, `Final`; else store |
| | | local user decides | C's decision known: persist, `Final`; else send `Decision`. Rejection: `Final(false)`, close |
| both | ESTABLISHED | service traffic | handled; a service error is answered, the connection stays |
| | | duplicate rule (other connection) | close (`DUPLICATE`) |
| both | ISOLATED | block frames | to the isolated process |
| both | any | `Error(reason)` | close (reason, remote) |
| | | end of stream | close (`PEER_CLOSED`), after the messages already queued |
| | | local shutdown | close (`SHUTDOWN`) |
| | | anything else | `Error(PROTOCOL_VIOLATION)`, close |

Timeouts: NEW 30 s (first message), AUTHENTICATING 30 s, REGISTERING 5 min. No keep-alive in ESTABLISHED for now.
The duplicate rule runs where both the partner and the kind of connection are known: on C when `AuthResponse(ok)` arrives, on S
when the `AuthRequest` arrives.

## Safety net that exists

On the branch (`./gradlew :auth:test`, about 2 minutes):
- `AuthHandshakeTest`: pairing, auth over real TLS, impostors, isolated connections, suspend/resume.
- `RegistrationTest` (12): accept/reject on both sides in both orders, instant handlers, who is told what on a rejection, trust only after
  both confirmed (a late accept after a rejection leaves nothing behind), the server trusts the client when the client is done.
- `BootAndShutdownTest` (11), `BootFailureTest` (4): boot levels, shutdown, startup failures.
- Known flake: the multicast discovery of the two test instances now and then swallows the explicit pairing connect.
  `RegistrationTest.startPairing()` retries; `AuthHandshakeTest.pair()` also retries, and was seen failing once in about six full runs.

## Tests to write first

- Concurrent long sends on one socket (problem 4).
- Two simultaneous connections for the same certificate pair, both directions at once: exactly one survives, the same on both
  sides (problem 2). Also: a reconnect while the partner still holds the old connection.
- A peer close right behind the registration's last message: the receiver still persists.
- Messages that do not fit the state (auth message in the register state, block data without isolation, and so on).
- A plain unit test of the transition table (no sockets).

## Order of work

1. Done: branch `rework2026` (from `registration-tests`) is the main line for this and all following reworks.
2. Tests above, against the old code where possible.
3. New state type behind the existing getters, then migrate the processes one by one.
4. Only then the JDeferred port (`fun.with.promise`), see `mel-jdeferred-replacement` in memory. The port touches the same classes.

## Practical notes

- `. ~/tools/env.sh` first (JDK 21; if `~/tools` is empty, see `mel-build-overview` in memory).
- `./gradlew build` (tests included) passes; `build -x test` is the fast variant.
- Git identity is not configured: commit with `-c user.name=xor -c user.email=mrkeule@gmail.com`.
- Test ports: each test class takes its own port range (18000 handshake, 19000 registration, 20000 boot, 21000 boot failure).
- `.gitignore` is a whitelist: new directories and files outside the listed modules are ignored (a `docs/` folder is). Root `*.md` files are fine.
- Already fixed on the branch (do not redo): the register request is sent before the register handlers are asked; a rejection by the local user
  is reported as local on both sides; the receiving side trusts a partner only when both sides confirmed; `answerUuid` is gone (code and database).

## Open questions

None at the moment.
