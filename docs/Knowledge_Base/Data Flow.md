# Data Flow

> What actually happens at runtime, for each class of operation. Verified paths only.
> Companion: [[Offline Synchronization]] (the machinery) · [[Data Flow]] → [[Architecture Map]]

## Read paths — never touch the network

```mermaid
flowchart LR
    SCR["Screen"] -->|"collectAsState()"| VM["ViewModel"]
    VM -->|"combine + stateIn"| REPO["Repository"]
    REPO --> DAOF["DAO @Query"]
    DAOF --> DB[("Room / SQLite")]
    CACHE["PatientCache"] -.->|"miss only"| REPO
    DB -.->|"Room invalidation<br/>on table patients"| CACHE
    DB -->|"emission"| VM --> SCR
```

✅ Every list screen reads a live Room `Flow`. A read is network-independent by
construction — the repository returns `Flow<T>` from the DAO and never awaits HTTP.

| Screen | Source |
|---|---|
| Patient list | `PatientRepositoryImpl.kt:86` `allPatients` Flow |
| Dashboard | 4-level `combine` over `ClinicStatsManager` (`DashboardViewModel.kt:223-299`) |
| Due list | `ReminderDueListProcessor.process()` |
| Inventory | `InventoryRepositoryImpl.getInventoryItems` (`getAllVaccines` + `getAllBatches`) |
| Statistics | `FinanceCalculator` / `ExpenseCalculator` over DAO Flows |

### Cache read-through

`PatientCache` (`feature/patient/data/PatientCache.kt`) is the only domain-object cache.
A miss falls through to Room (`PatientCacheTest` covers it). It is invalidated on Room
invalidation of **table `patients` only** — so a vaccination edit does not clear it.

## Write paths — the canonical shape

```mermaid
flowchart TD
    UI["ViewModel.saveX()"] --> TX[["Room transaction"]]
    TX --> W["write entity<br/>isSynced = false"]
    TX --> E["syncRepository.enqueue(...)"]
    TX --> A["auditLogger.recordLog(...)"]
    TX --> D["domain side-effects"]
    D --> INV["stock decrement"]
    D --> FIN["finance row"]
    D --> REM["reminder rows"]
    TX --> COMMIT[["COMMIT"]]
    COMMIT --> CACHE["invalidate cache"]
    COMMIT --> WM["syncManager.scheduleSync()"]
    WM --> WG["WorkManager<br/>NetworkType.CONNECTED"]
```

✅ **Atomic**: the row write, the sync-queue enqueue, the audit row and the domain
side-effects all commit together. A crash mid-write leaves nothing partial.

✅ **Offline**: nothing in this path touches `Postgrest`, `Auth` or `Realtime`.

The best example is `ClinicalVaccinationService.recordVaccination`
(`:52-122`) — **9 tables** in one transaction.

Two writes that used to escape the transaction are now inside it — kept for history:

| Location | What changed |
|---|---|
| `AddVaccinationViewModel.kt:685-695` | reminder loop removed; reminders pass into `recordVaccination` as `reminderSpecs` (NCC-004) |
| `InventoryRepositoryImpl.addStockBatch` (`:250`) | batch loop now wrapped in `transactionRunner.run` (NCC-018) |

Both are in [[Known Bugs]].

## The seven transactional write paths

| Path | Tables | Transaction |
|---|---|---|
| `recordVaccination` | 9 | ✅ `ClinicalVaccinationService.kt:52` |
| `addPatient` | 2 + queue | ✅ `PatientRepositoryImpl.kt:157` |
| `deletePatient` | cascades + queue | ✅ `:208` |
| `deleteVaccination` | 6 + stock reversal | ✅ `VaccinationRepositoryImpl.kt:392` |
| `recordConsultation` / `editConsultation` | 3 | ✅ `ConsultationEditEngine.kt:23` |
| `addBatch` / `deductStockFromBatch` / `reverseDeduction` | 3 | ⚠️ **caller must wrap** |
| `recordWaste` / `saveBorrowedItem` | 3 | ✅ wrapped by the caller |

## Sync trigger flow

```mermaid
flowchart TD
    T1["queue write"] --> S["scheduleSync"]
    T2["onResume"] --> S
    T3["15-min periodic"] --> S
    S --> WM["enqueueUniqueWork<br/>AUTOMATIC_SYNC<br/>APPEND_OR_REPLACE"]
    WM --> W["SyncWorker.doWork"]
    W --> P["processNextItems"]
    P --> Q[("sync_queue<br/>PENDING rows")]
    Q --> U["SyncUploader<br/>24 descriptors"]
    U -->|upsert/delete| PG[("Postgres")]
```

⚠️ `scheduleSync()` fires **per queued row** and uses `APPEND_OR_REPLACE`, so one
`deletePatient` can chain dozens of work requests. See [[Known Bugs]] §3.1.

## Pull / refresh flow

