# Rework runbook: sockets + JDeferred, unattended

This file drives one long, **unattended** run on branch `rework2026`. The user gives a "GO" and is away for 5+ hours on a slow
machine. Nobody answers questions during the run. **Time is not a constraint:** the machine keeps running (16 hours are fine,
user 2026-10-21). Never cut corners or stop early to save time; thoroughness over speed.

Related documents:
- `SOCKET_PROCESS_REWORK.md`: the brief (problems, decisions, wire format, state table).
- `SOCKET_REWORK_PLAN.md`: the socket plan (findings F1-F16, decisions, class by class, **section 4 = the 10 steps**).
- `REWORK_LOG.md`: written during the run (create it at the start).

## Definition of done (insisted on by the user)

At the end of the run:
1. **Sockets reworked:** all 10 steps of `SOCKET_REWORK_PLAN.md` section 4 ticked; every `@Disabled` socket test enabled and
   green.
2. **No JDeferred:** `grep -rn "org.jdeferred" --include=*.java --include=*.kt --exclude-dir=build .` prints nothing;
   `api libs.jdeferred` is gone from `auth/build.gradle`, and `jdeferred` from `gradle/libs.versions.toml`.
3. `./gradlew build` is green (all modules, unit and integration tests, Android assembles).
4. Everything is committed on `rework2026`. Nothing pushed.

If the run cannot reach all of it, it stops at a green commit and `REWORK_LOG.md` says exactly what is missing and why.

**Baseline (2026-10-21, before the run):** `./gradlew build` green on this machine in about 4.5 minutes with the `auth`
tests up to date; count about 8-10 minutes when everything has to run again. `:auth:test` about 12 s, `:auth:integrationTest`
about 3 minutes.

## Packages and order

| # | Package | Status |
|---|---|---|
| P1 | Tests for the socket rework | done (`05afb263`); the steps add more as they go |
| P2 | Socket rework | `SOCKET_REWORK_PLAN.md` section 4, steps 1-10 |
| P3 | Tests for the JDeferred replacement | below |
| P4 | JDeferred replacement | below |

Order: P2 -> P3 -> P4. Reason: P2 rewrites the classes with the trickiest JDeferred code (register `when(...)`,
`ConnectWorker` chains, isolated promise). Porting them first would mean writing them twice. P3 comes after P2 because it
pins the API as it is after the rework.

**Rule during P2:** new or rewritten code uses `fun.with.promise` (or no promise at all: one worker per socket replaces many
callbacks with plain state). The public API stays JDeferred until P4 (`connect()`, `request()`, `Job`, `Request`,
`getIsolatedProcess`, `DeferredRunnable.shutDown/onShutDown`). Where new code meets it, use a small temporary adapter
`auth/src/main/java/de/mel/auth/tools/PromiseAdapters.java` (both directions); P4 deletes it. `auth` already depends on
`fun.with` (`api 'fun.with:fun.with'`).

## P3: tests for the JDeferred replacement

What exists: `fun.with` has its own `PromiseImplTest` (~27 tests). In Mel, `BootAndShutdownTest`/`BootFailureTest` pin the
`MelDeferredManager`/`ShutDownDeferredManager` contracts; `RegistrationTest` pinned the register `when(...)` (gone after P2).

Write (in `auth`, unit level where possible, tagged `integration` only if they boot instances):
- [x] **Request/answer:** a request resolves with the answer's payload; an error answer rejects with the `ResponseException`
      sent; a request without an answer rejects on timeout (short timeout setting from P2 step 1); callbacks run on the
      thread that settles, or at once if already settled (the JDeferred semantics the code relies on).
- [x] **Jobs:** `ConnectJob`/`IsolatedConnectJob` resolve once; settling twice is a bug that throws (JDeferred: `IllegalStateException`,
      `PromiseImpl`: `AlreadyResolved*Exception`): find every place that might settle twice (known: `MelAuthWorker` start when a
      service throws in `onServiceRegistered`, see `BootFailureTest`) and make it settle once, test included.
- [x] **`MelService.getIsolatedProcess`:** reuses an open isolated process, opens a new one after a failure or close.
- [x] **`DeferredRunnable` start/shutdown promises:** `getStartedDeferred` resolves when running; `shutDown()` of a runnable whose
      `onShutDown` returns null still completes (the `fused` null filter).
- [x] **filesync baseline:** run `./gradlew :filesync:test` (and `:filesync:integrationTest` if anything is tagged) on the
      result of P2 and record which tests are green; those are the safety net for porting `filesync` (18 files). Do not start P4
      on a red filesync.
- Modules without automated tests (`android` 12 files, `authfx` 2, `fxbundle` 1, `serverparts` 2, `miniserver` 3, `contacts` 1):
  compile-only safety net; port them mechanically and keep behaviour identical.

## P4: the JDeferred replacement

Current size (2026-10-21, before P2): 75 files import `org.jdeferred` (auth 36, filesync 18, android 12, miniserver 3, authfx 2,
serverparts 2, contacts 1, fxbundle 1), plus code that uses `Request`/`Job` without importing JDeferred.

Mapping:

