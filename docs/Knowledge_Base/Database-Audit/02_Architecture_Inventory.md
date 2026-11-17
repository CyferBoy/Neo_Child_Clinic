# Architecture Inventory

## 1. Repository and build

| Item | Value | Evidence |
| --- | --- | --- |
| Module | single `:app` | `settings.gradle.kts` |
| Package / namespace | `com.neochildclinic` | `app/build.gradle.kts` |
| Language/UI | Kotlin, Jetpack Compose, Material 3 | source tree |
| DI | Hilt | `@HiltAndroidApp`, `@HiltViewModel`, `@Singleton` |
| Local DB | Room, schema **v33** | `AppDatabase.kt`, `app/schemas/.../33.json` |
| DB encryption | SQLCipher + Android Keystore passphrase | `AppDatabase.kt:442-460`, `SecurityUtils` |
| Backend | Supabase (PostgREST, Auth, Realtime, Storage, Edge Functions) | repositories, `supabase/` |
| Background | WorkManager (`SyncWorker`, widget, backups, notifications, migrations) | `core/sync`, `data/backup`, `feature/widget` |
| SDK | min 29, target 35, compile 37 | `app/build.gradle.kts:35-40` |
| Kotlin files | 352 main, 41 unit test, 8 androidTest | source count |
| SQL migrations | 23 files | `supabase/migrations/` |

Only `33.json` is exported. Migration tests cannot reconstruct v31/v32 schemas from the repository alone; `MigrationTestHelper` can only validate a migration when given the matching exported schema.

## 2. Layering

```text
Compose screen
  └── Hilt ViewModel (StateFlow / Flow)
        └── domain use case or engine (transaction orchestration)
              └── repository interface
                    └── repository implementation
                          ├── Room DAO  ──► SQLCipher DB
                          ├── SyncRepository.enqueue ──► sync_queue
                          └── PostgREST (pull or online-only write)
```

- **Presentation:** `feature/<name>/presentation`; screens collect Room-backed Flows.
- **Domain:** `feature/<name>/domain`; engines include `VaccinationEditEngine`, `ConsultationEditEngine`, `ClinicalVaccinationService`, `FinanceCalculator`, `ClinicStatsManager`, `RefreshDataUseCase`.
- **Data:** `feature/<name>/data`; Room access through `data/local/dao`; remote access through `Postgrest` or `PatientRemoteDataSource`.
- **Core:** `core/sync` (queue/upload/pull), `core/security` (session/role/DB passphrase), `core/common` (dates, age, JSON), `core/cache`, `core/notification`, `core/database` (`TransactionRunner`).

`TransactionRunner` is the single abstraction for Room transactions; repositories call it even when already inside an outer transaction (Room transactions are re-entrant).

## 3. Room inventory (25 entities, v33)

Source of truth: `app/schemas/com.neochildclinic.data.local.database.AppDatabase/33.json` and entity declarations.

### Synced business tables

| Local table | Local PK | Key FKs | Notes |
| --- | --- | --- | --- |
| `patients` | `id` | — | unique `patientClinicId`; cache-backed by `PatientCache` |
| `patient_visits` | `id` | `patientId` → patients CASCADE | receipt number, totals, next-due mirror |
| `vaccination_items` | `id` | visit + vaccine/batch (CASCADE) | one row per vaccine/batch |
| `reminders` | local UUID | `patientId` CASCADE; no visit FK | `serverId` maps remote identity; keeps soft-delete trio |
| `personal_vaccine_reminders` | `id` | patient CASCADE, vaccine SET NULL | optional patient |
| `consultations` | `id` | patient CASCADE, visit CASCADE | consultation visit header shares `patient_visits` |
| `consultation_todos` / `vaccination_todos` | `id` | — | dashboard day list |
| `finance_transactions` | `id` | patient/visit (nullable) | deterministic id for visit income |
| `expenses` | `id` | — | hard delete |
| `vaccines` | `id` | — | catalog |
| `vaccine_batches` | `batchId` | vaccine CASCADE | `remainingQuantity` local mirror |
| `inventory_transactions` | `transactionId` | batch/vaccine/visit fields | append-only ledger; **no pull path** |
| `waste_records` | `id` | vaccine/batch | waste + stock movement |
| `borrow_records` / `borrow_returns` | `id` | vaccine/batch, borrow record | partial returns |
| `doctor_weekly_slots` / `doctor_slot_exceptions` | `id` | — | scheduling |
| `profiles` | `id` | — | staff directory; role is **not** read for authorization |
| `audit_logs` | `id` | — | local staging + append-only remote |
| `patient_notes` | `id` | patient | documents metadata |

### Local-only tables

| Table | Purpose | Remote? |
| --- | --- | --- |
| `inventory_deductions` | per-visit, per-batch deduction audit used for edit/delete reversal | no |
| `sync_queue` | transactional outbox | no |
| `widget_due_cache` | precomputed widget rows | no |
| `backup_history` | backup metadata | no |

## 4. Sync components

