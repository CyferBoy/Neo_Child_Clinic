# 07 — Android Database Contracts

**Scope:** the wire contract between Room entities and the Postgres tables: column names, types, nullability, identity, timestamps, soft-delete, and delete semantics.
**Method:** compare every synced entity’s `@SerialName` / Kotlin type with the catalogue CSV and the final migration state. No serialization was executed.
**Status at `93021c2`:** `SQL-024` and `SQL-026` are fixed in the client; no table, column, or SQL contract changed. See `09-final-sql-audit-report.md §4.3`.

---

## 1. Entity ↔ table map

Room version: **33** (`AppDatabase.kt:42`). Only entities listed in `SYNC_ENTITY_REGISTRY` (`SyncUploader.kt:58-360`) cross the wire.

| Entity | Table | PK | Server PK type | Sync descriptor | Soft-delete fields | Timestamp used for conflict |
|---|---|---|---|---|---|---|
| `PatientEntity` | `patients` | `id` | uuid | `PATIENT` | no | `updatedAt` |
| `VisitEntity` | `patient_visits` | `id` | uuid | `VACCINATION` | no | `updatedAt` |
| `VaccinationItemEntity` | `vaccination_items` | `id` | uuid | `VACCINATION_ITEM` | no | none (`""`) |
| `VaccineEntity` | `vaccines` | `id` | uuid | `VACCINE` | no | `lastUpdated` |
| `VaccineBatchEntity` | `vaccine_batches` | `batchId` | uuid (`id`) | `BATCH` | no | `updatedAt` |
| `InventoryTransactionEntity` | `inventory_transactions` | `transactionId` | uuid (`id`) | `TRANSACTION` | no | `timestamp` |
| `WasteEntity` | `waste_records` | `id` | uuid | `WASTE` | no | `updatedAt` |
| `BorrowEntity` | `borrow_records` | `id` | uuid | `BORROW` | no | `borrowedDate` / now |
| `BorrowReturnEntity` | `borrow_returns` | `id` | uuid | `BORROW_RETURN` | no | none |
| `ConsultationEntity` | `consultations` | `id` | uuid | `CONSULTATION` | no | `updatedAt` (text) |
| `FinanceEntity` | `finance_transactions` | `id` | uuid | `FINANCE` | no | none (`""`) |
| `ExpenseEntity` | `expenses` | `id` | **text** | `EXPENSE` | no | `updatedAt` |
| `ReminderEntity` | `reminders` | `id` | uuid | `REMINDERS` | **yes** | `updatedAt` |
| `PersonalReminderEntity` | `personal_vaccine_reminders` | `id` | uuid | `PERSONAL_REMINDER` | no | `updatedAt` |
| `ConsultationTodoEntity` | `consultation_todos` | `id` | uuid | `CONSULTATION_TODO` | no | none |
| `VaccinationTodoEntity` | `vaccination_todos` | `id` | uuid | `VACCINATION_TODO` | no | none |
| `DoctorWeeklySlotEntity` | `doctor_weekly_slots` | `id` | uuid | `WEEKLY_SLOT` | no | `updatedAt` |
| `DoctorSlotExceptionEntity` | `doctor_slot_exceptions` | `id` | uuid | `SLOT_EXCEPTION` | no | `updatedAt` |
| `PatientNotesEntity` | `patient_notes` | `id` | uuid | `NOTE` | no | none |
| `AuditLogEntity` | `audit_logs` | `id` | uuid | `AUDIT_LOG` | no | `timestamp` (text) |
| `ProfileEntity` | `profiles` | `id` | uuid | `PROFILE` / `STAFF` | no | none |

Local-only tables (`inventory_deductions`, `backup_history`, `sync_queue`, `widget_due_cache`) never sync.

---

## 2. Column-name contract

The client uses `@SerialName` for snake_case. The server must expose exactly those names; PostgREST returns `PGRST107`/“column not found” otherwise.

Verified matches:

- `PatientEntity` (`patient_clinic_id`, `alternate_phone`, `registration_date`, `updated_at`, `is_synced`, `created_by`, `updated_by`) ↔ CSV.
- `VisitEntity` (`patient_id`, `date_given`, `doctor_id`, `vaccine_names`, `vaccine_ids`, `batch_ids`, `batch_numbers`, `materials_used`, `receipt_number`, `total_paid`, `payment_id`, `nxt_vaccine_names`, `next_due_date`, `cash_amount`, `online_amount`, `with_fees`, `doctors_acc`, `visit_type`, `inventory_status`, `availability_slot_id`, `created_at`, `updated_at`, `is_synced`, `created_by`, `updated_by`) ↔ CSV.
- `FinanceEntity` (`transaction_date`, `cash_amount`, `online_amount`, `payment_method`, `patient_id`, `visit_id`, `reference_number`, `recorded_by`, `is_synced`, `created_by`, `updated_by`) ↔ CSV.
- `WasteEntity` (`vaccine_id`, `batch_id`, `brand_name`, `batch_number`, `expiry_date`, `date_wasted`, `updated_at`, `is_synced`) ↔ migration `20260919_waste_records_snake_case.sql:22-30`.

