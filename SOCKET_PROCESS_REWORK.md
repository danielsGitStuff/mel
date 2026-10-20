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
- A full re-implementation of the socket/process part is allowed, including wire format and database.

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
- Two simultaneous connections for the same certificate pair, both directions at once: exactly one survives (problem 2).
- Messages that do not fit the state (auth message in the register state, block data without isolation, and so on).
- A plain unit test of the transition table (no sockets).

## Order of work

1. Merge `registration-tests` into `gradle-fixes` (or branch from it).
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

1. Is the register socket closed by the side that completes first, or by both? (depends on the "done" message)
2. Isolated connections: should the block mode switch become a state of its own, or stay a flag inside the isolated process?
3. `MelCertRetriever` / `MelAuthCertDelivery` sockets: part of this model or left alone?
