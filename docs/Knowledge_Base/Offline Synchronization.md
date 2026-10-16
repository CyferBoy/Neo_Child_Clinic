# Offline Synchronization

> The sync machinery in depth: triggers, queue, upload, pull, conflicts, retries,
> realtime, and where it breaks. Verified against source.
> Companion: [[Offline-First Architecture]] (narrative) · [[Data Flow]] (runtime)
> · [[Caching]]

## The guarantee

✅ A user can add, edit and delete a patient or a vaccination **with no network at all**.
Proven by trace: no path from a write to the database touches `Postgrest`, `Auth` or
`Realtime`.

## Trigger map

```mermaid
flowchart TD
    subgraph always["Always enqueued"]
        P1["15-min periodic<br/>SyncWorker · CONNECTED · KEEP"]
        P2["App start reconciler<br/>push THEN pull"]
        P3["onCreate + onResume<br/>scheduleSync"]
    end
    subgraph event["Event-driven"]
        E1["Every queue write<br/>SyncRepositoryImpl.kt:84"]
        E2["After login<br/>scheduleImmediateSync"]
        E3["After backup restore"]
        E4["Transient failure /<br/>session deferral"]
    end
    subgraph reactive["Reactive"]
        R1["Offline → online<br/>SyncReconciler.kt:57-63"]
        R2["Realtime postgres event<br/>PatientList + Dashboard"]
        R3["Pull-to-refresh<br/>SyncScreen + Dashboard"]
        R4["Sign-out flush<br/>20s bounded"]
    end
    always --> SYNC["processNextItems()"]
    event --> SYNC
    reactive --> SYNC
```

## Upload sequence

```mermaid
flowchart TD
    A["SyncWorker.doWork"] --> B{"SingleFlight held?"}
    B -->|waiter| Z["return await result<br/>⚠️ wrapper discards it"]
    B -->|owner| C["cleanCorruptedItems"]
    C --> D["requeueStale SYNCING<br/>older than 5 min"]
    D --> E["getItemsByStatus PENDING"]
    E -->|empty| IDLE["IDLE"]
    E --> F["SYNCING"]
    F --> G{"Session resolved?"}
    G -->|not authenticated| DEF["IDLE, no retry"]
    G -->|timeout / refresh failure| SCH["scheduleSync, return"]
    G -->|ok| H["orderPendingIntoGroups"]
    H --> I{"per group"}
    I --> J["all items → SYNCING<br/>one transaction"]
    J --> K["fetchRemoteConflictData<br/>one SELECT per table"]
    K --> L["uploadEntity per item<br/>sequentially"]
    L -->|401| M["refresh + retry once"]
    L -->|ok| N["markUploaded then DELETE row"]
    L -->|transient r&lt;5| PB["back to PENDING"]
    L -->|permanent / r=5| FL["markFailed, stays queued"]
    M --> L
    N --> R{"result"}
    PB --> R
    FL --> R
    R -->|error| ERR["ERROR + scheduleSync"]
    R -->|clean| IDLE
```

## Queue mechanics

`sync_queue` columns (`data/local/entity/SyncQueueEntity.kt:11-27`):
`entityName`, `entityId`, `operation`, `priority`, `status`,
`transactionGroupId`, `retryCount`, `lastError`, `createdAt`, `updatedAt`.

| Fact | Detail |
|---|---|
| Ack mechanism | the row is **deleted**, never written as `SYNCED` (`SyncQueueDao.kt:58-63`) |
| Dedup | `deletePendingDuplicates` + `@Insert(REPLACE)` — ⚠️ no unique index, documented `shortcut:` |
| Ordering | `orderPendingIntoGroups` (`:679-700`), re-sorted deterministically |
| Retry cap | **5 attempts**, then `FAILED` |
| Backoff | WorkManager only — **no per-item timer in the queue** |
| `FAILED` rows | retried only manually from the Sync screen |

### Group ordering — why it exists

`sync_queue` orders by `priority DESC` on a **TEXT** column, so `"HIGH"` sorts **last**
(`SyncQueueDao.kt:12`). Harmless, because `orderPendingIntoGroups` re-sorts:

1. group by `transactionGroupId ?: UUID.randomUUID()`
2. sort groups by `(min sortKey, min createdAt)`, negating the sort key for `DELETE`
3. within a group: exact enqueue order by `(createdAt, queueId)`

The negation exists because a server-side FK (`finance_transactions_visit_id_fkey`) rejects
a parent delete while children still reference it — so **children unlink before parents
delete**. This is why every delete path enqueues the parent last. See
[[Architecture Decisions]] §5.

Pinned by `SyncOrderingTest` (6 tests).

## Pull sequence

`RefreshDataUseCase.runRefresh()` (`:51-127`) — 14 steps in FK-safe order, each wrapped in
`runRefreshStep` so one failure does not abort the rest; errors aggregate and throw at the
end.

```
patients → vaccinations → finance → expenses → doctor availability
→ COGS migration → consultations → today-patient todos → reminders
→ (waste ‖ inventory, parallel) → personal reminders → vaccination items
```