**Drift found:** the CSV still lists `waste_records` columns as camelCase (`vaccineId`, `batchId`, `brandName`, `batchNumber`, `expiryDate`, `dateWasted`, `updatedAt`, `isSynced`). The migration and entity use snake_case. Either the CSV predates the rename or the rename was never applied. This is a direct instance of `SQL-002`; the live check is `information_schema.columns`.

---

## 3. Type contract

| Column | Server | Kotlin | Verdict |
|---|---|---|---|
| all `id` except `expenses.id` | `uuid` | `String` | works: local IDs are `UUID.randomUUID().toString()`; PostgREST casts |
| `expenses.id` | `text` | `String` | works, but inconsistent with every other PK |
| `patients.dob` | `date` | `String?` | works via PostgREST date cast |
| `vaccine_batches.expiry_date` / `purchase_date` | text (per CSV) | `String` | text comparison in app; no server date arithmetic |
| `finance_transactions.transaction_date` | `date NOT NULL` | `String?` | **mismatch** (`SQL-027`) |
| `finance_transactions.amount` | `numeric` | `Double` | precision risk on money |
| `expenses.amount_paise` | `int8` | `Long` | good (minor units) |
| `inventory_transactions.timestamp` | `text` | `String` | **sort/parse risk** (`SQL-029`, `SQL-030`) |
| `consultations.doctor_id` | `text` | `String` | **FK/type risk** (`SQL-005`) |
| `patient_visits.doctor_id` | `text` | `String` | **FK/type risk** (`SQL-005`) |
| `doctor_weekly_slots.doctor_id` | `uuid` | `String` | works only for UUID values |
| `reminders.due_date` | `text NOT NULL` | `String` | date logic is string-based |
| `profiles.is_deleted` | `bool NOT NULL` | `Boolean` | fine |
| `waste_records.quantity` | `int4` | `Int` | fine |

### Observation — money as `Double`

`finance_transactions.amount`, `cash_amount`, `online_amount` are `numeric` server-side but `Double` in `FinanceEntity`. Summing `Double` values and sending them through JSON can produce `0.30000000000000004`-style values, and `numeric` will store them exactly as sent. The app already uses `Long` paise for expenses (`ExpenseEntity.kt:47`), which is the safer pattern. This is not raised as a separate finding because no current reproduction was traced; it is a contract hazard to fix when finance is next touched.

---

## 4. Nullability contract

Kotlin’s default values hide missing columns: if a server column is absent and the property has a default, decoding succeeds with the default. That makes schema drift silent.

| Field | Server | Kotlin default | Risk |
|---|---|---|---|
| `FinanceEntity.transactionDate` | `NOT NULL` | `null` | upload 23502 (`SQL-027`) |
| `PatientEntity.updatedAt` | nullable timestamptz | `null` | conflict check `isoToLong(null) = 0` |
| `VisitEntity.doctorId` | nullable text, default `''` | `""` | empty string can fail a UUID FK |
| `ConsultationEntity.doctorId` | nullable text | `""` | same |
| `AuditLogEntity.timestamp` | `text` default now | now (ISO) | client decides retention (`SQL-019`) |
| `VaccineBatchEntity.remainingQuantity` | unknown NOT NULL | `Int` | overwritten to 0 on create (`SQL-008`) |
| `ExpenseEntity.id` | `text NOT NULL` | UUID string | fine |

**Rule to adopt:** every non-null server column must be non-null in Kotlin with no default, or the DTO must be separate from the Room entity. Today they are the same class, so a missing column is indistinguishable from a legitimate default.

---

## 5. Identity contract

### 5.1 Local vs remote PK

Most entities use the same UUID locally and remotely. Two exceptions:

- `InventoryTransactionEntity.transactionId` ↔ `inventory_transactions.id`. The registry declares `pkColumn = "transactionId"`, `remotePkColumn = "id"` (`SyncUploader.kt:169-175`), and DELETE filters on the remote name (`:464-471`). Correct, after a past bug.
- `ReminderEntity.id` ↔ server-generated `reminders.id`; the local row carries `serverId` (`ReminderEntity.kt:37-38`). `toRemote()` sends `id = serverId ?: id` (`:144-145`); `toLocal()` stores `serverId = id` and generates a new local id (`:168-170`).

`SQL-024` is the delete path that ignores `serverId`. The upload path is correct.

### 5.2 `isSynced` defaults

`PatientEntity.isSynced` defaults to `true` (`PatientEntity.kt:35`), while `FinanceEntity`, `ExpenseEntity`, and most others default to `false`. A `PatientEntity` constructed without explicitly setting the flag is considered synced. Repositories set it correctly today; a future caller that forgets will silently skip upload. Low risk, worth a test.

### 5.3 Merge/transfer