```mermaid
flowchart TD
    E1["connectivity restored"] --> RD["RefreshDataUseCase.runRefresh"]
    E2["realtime postgres event"] --> RD
    E3["pull-to-refresh"] --> RD
    RD --> SF{{"SingleFlight gate"}}
    SF --> S1["1. patients"]
    S1 --> S2["2. vaccinations"]
    S2 --> S3["3. finance"]
    S3 --> S4["4. expenses"]
    S4 --> S5["5. doctor availability"]
    S5 --> S6["6. COGS migration"]
    S6 --> S7["7. consultations"]
    S7 --> S8["8. today-patient todos"]
    S8 --> S9["9. reminders"]
    S9 --> S10["10. waste ‖ inventory (parallel)"]
    S10 --> S11["11. personal reminders"]
    S11 --> S12["12. vaccination items"]
    S12 --> AGG["aggregate errors, throw at end"]
```

13 steps in FK-safe order (`feature/sync/domain/RefreshDataUseCase.kt:57-127`). Each is
wrapped in `runRefreshStep` so **one failure does not abort the rest**; errors aggregate
and throw at the end.

⚠️ Two pull paths have **no tombstone application** — a row deleted on device B survives on
device A: `ReminderRepositoryImpl.refreshReminders()` and
`PersonalReminderRepositoryImpl.refresh()`. See [[Known Bugs]] §3.6.

## The conflict rule, in one diagram

```mermaid
flowchart TD
    IN["Remote row arrives"] --> Q1{"Local row exists?"}
    Q1 -->|no| INSERT["INSERT with isSynced = true<br/>REMOTE WINS"]
    Q1 -->|yes| Q2{"Local isSynced == true?"}
    Q2 -->|no| KEEP["SKIP<br/>LOCAL WINS (pending change)"]
    Q2 -->|yes| Q3{"Queue row exists for this id?"}
    Q3 -->|yes| KEEP
    Q3 -->|no| OVER["INSERT/OVERWRITE<br/>REMOTE WINS — even if older"]
```

✅ **There is no clock comparison on the pull side.** "Last puller wins" — see
[[Offline Synchronization]] and [[Architecture Decisions]] §4.

## Background work

```mermaid
flowchart LR
    subgraph startup["NeoChildApp.onCreate"]
        A["SyncReconciler.start"]
        B["ReminderScheduler<br/>×2"]
        C["BackupAutoScheduler"]
        D["WidgetWorker"]
        E["PatientClinicIdMigration"]
    end
    A --> SF["push then pull"]
    B --> DSW["DailySummaryWorker · 1h CONNECTED"]
    B --> PRW["PersonalReminder · 24h no constraint"]
    C --> ABW["AutoBackupWorker · 24-168h"]
```

⚠️ Both `ReminderScheduler`'s schedule methods and `BackupAutoScheduler.schedule()` have
**no call site after startup** — settings changes do not reschedule. See [[Known Bugs]]
§2.4 and §2.5.

## Notification flow

```mermaid
flowchart LR
    MUT["any reminder mutation"] --> RUN["ReminderScheduler.runNow()"]
    RUN --> DS["DailySummaryWorker"]
    DS --> ST["getClinicStats + getInventoryItems"]
    ST --> N1["summary notification<br/>id 1001"]
    ST --> N2["low-stock notification<br/>id 1002"]
    DS -.->|"server push"| FU["notify-update<br/>edge function"]
    FU --> FCM["FCM v1 · serial, unbatched"]
    FCM --> DEV["all active devices"]
```

⚠️ `runNow()` is **non-unique**, so N reminder mutations enqueue N workers, each doing a
full stats recompute.

## Backup / restore flow

```mermaid
flowchart TD
    B1["BackupCollector<br/>22 tables, fcmToken stripped"] --> B2["BackupSerializer<br/>JSON envelope + sha256"]
    B2 --> B3["BackupCrypto<br/>PBKDF2 210k + AES-256-GCM"]
    B3 --> B4["PUT → CONFIRM<br/>Cloudflare Worker + R2"]
    B3 --> B5["local file<br/>filesDir/backups/automatic"]

    R1["restore request"] --> R2["stage safety snapshot<br/>Keystore, stage→rename→commit"]
    R2 -->|fails| FAIL["THROW — fail closed"]
    R2 --> R3["decrypt + validate<br/>FKs + duplicate ids"]
    R3 --> R4["BackupRestorer<br/>one transaction"]
    R4 --> R5["commit safety snapshot"]
    R5 --> R6["resync rows where isSynced = false"]
```

## Where flows can break

| Flow | Weakest link | Bug |
|---|---|---|
| Read | `PatientCache` not cleared on vaccination edit | stale object for up to 5 min |
| Write | reminder loop now inside the visit transaction | §1.3 (fixed) |
| Sync push | `SingleFlight` wrapper discards the result | §2.3 |
| Sync pull | no tombstones | §3.6 |
| Realtime | `.catch {}` kills the subscription permanently | §3.7 |
| Stock | lost-update race, no `@Version` | §3.3 |
| Backup | `BackupRestorer` untested, 318 lines | — |

## Related

- [[Architecture Map]] — static structure
- [[Offline Synchronization]] — sync internals
- [[Inventory Management]] — the most consequential write path
- [[Backup and Restore]] — export/restore internals
- [[Known Bugs]] — every flow's failure mode
