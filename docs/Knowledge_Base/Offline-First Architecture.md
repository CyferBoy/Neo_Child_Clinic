# Offline-First Architecture

> Sync, queue, conflict resolution, and the offline guarantees — traced end to end from
> source. Related: [[Caching]] · [[Room Database]] · [[Clean Architecture]] · [[Known Bugs]]

## The guarantee, stated first

✅ **A user can add, edit, and delete a patient or a vaccination with the network
completely unavailable.** No path from a write to a database touches `Postgrest`, `Auth`,
or `Realtime`. Proven by tracing the write paths below.

## Triggers — everything that starts a sync

| Trigger | Code | What runs |
|---|---|---|
| App process start | `app/NeoChildApp.kt:155-169` | `PeriodicWorkRequest<SyncWorker>` 15 min, `NetworkType.CONNECTED`, unique `"SyncWork"`, `KEEP` |
| App process start | `app/NeoChildApp.kt:83` | `SyncReconciler.start()` → push **then** pull |
| `MainActivity.onCreate` | `app/MainActivity.kt:87` | `scheduleSync()` |
| `MainActivity.onResume` | `app/MainActivity.kt:187` | `scheduleSync()` |
| After login | `core/security/AuthViewModel.kt:256` | `scheduleImmediateSync()` (expedited) |
| After backup restore | `data/backup/BackupRepositoryImpl.kt:183`, `:407` | `scheduleImmediateSync()` |
| **Every queue write** | `core/sync/SyncRepositoryImpl.kt:84` | `scheduleSync()` — i.e. once per row |
| Transient failure / session deferral | `SyncRepositoryImpl.kt:167`, `:174`, `:229`, `:245` | `scheduleSync()` |
| Offline → online edge | `core/sync/SyncReconciler.kt:57-63` | `processNextItems()` then `refreshDataUseCase()` |
| Realtime postgres event | `PatientListViewModel.kt:110-117`, `DashboardViewModel.kt:207-212` | `refreshDataUseCase()` |
| Manual pull-to-refresh | `feature/sync/presentation/SyncScreen.kt:98`, `DashboardViewModel.kt:422` | push + pull |
| Sign-out flush | `AuthViewModel.kt:298-302` | bounded by `withTimeoutOrNull(SIGN_OUT_FLUSH_TIMEOUT_MS)` |

## The upload path (local → cloud)

`SyncWorker.doWork()` (`core/sync/SyncWorker.kt:25-48`) → `SyncRepositoryImpl.processNextItems()`:

```mermaid
flowchart TD
    A[processNextItems] --> B{SingleFlight gate}
    B -->|waiter| Z[return await result]
    B -->|owner| C[cleanCorruptedItems]
    C --> D[requeueStaleSyncingItems older than 5 min]
    D --> E[getItemsByStatus PENDING]
    E -->|empty| IDLE[syncState = IDLE]
    E --> F[syncState = SYNCING]
    F --> G{Session resolved?}
    G -->|not authenticated| DEF[syncState = IDLE, no retry]
    G -->|timeout / refresh failure| SCHED[scheduleSync, return]
    G -->|ok| H[orderPendingIntoGroups]
    H --> I{per group}
    I --> J[all items → SYNCING in one transaction]
    J --> K[fetchRemoteConflictData: one SELECT per table]
    K --> L[uploadEntity per item, sequentially]
    L -->|401| M[refresh session, retry same item once]
    L -->|success| N[markUploaded then DELETE queue row]
    L -->|transient, retries &lt; 5| PENDING[back to PENDING]
    L -->|permanent / retries = 5| FAILED[markFailed, stays in queue]
    M --> L
    N --> RESULT{syncState}
    FAILED --> RESULT
    PENDING --> RESULT
    RESULT -->|hasError| ERR[ERROR + scheduleSync]
    RESULT -->|clean| IDLE
```

### Ordering guarantees

`SyncRepositoryImpl.orderPendingIntoGroups` (`:679-700`):

1. Group by `transactionGroupId ?: UUID.randomUUID()` — no group id means singleton group.
2. Sort groups by `(min sortKey, min createdAt)`; `sortKey` is negated for `DELETE`
   operations, so **children unlink before parents delete**
   (`finance_transactions_visit_id_fkey` is the reason).
3. Within a group: exact enqueue order by `(createdAt, queueId)`, deliberately **not**
   re-ranked by entity priority.

Pinned by `app/src/test/.../core/sync/SyncOrderingTest.kt` (6 tests).

### Retry and failure

`SyncRepositoryImpl.classifyAndRecordError` (`:353-367`):

- Transient = `IOException`, `HttpRequestException`, or `RestException` with 408 / 429 / 5xx.
- **5 attempts**, then `FAILED`. `FAILED` rows keep `lastError` and are retried only
  manually from the Sync screen (`:518-542`).
- `401` never reaches the classifier — it is handled earlier with one inline retry.
- Backoff is WorkManager's (`EXPONENTIAL`, `MIN_BACKOFF_MILLIS`, `SyncManagerImpl.kt:24-28`).
  **There is no per-item backoff timer in the queue itself.**

### Session handling

`resolveSessionOrDefer()` (`:207-256`) waits out `SessionStatus.Initializing` for up to 5 s
(`:611-617`), then either defers (schedules a retry, returns false) or fails permanently
with **no retry scheduled**.

## The pull path (cloud → local)

