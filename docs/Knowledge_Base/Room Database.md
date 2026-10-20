# Room Database

> Local persistence as implemented. Version 33, 25 entities, 21 DAOs, 16 registered
> migrations. Related: [[Database Schema]] · [[Caching]] · [[Testing]] · [[Known Bugs]]

## Identity

`data/local/database/AppDatabase.kt:14-46`:

```kotlin
@Database(
    entities = [ PatientEntity::class, VisitEntity::class, /* …25 total… */ BackupHistoryEntity::class ],
    version = 33,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase()
```

| Property | Value | Source |
|---|---|---|
| Version | **33** | `AppDatabase.kt:42` (mirrored as `DB_VERSION` at `:79`) |
| File name | `neochild_db` | `:75` |
| Schema export | `$projectDir/schemas` | `app/build.gradle.kts:29-31` |
| Journal mode | `TRUNCATE` (not WAL) | `:508` |
| Encryption | SQLCipher `SupportOpenHelperFactory` | `:500`, SQLCipher 4.19.0 |
| Destructive fallback | **None, deliberately** | `:510-511` |

The passphrase comes from `SecurityUtils.getDatabasePassphrase(context)` (`:447`), a random
UUID in `EncryptedSharedPreferences`. A Keystore failure **throws** rather than silently
opening plaintext (`:448-451`).

A corrupt or undecryptable database is **moved aside, never deleted**
(`:464-497`) — sidecar files (`-journal`, `-wal`, `-shm`) are renamed before the main file,
so a partial rename destroys nothing, and a rename failure throws instead of wiping.

> ⚠️ **The floor is version 17.** There is no 16→17 migration, so a device below v17 crashes
> on open. This is asserted deliberately in `AppDatabaseMigrationTest.kt:17-21`.

## Migrations — 16, contiguous, no gaps

`AppDatabase.kt:435-440` registers `migration17_18` … `migration32_33`.

| From → To | What it does | Line |
|---|---|---|
| 17→18 | `ADD COLUMN created_by`/`updated_by` to 13 tables | `:84-97` |
| 18→19 | creates `consultation_todos`, `vaccination_todos` + indices | `:99-110` |
| 19→20 | creates `personal_vaccine_reminders` + 4 indices | `:112-142` |
| 20→21 | creates `borrow_returns` + 2 indices | `:144-165` |
| 21→22 | **rebuilds** `personal_vaccine_reminders` (rename→copy→drop) | `:167-178` |
| 22→23 | creates `expenses` + 5 indices | `:180-208` |
| 23→24 | **intentional no-op** — shipped in one step | `:332-334` |
| 24→25 | adds slot/doctor columns; creates doctor availability tables | `:217-268` |
| 25→26 | creates `backup_history` + 3 indices | `:273-295` |
| 26→27 | index-only | `:302-311` |
| 27→28 | **drops** `expenses.isDeleted`, `doctor_slot_exceptions.is_deleted` | `:315-324` |
| 28→29, 29→30 | `addSlotMinuteColumnsIfMissing` (idempotent helper) | `:356-366` |
| 30→31 | adds `reminders.is_deleted`/`deleted_at`/`deleted_by` | `:368-383` |
| 31→32 | **hard-deletes then drops** the soft-delete trio on **18 tables** | `:390-415` |
| 32→33 | index-only: `index_patient_visits_dateGiven` | `:423-430` |

Three of these are destructive-by-design (21→22 table rebuild, 27→28 column drops,
31→32 mass delete + 54 column drops). Only one — 32→33 — has any test, and that test
re-implements the migration rather than running the real one. See [[Testing]].

## Entities — 25 classes across 21 files