| Component | Responsibility | Evidence |
| --- | --- | --- |
| `SyncRepositoryImpl` | transactional enqueue, dedupe attempt, queue processing, stale `SYNCING` recovery, single-flight | `core/sync/SyncRepositoryImpl.kt` |
| `SyncUploader` | entity descriptor registry, conflict read, PostgREST upsert/delete, receipt read-back | `core/sync/SyncUploader.kt` |
| `SyncWorker` | periodic/immediate upload worker | `core/sync/SyncWorker.kt` |
| `SyncReconciler` | on startup/reconnect: push then pull, both single-flight | `core/sync/SyncReconciler.kt` |
| `RefreshDataUseCase` | full inbound pipeline (11 repositories, 13 steps), per-step error aggregation | `feature/sync/domain/RefreshDataUseCase.kt` |
| `CloudRefresh` | IO wrapper; always rethrows auth failures, optionally rethrows others | `core/sync/CloudRefresh.kt` |
| `SingleFlight` | one in-process run per key; coalesces global pull/upload | `core/sync/SingleFlight.kt` |
| `RealtimeChangeSubscriptions` | Supabase postgres-change Flow, cancellation-safe | `core/sync/RealtimeChangeSubscriptions.kt` |
| `WidgetWorker` | rebuild widget cache after writes/receipts | `feature/widget` |

### Upload registry (24 descriptors)

`PATIENT`, `VACCINATION`, `VISIT`, `VACCINATION_ITEM`, `WASTE`, `REMINDERS`, `VACCINE`, `BATCH`, `TRANSACTION`, `INVENTORY_TRANSACTION`, `PATIENT_NOTE`, `FINANCE`, `EXPENSE`, `PROFILE`, `STAFF`, `BORROW`, `BORROW_RETURN`, `AUDIT_LOG`, `CONSULTATION`, `CONSULTATION_TODO`, `VACCINATION_TODO`, `PERSONAL_REMINDER`, `DOCTOR_WEEKLY_SLOT`, `DOCTOR_SLOT_EXCEPTION`.

Four descriptors have no `download` function: `TRANSACTION` and `INVENTORY_TRANSACTION` (both `inventory_transactions`), plus `PROFILE` and `STAFF` (both `profiles`). The default downloader is a no-op (`SyncUploader.kt:45`).

### Background workers

| Worker | Role | DB access |
| --- | --- | --- |
| `SyncWorker` | periodic/immediate outbox upload | `sync_queue`, all synced entities |
| `WidgetWorker` | rebuild `widget_due_cache` after writes | reminders, visits, patients, batches |
| `AutoBackupWorker` | scheduled local/cloud backup | backup collector (all business tables), `backup_history` |
| `PatientClinicIdMigrationWorker` | one-time legacy clinic-id assignment | patients + enqueue PATIENT UPDATE + audit |
| `DailySummaryWorker` | daily notification summary | reminders/visits (read) |
| `PersonalReminderNotificationWorker` | personal reminder notifications | personal_vaccine_reminders (read) |

## 5. Inbound refresh pipeline

`RefreshDataUseCase` pulls, in order (parent before child):

1. patients
2. vaccinations (visit headers)
3. finance
4. expenses
5. doctor availability
6. legacy COGS migration (depends on steps 2–3)
7. consultations
8. patient todos
9. reminders
10. waste (parallel)
11. inventory — vaccines + batches (parallel with waste)
12. personal reminders (after inventory for the vaccine FK)
13. vaccination items (after inventory)

Borrows are **not** in the global pipeline: they are pulled only by `BorrowedViewModel` when that screen opens. Each step is wrapped so one failure is reported without aborting the rest; auth failures still surface. The pipeline is single-flight. Screen-specific repository refreshes are **not** part of that flight.

## 6. Outbound lifecycle

1. Business method opens a Room transaction.
2. Entity write + `syncRepository.enqueue(entityName, entityId, operation, priority, groupId?)`.
3. Commit. UI observes the local row immediately.
4. `SyncManager` schedules expedited upload; periodic `SyncWorker` is the safety net.
5. `SyncRepositoryImpl` claims a group: priority DESC, then `createdAt`; items sharing a `transactionGroupId` stay in enqueue order.
6. `SyncUploader` fetches the current local row, reads the remote row for conflict detection (for descriptors that support it), then upserts/deletes.
7. Success deletes the queue row and marks `isSynced`; failure is classified permanent vs retryable.

## 7. Supabase surface

- 23 SQL migrations in `supabase/migrations/`, plus a schema catalog CSV.
- Edge functions: `manage-staff` (admin-only staff provisioning with service role) and `notify-update`.
- Tables present remotely include every synced business table plus `receipt_sequences`.
- Server-side invariants: receipt numbering, stock trigger, RLS baseline, write rate limit, profile privileged-field guard.
- `supabase/tests/` exists but its coverage was not executed here.

## 8. Online-only paths

| Path | Why it matters |
| --- | --- |
| Stock History (`remoteOnly=true`) | screen cannot work offline; ledger never cached |
| Full Audit History | intentionally remote-only; local audit staging is not read |
| Auth profile bootstrap (`fetchProfileFromRemote`) | first login needs network |
| `user_devices` registration | direct PostgREST, no queue |
| Patient documents | Supabase Storage, no local metadata table |
| Staff management | edge function, no local write |

These are documented exceptions, not accidental bypasses of Room for the main clinical lists.

## 9. Documentation drift

Existing docs (`docs/SYNC.md`, `docs/Knowledge_Base/Offline-First Architecture.md`, Bug Register) describe several behaviors that no longer match HEAD:

- “clear visit link before visit delete” — the step is gone (`VaccinationRepositoryImpl.kt:429-430`).
- “NCC-002/NCC-003 fixed” — the UPDATE arithmetic in `20261017` is still wrong.
- soft-delete descriptions refer to both the removed local soft-delete scheme and the server columns added by `20260925`.

Treat this audit's file:line evidence as the current state.