`RefreshDataUseCase.runRefresh()` (`:51-127`) runs 14 steps in FK-safe order. Each step is
wrapped in `runRefreshStep` (`:134-142`) so one failure does not abort the rest; an
aggregated error is thrown at the end.

```
Patients → Vaccinations → Finance → Expenses → Doctor availability → COGS migration
→ Consultations → Today's-Patient todos → Reminders
→ (Waste ‖ Inventory, parallel) → Personal reminders → Vaccination items
```

`cloudRefresh(tag, rethrow = true)` (`core/sync/CloudRefresh.kt:18-31`) **always rethrows a
session-shaped failure** even when the caller asked to swallow errors — so an expired token
aborts the refresh instead of silently leaving stale data.

## Conflict resolution — two mechanisms

### 1. Uploader-side: "who wins when I upload this row"

`core/sync/SyncUploader.kt:486-521`. Requires **all three** conditions to let the remote win:

- **C1** remote `updated_at` strictly newer than local.
- **C2** local `updatedAt` parses to `> 0`.
- **C3** `!isUnsynced(entityName, entityId)`.

⚠️ **C3 is always false in the upload path.** `processGroup` commits the item to `SYNCING`
(`SyncRepositoryImpl.kt:284-288`) *before* calling `uploadEntity` (`:300`), and `isUnsynced`
matches `status != 'SYNCED'` (`SyncQueueDao.kt:65-66`). So the entire timestamp-comparison
block is **dead code**, and `fetchRemoteConflictData`'s batch read is fetched then ignored.

### 2. Pull-side: the real policy

Uniform across every repository, with **no timestamp comparison at all**:

```kotlin
if ((local == null || local.isSynced) && entity.id !in pendingIds) { insert remote }
```

`PatientRepositoryImpl.kt:97`+`:124`, `VaccinationRepositoryImpl.kt:105`+`:125`, and the
same shape in 10 more repositories.

| Local state | Result on pull |
|---|---|
| Row absent | remote wins |
| Row present, `isSynced = true`, nothing queued | **remote wins unconditionally** (even if older) |
| Row present, `isSynced = false` | local wins (skipped) |
| Row present, any queue row for that id | local wins (skipped) |

**Effective rule: a pending local change makes local authoritative until pushed; otherwise
last-puller wins, with no clock comparison.** See [[Known Bugs]] for what this costs.

## Realtime

Only **two** subscription sites, both in `core/sync/RealtimeChangeSubscriptions.kt`:

- `PatientListViewModel.kt:110-112` — channel `"patients-db-changes"`, tables `patients`,
  `patient_visits`, `consultations`, `.debounce(500)` → full `refreshDataUseCase()`.
- `DashboardViewModel.kt:207-208` — channel `"todays-patients-db-changes"`, tables
  `consultation_todos`, `vaccination_todos` → `patientTodoRepository.refresh()`.

Events **do** trigger refreshes — but a refresh is a **pull**, never a write-back. Setup
removes any pre-existing channel with the same name first (`:39-41`); teardown runs in
`awaitClose`.

⚠️ Each table flow has `.catch { }` (`:52-54`) that **permanently terminates that
subscription on first error with no resubscribe**. The screen keeps a live-looking
ViewModel with a dead channel.

## The offline write path, proven

**Add patient** — `feature/patient/data/PatientRepositoryImpl.addPatient` (`:142-191`):

1. `database.withTransaction { }` (`:148`) — mint clinic ID (`:149-159`), insert with
   `isSynced = false` (`:168`), **and enqueue for sync in the same transaction** (`:172-177`).
2. Outside: `auditLogger.recordLog(...)` — local-only, fully try/caught (`AuditLogger.kt:59-90`).
3. `patientCache.invalidate(patient.id)` (`:190`).

No `Postgrest`/`Auth`/`Realtime` call anywhere. Same shape for `deletePatient` (`:193-251`),
`addVaccination` (`VaccinationRepositoryImpl.kt:258-353`) and `deleteVaccination` (`:387-504`).

**Sign-out guard** — `AuthViewModel.logout` (`:296-312`) blocks sign-out while
`syncQueueDao.getUnacknowledgedCountSync() > 0`, forcing explicit consent before erasing.

## Confirmed bugs in this layer

Ranked in [[Known Bugs]]. The three that matter most:

1. **`SingleFlight` wrapper discards the failure result.**
   `SyncRepositoryImpl.kt:103-107` — a waiter never assigns its captured `anyFailed`, so it
   always returns `false`, and `SyncWorker.kt:37-40` resets `failure_count` and returns
   `Result.success()`. The "Some changes could not be synced" alert never fires for a
   coalesced call.
2. **Unbounded WorkManager chain growth.** `SyncManagerImpl.kt:32-36` uses
   `ExistingWorkPolicy.APPEND_OR_REPLACE`, and `scheduleSync()` fires **per queued row**.
   One `deletePatient` can enqueue dozens of rows (`:206-239`) inside one transaction.
   Offline, those requests accumulate; on reconnect the whole chain runs sequentially.
3. **The uploader's conflict branch is unreachable** (above).

## Related

- [[Caching]] — the in-memory layers that make reads instant offline
- [[Room Database]] — `sync_queue` and every other local table
- [[Supabase]] — the server side that receives these writes
- [[Known Bugs]] — full defect list
- [[Architecture Decisions]] — why the queue is last-write-wins