✅ `CloudRefresh.cloudRefresh(tag, rethrow = true)` **always rethrows a session-shaped
failure** even when the caller asked to swallow errors — an expired token aborts the
refresh rather than silently leaving stale data. Covered by `CloudRefreshTest` (6).

## Conflict resolution — two mechanisms, one reachable

```mermaid
flowchart TD
    subgraph upload["Upload-time (UNREACHABLE)"]
        U1{"remote newer?"} -->|yes| U2{"isUnsynced?"}
        U2 -->|"false"| DL["descriptor.download<br/>REMOTE WINS"]
        U2 -->|"true — always here"| UP["upsert local<br/>LOCAL WINS"]
    end
    subgraph pull["Pull-time (the real policy)"]
        P1{"local missing?"} -->|yes| PI["INSERT remote<br/>REMOTE WINS"]
        P1 -->|no| P2{"local isSynced?"}
        P2 -->|false| PK["SKIP<br/>LOCAL WINS"]
        P2 -->|true| P3{"queue row?"}
        P3 -->|yes| PK
        P3 -->|no| PO["OVERWRITE<br/>REMOTE WINS even if older"]
    end
```

⚠️ The upload-time branch requires `!isUnsynced(...)` (`SyncUploader.kt:514-516`), but
`processGroup` commits the item to `SYNCING` **before** uploading
(`SyncRepositoryImpl.kt:284-288` → `:300`), and `isUnsynced` matches `status != 'SYNCED'`.
So that whole block is dead code. See [[Known Bugs]] §3.2.

**Effective policy**: a pending local change makes local authoritative until pushed;
otherwise **last puller wins, with no clock comparison.**

## Retry and failure classification

`isTransientSyncError` (`SyncRepositoryImpl.kt:585-603`):

| Condition | Classification |
|---|---|
| `IOException`, `HttpRequestException` | transient |
| `RestException` 408 / 429 / 5xx | transient |
| 401 | handled earlier — refresh + one inline retry, never classified |
| anything else | **permanent** → `markFailed` immediately |

⚠️ `check_violation` (the stock trigger's negative guard) arrives as HTTP **400** →
permanent → **the whole sync group is marked `FAILED`**. The visit never reaches the server
and nothing reconciles it automatically.

## Realtime

Only two subscription sites, both in `core/sync/RealtimeChangeSubscriptions.kt`:

| ViewModel | Channel | Tables | Action |
|---|---|---|---|
| `PatientListViewModel.kt:110-112` | `patients-db-changes` | `patients`, `patient_visits`, `consultations` | `.debounce(500)` → full `refreshDataUseCase()` |
| `DashboardViewModel.kt:207-208` | `todays-patients-db-changes` | `consultation_todos`, `vaccination_todos` | `patientTodoRepository.refresh()` |

```mermaid
flowchart LR
    PG["postgres INSERT/UPDATE/DELETE"] --> CH["Supabase Realtime channel"]
    CH -->|"callbackFlow"| F["one collector per table"]
    F --> D[".debounce(500)"]
    D --> R["pull refresh<br/>never a write-back"]
    CH -.->|"awaitClose"| RM["removeChannel<br/>then cancel scope"]
```

✅ Setup removes any pre-existing channel with the same name first (`:39-41`).
✅ Teardown ordering is correct and documented (`:64-78`).

⚠️ `.catch { }` on each table flow (`:52-54`) **permanently terminates that subscription on
first error with no resubscribe** — the screen keeps a live-looking ViewModel with a dead
channel.

## Cache interaction

Three layers, all in front of Room. Room invalidation of table `patients` clears
`PatientCache` (`PatientRepositoryImpl.kt:66-68`); a refresh clears it wholesale (`:139`).
See [[Caching]].

## Known failure modes

| # | Failure | Mechanism |
|---|---|---|
| 2.3 | waiter always reports success | `SyncRepositoryImpl.kt:103-107` captures into a local instead of using the returned value |
| 3.1 | unbounded work chain | `APPEND_OR_REPLACE` + per-row scheduling |
| 3.2 | conflict branch unreachable | status committed before upload |
| 3.5 | `priority DESC` on TEXT sorts HIGH last | cosmetic, re-sorted downstream |
| 3.6 | pull never tombstones | deleted rows resurrect from the queue |
| 3.7 | realtime dies permanently | `.catch {}` without resubscribe |
| — | items stuck `SYNCING` invisible to the counter | `getPendingCount()` counts only `PENDING` |
| — | `Result.failure()` cancels appended dependents | next sync delayed ≤15 min |

All detailed in [[Known Bugs]] and the register at [[Quality/Bug Register]].

## Related

- [[Offline-First Architecture]] — the same system, narrated
- [[Data Flow]] — runtime paths
- [[Caching]] — the read-side acceleration
- [[Room Database]] — `sync_queue` lives here
- [[Quality/Bug Register]] — every sync defect with status