| Table | PK | Notable | Soft-delete? |
|---|---|---|---|
| `patients` | `id` String | `patientClinicId` (unique index) | ❌ |
| `patient_visits` | `id` String | FK→`patients` CASCADE, `dateGiven`, `receiptNumber` | ❌ |
| `reminders` | `id` String | FK→`patients` CASCADE, unique composite idx, `nxtVaccineId` as JSON | ✅ **the only one** |
| `vaccines` | `id` String | `brandName`, `mrp`, `netRate` — **no indices at all** | ❌ |
| `vaccine_batches` | `batchId` String | FK→`vaccines` CASCADE, `remainingQuantity` + 4 bucket counters | ❌ |
| `inventory_transactions` | `transactionId` Long auto | **no FKs** despite 3 references | ❌ |
| `inventory_deductions` | `id` Long auto | **no FK** to `patient_visits`; **never synced** | ❌ |
| `sync_queue` | `queueId` Long auto | **no unique index** on the dedup key | ❌ |
| `waste_records` | `id` String | no FKs (acknowledged, `VaccineDao.kt:118-121`) | ❌ |
| `widget_due_cache` | `id` Int auto | derived cache only | ❌ |
| `audit_logs` | `id` String | 6 single-column indices, **no composite** | ❌ |
| `patient_notes` | `id` String | FK→`patients` CASCADE | ❌ |
| `profiles` | `id` String | `email` unique index | ❌ |
| `finance_transactions` | `id` String | **no FKs** despite `visitId`/`patientId` | ❌ |
| `borrow_records` | `id` String | FKs→`vaccines` and `vaccine_batches` CASCADE | ❌ |
| `consultations` | `id` String | FKs→`patient_visits`, `patients` CASCADE; `visitId` defaults to `""` | ❌ |
| `vaccination_items` | `id` String | 3 FKs CASCADE + denormalised `vaccineName`/`batchNumber` | ❌ |
| `consultation_todos` | `id` String | **no FK** to `patients` | ❌ |
| `vaccination_todos` | `id` String | **no FK** to `patients` | ❌ |
| `personal_vaccine_reminders` | `id` String | `patient_id` FK CASCADE, `vaccine_id` FK SET NULL | ❌ |
| `borrow_returns` | `id` String | FKs→`borrow_records`, `vaccine_batches` CASCADE | ❌ |
| `expenses` | `id` String | **`amountPaise: Long`** — integer paise, not Double | ❌ (dropped in 27→28) |
| `doctor_weekly_slots` | `id` String | soft-delete **emulated** by `is_active = false` | ⚠️ |
| `doctor_slot_exceptions` | `id` String | minute-of-day slots | ❌ (dropped in 27→28) |
| `backup_history` | `id` String | local-only, never synced | ❌ |

`typealias VaccinationEntity = VisitEntity` (`VaccinationEntity.kt:77`) — which is why the
`@Database` annotation lists `VisitEntity`.

### Soft-delete is vestigial

✅ **Exactly one table** still carries soft-delete columns at HEAD: `reminders`.
✅ **Nothing in `app/src/main` ever writes `is_deleted = 1`.** Verified by exhaustive grep —
the only occurrence is the `DELETE FROM` inside `migration31_32` (`AppDatabase.kt:408`).

`migration31_32` hard-deletes soft-deleted rows on 18 tables but **deliberately excludes
`reminders`** (`:392-399`), because HEAD keeps the columns there. So a device upgraded from
an old build can carry live `is_deleted = 1` reminder rows into HEAD.

The DAO applies `is_deleted = 0` to **all 16** `DueReminderDao` read queries, matching the
file's own comment (`:10-13`). See [[Known Bugs]].

## DAOs — 21

All are plain `@Dao` interfaces, all exposed as `abstract fun` on `AppDatabase` (`:47-71`)
and provided by Hilt in `core/di/DatabaseModule.kt:39-137`.

| DAO | `@Transaction` methods | Tests |
|---|---|---|
| `PatientDao` | none | ✅ androidTest |
| `VaccinationDao` | 3 (read/`@Relation`) | ✅ androidTest |
| `DueReminderDao` | 5, all single-query wrappers | ✅ androidTest |
| `VaccineDao` | none | ❌ |
| `SyncQueueDao` | none | ❌ |
| `WasteDao` | none | ❌ |
| `WidgetDueDao` | 1 (genuine 2-statement swap) | ❌ |
| `AuditLogDao` | none | ❌ |
| `FinanceDao` | none | ❌ |
| `ExpenseDao` | none | ✅ androidTest |
| `BorrowDao` | none | ❌ |
| `BorrowReturnDao` | none | ❌ |
| `PatientNotesDao` | none | ❌ |
| `PatientTodoDao` | none | ❌ |
| `PersonalReminderDao` | none | ❌ |
| `ProfileDao` | none | ❌ |
| `InventoryDeductionDao` | none | ❌ |
| `ConsultationDao` | none | ❌ |
| `VaccinationItemDao` | none | ✅ androidTest |
| `DoctorAvailabilityDao` | none | ❌ |
| `BackupDao` (23 tables) | none — relies on `BackupRestorer` | ❌ |