| JDeferred | fun.with.promise |
|---|---|
| `Promise<D, F, P>` | `Promise<D, F>` (no progress; nothing uses it) |
| `DeferredObject<D, F, P>` | `PromiseImpl<D, F>` (factory `withoutThrowingOnException()` unless a test needs throwing) |
| `.done(cb)` / `.fail(cb)` | `.onDone(cb)` / `.onError(cb)` |
| `.always(cb)` (1 use, ConnectWorker, likely gone after P2) | `onDone` + `onError` |
| `.resolve(v)` / `.reject(e)` | `.resolveOk(v)` / `.resolveError(e)` |
| `isResolved()` / `isRejected()` / `isPending()` | `hasResult()` / `hasError()` / `isPending()` |
| `DefaultDeferredManager`/`MelDeferredManager.when(...)` (fail fast) | `Promise.fusedAnd(...)`; errors are read from the input promises (no `OneReject`) |
| `ShutDownDeferredManager` (always resolves) | `Promise.fused(...)` (skips nulls) |
| `DeferredRunnable.ResolvedDeferredObject()` | `Promise.resolvedOk()` |
| `Job<R, F, P> extends DeferredObject` | `Job<R, F> extends PromiseImpl` |
| `Request<T> extends DeferredObject<T, Exception, Void>` | `Request<T> extends PromiseImpl<T, Exception>` |
| failure type `List<String>` (Android permissions) | keep as `Promise<..., List<String>>` (allowed) or a small holder |

Order:
1. [ ] `auth` internals that are not public API (tools, workers, sockets left over after P2).
2. [ ] The public types one at a time, each followed by compiling **all** modules and fixing every caller in the same commit:
       `DeferredRunnable` (start/shutdown promises), `Job` and the connect jobs, `Request` + `MelRequest.answerDeferred`,
       `MelAuthService` API (`connect`, `connectToService`, `boot`, `shutDown`, `getAllowedServices`), `MelService` /
       `MelServiceWorker`, `Bootloader`, `MelBoot`.
3. [ ] Remaining modules: `contacts`, `filesync`, `miniserver`, `serverparts`, `authfx`, `fxbundle`, `android`.
4. [ ] Delete `MelDeferredManager`, `ShutDownDeferredManager`, `PromiseAdapters`; remove the dependency (`auth/build.gradle`,
       `libs.versions.toml`); the grep in "Definition of done" is empty; full `./gradlew build`.
5. [ ] Update memory `mel-jdeferred-replacement` (done) and `mel-socket-rework` (done).

`fun.with` is a git submodule. Avoid changing it. If a change is unavoidable (a missing overload, a bug): commit inside
`fun.with` first, then the new submodule pointer in the parent, both with the trailer; never push either; log it.

## Rules for the unattended run

**Never wait for a human.**
- Do not ask questions (no AskUserQuestion, no "should I ...?" and stopping). Decide, write the decision and the reason into
  `REWORK_LOG.md`, continue. Decisions already made by the user (brief, plan section 1) are binding.
- If a decision would contradict the user's decisions, do not take it: take the closest option that does not, log it as
  "needs the user".

**Stay safe.**
- Only branch `rework2026`. No push, no force, no rebase, no branch deletion, no changes to `master`, `gradle-fixes`,
  `registration-tests`. No changes in `~/tools`.
- Never make a test pass by disabling it, loosening an assertion or adding sleeps. The only allowed `@Disabled` is a test
  that waits for a later step, with that step in the reason (as today).
- Only revert your own uncommitted work (`git restore`/`git stash` on files you changed); never `git reset --hard` past a
  commit.

**Stay green.**
- Every commit: `./gradlew :auth:test` green and the integration tests of the touched area green (single classes with
  `--tests` while iterating; the whole `:auth:integrationTest` before the commit).
- End of each socket step and each P4 sub-step: `./gradlew build -x test -x integrationTest` (all modules compile).
- End of each package: full `./gradlew build`.
- Timing-related integration tests (duplicates, hang-up, concurrent send): 5 runs in a row before a step counts as done. A test
  that fails once in 5 is a bug, not a flake: find it.

**Don't get stuck.**
- If one item does not move for about 45 minutes: commit nothing broken; restore the last green state of your own changes; write
  what was tried and why it failed into the log; continue with the next independent item; come back at the end.
- Slow machine: run long Gradle commands in the background with a generous timeout and wait for the notification; never poll
  with sleeps; read results from the XML/log files with a short summary script instead of reading whole logs.

**Keep track.**
- `REWORK_LOG.md`: one entry per step: time, what changed, test results (counts and durations), decisions, problems. Commit it
  with the step.
- Tick the checkboxes in `SOCKET_REWORK_PLAN.md` and here as steps finish.
- Update the memory files at the end of each package (`mel-socket-rework`, `mel-jdeferred-replacement`, `mel-build-overview`).

**At the end** write a final summary at the top of `REWORK_LOG.md`: done / not done against the definition of done, all
decisions taken alone ("needs the user" first), remaining risks, and the commands to verify. Then stop.

## Kick-off

In this session: the user writes "GO". In a fresh session: "Read `REWORK_RUNBOOK.md` and execute it, unattended, until the
definition of done is reached." The documents are self-contained; memory points here.