`MergePatientsUseCase` rewrites `patient_visits.patientId` and `inventory_transactions.patientId` locally (`VaccinationDao.kt:76`, `:77`). The server rows are updated by the queued UPDATEs. `audit_logs.patientId` is **not** transferred, so the merged patient’s audit history stays under the duplicate id. Covered by `SQL-021`’s FK policy; not a separate finding.

---

## 6. Timestamp contract

| Field | Format written by client | Format returned by Postgres | Parser behaviour |
|---|---|---|---|
| `updated_at` (Room entities) | `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` (e.g. `2026-08-22T03:48:08.105+00:00`) | `2026-08-22 03:48:08.105121+00` (text columns) or ISO (timestamptz) | `Instant.parse` handles the client form; `parseDate` handles the server form but in IST (`SQL-030`) |
| `audit_logs.timestamp` | ISO with `T` | text, `(now())::text` | mixed |
| `inventory_transactions.timestamp` | ISO with `T` | text | mixed |
| `vaccine_batches.updated_at` | ISO with `T` | text | mixed |

The conflict check (`SyncUploader.kt:504-508`) compares `isoToLong(remote)` with `isoToLong(local)`. For a text remote value, the 5.5-hour skew is deterministic. For a `timestamptz` remote value, PostgREST returns ISO with `+00:00`, which `Instant.parse` handles correctly.

**Contract fix:** either make every server timestamp `timestamptz` (preferred), or have the client parse both forms explicitly and treat a missing offset as UTC for server-originated values.

---

## 7. Delete contract

| Delete | Local action | Remote action | Contract issue |
|---|---|---|---|
| Patient | hard delete, children first | `DELETE patients` | FK blockers (`SQL-021`), todos left (`SQL-022`), visits double-deleted (`SQL-025`), reminder id (`SQL-024`) |
| Visit | hard delete | `DELETE patient_visits` | ledger `visit_id` left (`SQL-023`) |
| Vaccination item | hard delete | `DELETE vaccination_items` | fine |
| Batch | hard delete | `DELETE vaccine_batches` | guard blind (`SQL-011`), trigger failure (`SQL-012`) |
| Vaccine | hard delete | `DELETE vaccines` | guard checks local counts only; same blind-spot class as `SQL-011` |
| Finance | hard delete | `DELETE finance_transactions` | fine |
| Expense | hard delete | `DELETE expenses` | policy allows admin/doctor only (`20261013120000…:71-80`); a nurse delete fails RLS |
| Reminder | hard delete | `DELETE reminders` | `serverId` bug (`SQL-024`) |
| Consultation | hard delete + finance + visit | three groups | ordering (`SQL-025`) |
| Profile | service role only | `DELETE`/deactivate via Edge Function | fine |

**Expense delete policy mismatch:** the app exposes delete to any active staff UI path? `ExpenseRepositoryImpl.deleteExpense` (`:99-118`) enqueues a DELETE for the current user. RLS allows only admin/doctor (`20260913120000…:71-80`). A nurse deleting an expense gets a permanent 42501. This is a real client/server contract mismatch; it is covered by `SQL-014` (broad CRUD is not actually broad for expenses) and should be confirmed in the UI role gating.

---

## 8. Pull contract

- Every pull filters `is_deleted = false` (13 paths). That is correct for a soft-delete world and wrong for the current hard-delete world (`SQL-028`).
- `RemotePrune` deletes local rows absent from the remote live set, with guards for empty responses, pending queue entries, and unsynced rows (`RemotePrune.kt:1-40`). With the `is_deleted` filter, a tombstoned server row is “absent”, so the local row is pruned. That is the intended end state.
- `refreshInventory` does not pull `inventory_transactions` (`SQL-011`).
- `refreshVaccinationItems` and `refreshVaccinations` use `order("id")` + `range` (`Pagination.kt:14-22`, 1,000-row pages). Fine at clinic scale.
- `refreshProfiles` uses the conditional unique email index; if it was skipped (`SQL-003`), duplicate profiles can be downloaded and the local `upsert` keeps the last one.

---

## 9. Contract tests the repo should have

1. **Serialization round-trip per entity:** encode a fully populated entity, assert every key exists and matches the CSV column set; fail on unknown/missing keys. This would have caught the `waste_records` camelCase drift.
2. **Nullability contract:** for every non-null server column, assert the Kotlin property is non-null or has a server-side default.
3. **PK mapping:** for every descriptor, assert `pkColumn` and `remotePkColumn` exist in the entity and the table.
4. **Delete identity:** for reminders, assert the queued id is `serverId ?: id`.
5. **Timestamp parsing:** the table from `SQL-030`.
6. **Enum values:** `visit_type` (`VACCINATION`/`CONSULTATION`), `status`, `transaction_type`, `payment_method`, `role`. A typo is accepted by `text` columns; add check constraints or a shared enum.

None of these exists today. `SQL-036` covers the missing CI; this list is the client-side subset.