**5 of 21 DAOs have any test (24%), and every DAO test is `androidTest`, which is not part
of `./gradlew test`.**

## Type converters

`Converters.kt` is 17 lines and registers exactly **one** converted type: `List<String>?` ↔
JSON string. Registered twice — on the database (`AppDatabase.kt:45`) and on `ReminderEntity`
(`ReminderEntity.kt:14`).

The entire JSON-in-column surface of the database is **one column**:
`reminders.nxt_vaccine_id`. It is queried by delimited `LIKE`
(`DueReminderDao.kt:101`), which cannot use an index.

Three other columns hold delimited strings built by hand, not by a converter:
`reminders.vaccineName` (comma-joined), and `patient_visits.vaccineNames` / `vaccineIds` /
`batchNumbers`. The latter are queried with **unanchored** `LIKE '%' || :id || '%'`
(`VaccineDao.kt:108`) — a substring match that can false-positive on one id being a prefix
of another.

## Transactions

`TransactionRunner` (`core/database/TransactionRunner.kt`) is a thin wrapper over Room's
`withTransaction`, bound in `core/di/RepositoryModule.kt:70`. Nesting is safe because Room
reuses the dispatcher.

13 classes consume it — 9 repositories plus 4 domain engines/services. The best-transacted
write is `ClinicalVaccinationService.recordVaccination` (`:52-122`), which covers **9 tables** —
`patient_visits`, `vaccination_items`, `finance_transactions`, `reminders`,
`vaccine_batches`, `inventory_transactions`, `inventory_deductions`, `sync_queue`,
`audit_logs`.

### Multi-table writes that escape a transaction

| Location | Why |
|---|---|
| `PersonalReminderRepositoryImpl` — all 6 mutators | repository has **no** `TransactionRunner` dependency |
| `DoctorAvailabilityRepositoryImpl` — all 4 mutators | same |
| `InventoryRepositoryImpl.deductStock` (`:384-433`) | `for` loop at `:405-421` with no transaction — but **dead code**, zero callers |
| `AuditLogger.log(...)` called from inside live transactions | fires on a **separate** coroutine (`AuditLogger.kt:40-42`), so the audit row survives a rollback |

Two entries formerly in this table have since been fixed and are kept for history:
`InventoryRepositoryImpl.addStockBatch` now wraps its whole validation/write loop in
`transactionRunner.run` (`:250`, NCC-018), and the `AddVaccinationViewModel` reminder loop is
gone — reminders are passed into `recordVaccination` as `reminderSpecs` and written inside its
transaction (`AddVaccinationViewModel.kt:685-695`, NCC-004).

## Confirmed findings

See [[Known Bugs]] for the full list with severity. Room-specific:

1. ✅ Re-checked at HEAD: `DueReminderDao` applies `is_deleted = 0` to all 16 read queries,
   matching the DAO's own stated rule — the earlier "7 of 16" observation no longer holds.
2. ✅ `AuditLogDao.kt:30-33` comment claims a composite `(entityType, entityId)` index that
   **does not exist**; the entity declares 6 single-column indices only.
3. ✅ `VaccineDao.getAuditCountForVaccine` (`:147-148`, sibling at `:150-151`) queries
   `audit_logs.remarks` for a brand name — and `remarks` is exactly where the brand is written
   (`InventoryRepositoryImpl.kt:171`). Both methods have **zero call sites** — the deletion
   guard counts vaccinations, waste and transactions (`InventoryRepositoryImpl.kt:364-366`),
   not audit rows.
4. ✅ 7 of the 9 `FinanceDao` read queries order by
   `COALESCE(transaction_date, substr(timestamp,1,10)) DESC` — an expression that **no
   B-tree index can serve**, making `index_finance_transactions_transaction_date` useless.
5. ✅ `consultations.visitId` is `NOT NULL` with a CASCADE FK but defaults to `""` in Kotlin
   (`ConsultationEntity.kt:33`) — a latent FK-violating default. No live path reaches it.

## Related

- [[Database Schema]] — the Postgres side and the client/server drift
- [[Caching]] — what sits in front of these tables
- [[Testing]] — 16 of 21 DAOs untested, all DAO tests are instrumented
- [[Known Bugs]] — full defect list
- [[Backup and Restore]] — `BackupDao` is a second DAO over 23 of these tables
