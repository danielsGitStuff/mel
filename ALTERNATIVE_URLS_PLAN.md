# Alternative / Fallback URLs Plan

## Goal
Allow each trusted `Certificate` (remote Mel instance) to have an ordered list of user-editable alternative/fallback addresses (`host[:port]`, optional `certport`), in addition to autodiscovery and the last-known address.

## Rules for Next Session
1. **Create a new git branch** from `master` before making any code edits (e.g., `git checkout -b feat/alternative-addresses`).
2. **2-step process:**
   - **Step 1:** Full feature implementation + tests.
   - **Step 2:** Timeout budget & non-blocking polish (separate pass after Step 1).
3. **Do it properly** (clean schema/DAO/manager separation, shared validation, proper UIs, tests) rather than minimal hacks.

## Environment Already Configured
- **JDK:** OpenJDK 21 (`/usr/bin/java`)
- **Android SDK:** `.android-sdk/` (gitignored) with `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`, `cmdline-tools;latest`
- **Gradle SDK config:** `local.properties` (gitignored) -> `sdk.dir=/home/droid/projects/mel/.android-sdk`

---

## Step 1: Feature Implementation

### 1. Database Schema & Migration
- Update both identical schema files:
  - `auth/src/main/resources/de/mel/auth/sql.sql`
  - `android/src/main/assets/de/mel/auth/sql.sql`
- Add `certificate_address` table:
  - `id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT`
  - `certificateid INTEGER NOT NULL` (`FOREIGN KEY (certificateid) REFERENCES certificate(id) ON DELETE CASCADE`)
  - `address TEXT NOT NULL`
  - `port INTEGER` (nullable -> falls back to `certificate.port`)
  - `certport INTEGER` (nullable -> falls back to `certificate.certport`)
  - `priority INTEGER NOT NULL DEFAULT 0`
  - `UNIQUE (certificateid, address, port)` + index on `certificateid`
- Update `DatabaseManager` (`auth/src/main/java/de/mel/auth/data/access/DatabaseManager.java`) to run an idempotent `CREATE TABLE IF NOT EXISTS certificate_address (...)` + index on startup for existing databases.

### 2. Model, DAO & `CertificateManager`
- `CertificateAddress extends SQLTableObject` in `auth/src/main/java/de/mel/auth/data/db/`
- `CertificateAddressDao extends Dao.ConnectionLockingDao` in `auth/src/main/java/de/mel/auth/data/db/dao/`:
  - `getAddressesForCertificate(long certificateId)` ordered by `priority ASC, id ASC`
  - `replaceAddressesForCertificate(long certificateId, List<CertificateAddress> addresses)` (atomic replace)
- Expose via `CertificateManager` (`auth/src/main/java/de/mel/auth/data/access/CertificateManager.java`) with `P.confine(...)` locking.

### 3. Shared Input Parser & Validator (in `auth`)
- Support `host`, `host:port`, `[ipv6]`, `[ipv6]:port` (optional `certport`).
- Trim whitespace; reject empty input, URL schemes (`http://`), paths/slashes, and ports outside `1..65535`.
- Deduplicate `(address, port)` per certificate.

### 4. Connection & Discovery Procedure
- Build an ordered, deduplicated candidate list for a `Certificate`:
  1. Last-known `certificate.getAddress()` + `port` + `certDeliveryPort` (if non-null)
  2. `certificateManager.getAlternativeAddresses(certificateId)` in `priority` order (falling back to `certificate.getPort()` / `certDeliveryPort` when `null`)
- Update:
  - `ConnectedEnvironment.connect(Certificate)` (`auth/src/main/java/de/mel/auth/service/ConnectedEnvironment.java`)
  - `ConnectWorker` (`auth/src/main/java/de/mel/auth/socket/ConnectWorker.java`) & `ConnectJob`:
    - Try candidates sequentially, creating a per-attempt `ConnectJob` / `MelAuthSocket` (matching existing `ConnectWorker.authenticate`).
    - Stop immediately on `DuplicateConnectionException` (hand off to `whenConnected`), `ShamefulSelfConnectException`, `UntrustedPartnerException`.
    - Advance to next candidate on `ConnectException`, `SocketTimeoutException`, `UnknownHostException`, `WrongPartnerException`; settle with `CannotConnectException` only after all candidates fail.
    - Note: `MelAuthProcess` already updates `certificate.address` to the resolved IP on successful auth while keeping `certificate_address` hostnames intact.
  - `MelAuthServiceImpl.connectAndCollect(...)` and `connectToService(...)` (`auth/src/main/java/de/mel/auth/service/MelAuthServiceImpl.java`).

### 5. JavaFX & Android UIs
- **JavaFX:** `OthersSettingsFX` (`authfx/src/main/java/de/mel/auth/gui/OthersSettingsFX.java`) + `others.fxml`:
  - Add edit-addresses UI for `selectedCert` (via `AuthSettingsFX` secondary button or inline/dialog panel): add `host[:port]`, remove, reorder priority, inline validation error, save via `CertificateManager`.
- **Android:** `ConnectedController` (`android/src/main/java/de/mel/android/controller/ConnectedController.java`) + `content_connected.xml`:
  - Add "Edit Addresses" action for `selectedCert` opening a dialog with `EditText` (`host[:port]`), Add button, list with remove & up/down reorder, `setError(...)` validation, and Save/Cancel.

### 6. Verification (Step 1)
- Unit tests in `auth` for `CertificateAddressDao`, `DatabaseManager` migration, address parser/validator, and `ConnectWorker` fallback behavior.
- Compile/test via `./gradlew :auth:test :authfx:compileJava :android:compileDebugJavaWithJavac`.

---

## Step 2: Timeouts & Non-blocking Polish
- Ensure per-attempt and/or overall connect timeout does not exceed 10s (`MelAuthService.DEFAULT_CONNECT_TIMEOUT_MS = 10_000L`; `TlsConnect.connect` currently applies it separately to TCP connect and TLS handshake).
- Move `intendedCertificate.getInetAddress()` (`InetAddress.getByName`) out of `MelAuthServiceImpl.connectAndCollect` caller thread into background execution so Android never hits `NetworkOnMainThreadException` or stalls UI.
- Audit remaining `MelAuthService` timeouts (`registerTimeoutMs`, `requestTimeoutMs`, `StateTimeout`) and verify no UI path blocks on `connect(...)`.
