# Data Integrity and Business Logic

## 1. Invariants audited

| Domain | Invariant |
| --- | --- |
| Stock | shelf + used + wasted + borrowed must reconcile with purchases, vaccinations, waste, borrows, returns, edits and reversals |
| Vaccination | stock moves by `new - old` exactly once per edit; delete restores exactly what was deducted |
| Finance | visit total = cash + online; linked finance row matches after create/edit/delete; one row per visit |
| Borrow | `0 <= returned <= borrowed`; a return is counted once; stock rises only on return |
| Reminders | one logical reminder per patient/visit/vaccine/type; due date matches the visit; delete leaves no remote orphan |
| Dates | clinic day = Asia/Kolkata; stored formats comparable; no device-timezone dependence |
| Identity | local id vs server id used correctly; receipt immutable; `created_at` stable |
| Statistics | no deleted/voided row counted; detail and aggregate use the same rows/date basis |

## 2. Stock accounting — confirmed critical defect (DBA-01)

`update_batch_stock()` in `20261017_stock_trigger_duplicate_and_update_arithmetic.sql`:

```sql
elsif tg_op = 'UPDATE' then
    v_old_batch := old.batch_id;
    v_new_batch := new.batch_id;
    v_delta     := new.quantity - old.quantity;
...
if v_old_batch is not null and v_delta <> old.quantity then
    update vaccine_batches set remaining_quantity = remaining_quantity - old.quantity ...
if v_new_batch is not null then
    update vaccine_batches set remaining_quantity = remaining_quantity + v_delta ...
```

Operations that cause an UPDATE:

- PostgREST `upsert` of an existing `inventory_transactions` row (a retry after a lost response).
- Any edit that changes quantity/batch.

Pure arithmetic simulation (no database touched). `Start` is the batch value immediately before the UPDATE and `Expected` is `Start + (new - old)`, i.e. the old row's effect is already reflected in `Start`. A real sequence that includes the INSERT shifts every value, but the error is the same:

| Case | Start | Actual result | Expected | Verdict |
| --- | --- | --- | --- | --- |
| retry `-2` (vaccination) | 10 | **12** | 10 | corrupt (+2) |
| retry `+10` (purchase) | 10 | **0** | 10 | corrupt (−10) |
| retry `+2` | 10 | **8** | 10 | corrupt (−2) |
| edit `-2 → -3` | 100 | **101** | 99 | corrupt (+2) |
| edit `+2 → +5` | 100 | **101** | 103 | corrupt (−2) |
| edit `+10 → +4` | 100 | **84** | 94 | corrupt (−10) |
| cross-batch `A:-2 → B:-3` | A=100, B=50 | A=102, **B=49** | A=102, B=47 | corrupt (+2) |
| DELETE `-2` | 10 | 12 | 12 | correct |

Root cause: the function both “undoes old” and applies `new - old`, and guards the undo with `v_delta <> old.quantity` (i.e. `new <> 2*old`), which is true for almost every update. Correct options:

- same batch: `remaining += new.quantity - old.quantity` with **no** undo; or
- move: `old.remaining -= old.quantity` and `new.remaining += new.quantity`.

Impact: stock inflation/deflation, negative-stock guard raising `check_violation` and rolling back a legitimate ledger row, retry storms, and no history repair by the migration itself. Every retry of a stock upload is an UPDATE, so this is reachable in normal offline/reconnect behavior, not only manual edits.

Negative guard interacts badly: the pre-check computes `remaining + delta` **after** the erroneous undo, so an operation can be rejected even though the real arithmetic would be valid (and vice versa).

## 3. Vaccination edit — correct at HEAD, residual server risk

`VaccinationEditEngine` and `inventoryDiff`:

- computes per-batch `new - old`;
- positive delta → `deductStockFromBatch` (expiry rule relaxed for batches already on the visit);
- negative delta → `reverseDeduction`, capped at the sum of COMPLETED `inventory_deductions` rows;
- writes a FAILED deduction row when stock is short but still saves the non-inventory corrections;
- rebuilds the local deduction ledger from final items;
- runs the whole edit in `TransactionRunner` (one Room transaction);
- reconciles finance and reminders in the same transaction.

This resolves the older “edit double-deducts” concern for a single device. Residual risk is server-side: retries hit DBA-01, and the local batch mirror can diverge from server stock after a failed/rolled-back ledger upload.

## 4. Delete semantics

### Delete vaccination

1. Sum COMPLETED deductions per batch.
2. `reverseDeduction` for each (stock + REVERSAL ledger).
3. Delete the local deduction ledger.
4. Delete item rows and enqueue ITEM DELETEs.
5. Delete finance rows and enqueue DELETEs.
6. Delete reminders by visit, enqueue DELETE using `serverId ?: id`.
7. Delete the visit and enqueue VACCINATION DELETE.

The reversal-before-ledger-delete ordering makes a local retry idempotent: the second run finds no COMPLETED rows. However, `inventory_transactions.visit_id` is deliberately **not** cleared (step 2b comment), while the uploader still claims it is. See DBA-02.

### Delete patient

- Deletes local reminders and personal reminders.
- Clears the local deduction ledger for every visit **before** calling `deleteVaccination`, deliberately making the delete records-only: doses already given are not put back on the shelf. This is a defensible business decision and is documented in code.
- Deletes visits, finance, consultations, then the patient.
- Reminder DELETEs use **local ids**, not `serverId` (DBA-05). Pulled reminders have `id = random UUID`, `serverId = remote id`; the remote delete therefore targets an id that does not exist. PostgREST delete of zero rows is success, so the queue item disappears and the remote reminder survives.
- The reminder DELETEs are ungrouped and `LOW`, while the patient DELETE is `MEDIUM`, so the parent is likely to upload first (`PatientRepositoryImpl.kt:251-254`).

### Merge duplicate patients

`MergePatientsUseCase` transfers only vaccinations, reminders and the `inventory_transactions.patient_id`. It then calls `deletePatient(dupId)`, which deletes the duplicate's consultations, finance rows, notes and remaining children instead of moving them (DBA-26). The moved-row UPDATEs are also not grouped with the duplicate's PATIENT DELETE, so the parent delete can upload first and cascade remotely.

## 5. Finance

- Visit income uses a deterministic UUID (`nameUUIDFromBytes("visit_income:$visitId")`), preventing duplicate rows for the same visit across devices.
- `updateConsultationIncome`/`updateIncomeForVisit` update the existing row; consultation edits update visit totals and finance via `ConsultationEditEngine`.
- Pull self-heal:
  - computes visit date via `parseDate(visit.dateGiven)` then **`ZoneId.systemDefault()`**;
  - if it differs from `transactionDate`, rewrites the remote value, sets `isSynced=false`, and enqueues UPDATE;
  - the `local != null && wasHealed` branch forces `shouldUpdateLocal=true` even when the row is in the pending queue.
- Consequences:
  1. a pending local finance edit can be replaced by the pulled row (DBA-03);
  2. on a non-IST device the “healed” date is the previous/next day, so every pull rewrites and re-uploads every finance row (DBA-04);
  3. two devices in different zones can fight indefinitely.
- Finance conflict timestamp is intentionally blank because the entity has no local `updatedAt`; the local edit uploads rather than being dropped. That is a last-writer-wins design, not a merge.

## 6. Reminders

- `ReminderEntity` separates `id` (local UUID) from `serverId` (remote id); `toRemote()` sends `serverId ?: id`; `toLocal()` stores the remote id in `serverId`.
- `deleteVaccination` correctly uses `serverId ?: id`.
- `deletePatient` does not (DBA-05).
- `refreshReminders` skips remote rows whose `originalVisitId` is missing locally, preventing local resurrection/FK failure. The orphan row still exists remotely.
- `saveNextVaccination` looks up by `(patientId, originalVisitId, dueDate, vaccineName, type)`; the local unique index is the same five columns. The server unique index (when it could be created) is only `(patient_id, original_visit_id, vaccine_name, type)` — no due date. A changed due date creates a new local row that the server rejects with 23505. The edit path deletes the old row first, so this is a narrow create/duplicate-spec risk, not a proven everyday bug (DBA-19, suspected).
- Edit reconciliation is delete-then-create with excluded (dismissed) rows preserved; it is not an in-place diff.

## 7. Borrow / return

- Repository re-checks `quantity <= outstanding` where `outstanding = borrow.quantity - local returned total` (line 217-221). This blocks stale-dialog and double-submit over-return on one device.
- **But** `markReturned` uses the caller-supplied `remainingQuantity`, not the recomputed outstanding:

```kotlin
if (quantity >= remainingQuantity) { markReturned(...) }
```

  A stale dialog can mark a borrow returned while 8 doses are still outstanding, or leave it active after the final return. This is a confirmed single-device status bug (DBA-10).
- Across devices, each device computes “returned” from its local `borrow_returns`; there is no server aggregate constraint. Two devices can each pass the local cap and jointly return more than borrowed, inflating shelf stock. The stock trigger does not guard positive returns.
- Return-to-new-batch creates a zero-purchase batch created at `remaining=0`; the paired return transaction raises it via the trigger. This relies on correct trigger arithmetic for INSERT (currently correct) and on batch creation ordering (grouped).

## 8. Dates and timezones

The IST layer exists (`DateUtils.kt:19-35`, `parseDate` anchored to Asia/Kolkata at line 100, extension `Date.toLocalDate()`), but it is bypassed in:

| Location | Code | Effect |
| --- | --- | --- |
| Finance transaction date | `FinanceRepositoryImpl.kt:58` | visit date shifted to device-local date |
| Finance self-heal | `FinanceRepositoryImpl.kt:399` | re-heals/re-uploads on non-IST devices |
| Stock history filter | `StockHistoryViewModel.kt:197` | off-by-one day filter |
| Expense date | `AddExpenseViewModel.kt:164`, `ExpenseListViewModel.kt:131` | off-by-one expense date |
| Dashboard “today” | `DashboardViewModel.kt:123` | wrong day's todos/statistics |
| Daily summary | `DailySummaryWorker.kt:29` | wrong summary day |
| Personal reminder notifications | `PersonalReminderNotificationWorker.kt:25` | fires on device date, not clinic date |
| Display formatters | `DateUtils.kt:121,146,155,165` etc. | dates/times shift for travelers |
| Statistics month buckets | `FinanceCalculator.kt:338-346` uses `Calendar.getInstance()` on dates parsed in IST | month boundary can shift |
| Borrow dates | `BorrowRepositoryImpl.kt:208`, `BorrowedScreen.kt:294` | device date stored as business date |

`DateUtils.formatDateForDisplay` also uses `ZoneId.systemDefault()` after parsing with IST, which can shift the displayed day. The pattern is inconsistent: same file contains both correct IST formatting and device-zone formatting.

## 9. Statistics and soft deletes

- Local business deletes are hard deletes; migration 31→32 removes legacy soft-delete columns after physically deleting `is_deleted=1` rows.
- Remote pulls filter `is_deleted=false`, so legacy soft-deleted server rows are not imported/counted.
- `reminders` retains local soft-delete columns and its DAO filters `is_deleted=0`; this is intentional.
- No confirmed case of a soft-deleted record being counted in statistics.
- The real statistics defect is **stale rows**: a remote hard delete never reaches this device (DBA-06), so the deleted visit/finance row continues to be counted.

## 10. Identity and audit

- Receipt numbers: server trigger assigns once and preserves on UPDATE; client read-back is best-effort. Correct.
- `VaccinationEntity.toEntity()` sets `createdAt = timestamp` where `timestamp = updatedAt.ifBlank { now }`. Editing a visit therefore rewrites `created_at` locally and in the upsert payload. `ConsultationEntity` preserves `createdAt`; visits do not (DBA-13). Statistics do not use it, but audit/lineage does.
- `AuditLogger` writes and enqueues on its own `CoroutineScope`, explicitly outside the caller's transaction. A rolled-back business write can still produce an audit row claiming success (DBA-16).
- `PatientIdGenerator` derives `NEO-<max+1>` from the local table only; two offline devices can choose the same clinic ID and one upload then fails the server unique index (DBA-25).

## 11. Data repair implications

DBA-01 requires a **repair plan**, not only a code fix:

- Identify affected batches by replaying `inventory_transactions` in ledger order and comparing the computed shelf quantity with `vaccine_batches.remaining_quantity`.
- Quantify divergence before changing anything.
- Repair in a reviewed migration/transaction with a safety snapshot.
- Add a reconciliation query/view for ongoing detection.

Do not apply a repair automatically. The exact live divergence is unknown (see `15_Unknowns_And_Live_DB_Gaps.md`).
