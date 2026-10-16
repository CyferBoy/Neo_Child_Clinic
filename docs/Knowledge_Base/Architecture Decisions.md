# Architecture Decisions

> Decisions that were made deliberately, with the reasoning visible in the code or in a
> migration comment. Related: [[Clean Architecture]] · [[Known Bugs]] · [[Technical Debt]]

Each entry is a decision the codebase **owns**, not a happy accident. Where a decision has
since been partly undone, that is noted.

## 1. No destructive-migration fallback

`data/local/database/AppDatabase.kt:510-511`:

```kotlin
// No destructive fallback: a future missing migration must crash loudly,
// never silently wipe a clinic's local patient data.
```

✅ `fallbackToDestructiveMigration` is deliberately absent, and there is a test pinning the
v17 floor (`AppDatabaseMigrationTest.kt:17-21`). The cost is 16 hand-written migrations; the
benefit is that a schema mistake cannot destroy patient records.

## 2. A corrupt database is moved aside, never deleted

`AppDatabase.kt:464-497`. Sidecar files (`-journal`, `-wal`, `-shm`) are renamed **before**
the main file (`:480-483`) so a partial rename destroys nothing, and a rename failure
**throws** instead of wiping (`:484-489`).

## 3. Expenses are stored in integer paise

`data/local/entity/ExpenseEntity.kt:26-29`:

```kotlin
// amount_paise: bigint, not double — money must not touch a float.
val amountPaise: Long
```

✅ `ExpenseCalculatorTest` pins "paise precision survives without FP drift". Contrast
`FinanceEntity.amount: Double` (`FinanceEntity.kt`) — the older table still uses a float, so
the codebase improved this only for `expenses`.

## 4. Conflict resolution is queue-aware, not clock-aware

The pull-side rule, uniform across 12 repositories:

```kotlin
if ((local == null || local.isSynced) && entity.id !in pendingIds) { insert remote }
```

**A pending local change makes local authoritative until pushed; otherwise last-puller
wins.** There is no timestamp comparison on the pull side at all.

⚠️ The uploader *does* have timestamp comparison (`SyncUploader.kt:486-521`) but it is
unreachable — see [[Known Bugs]] §3.2. So in practice the app is **last-write-wins with no
clock**, and the sync-group ordering exists precisely to prevent the FK violations that a
naive last-write-wins would cause (`SyncRepositoryImpl.kt:668-678` explains this at length).

## 5. Deletes are enqueued child-before-parent, always

`VaccinationRepositoryImpl.deleteVaccination` (`:387-506`), `deletePatient` (`:193-251`),
`deleteVaccination`'s comment at `:366-386`. The parent DELETE is always enqueued **last**,
because a server-side FK (`finance_transactions_visit_id_fkey`) would otherwise reject the
parent delete while children still reference it.

This is why `transactionGroupId` exists and why `SyncOrderingTest` pins it. It also creates
bug §3.7 — reversal and edit land in different groups because `applyInventoryDiff` omits the
group id.

## 6. `ABORT` on patient insert, so a clinic-id collision fails loudly

`PatientRepositoryImpl.kt:168` uses the `ABORT` insert variant
(`PatientDao.kt:29-30`) rather than `REPLACE`. `REPLACE` on a unique-index conflict would
**delete the colliding patient** and insert the new one. ✅ Correct.

## 7. Inventory quantity is maintained server-side by a trigger, not pushed

`SyncUploader.kt:595-619` sends `remaining_quantity = 0` on create and **omits** the column
on update. The comment at `:595-597` explains that `tr_update_batch_stock` adjusts it by
`NEW.quantity` for every ledger row.

The rationale — a clinic with several devices must not have stock clobbered by whichever
device synced last — is sound. ⚠️ The implementation is where bugs §1.5 and §1.6 live.

## 8. The negative-stock guard raises rather than clamps

`20261013140000_stock_trigger_negative_guard.sql:40-49`, and the comment at `:41-43`:

> *After trigger, so raising rolls back the transaction that inserted the stock movement: the
> losing device's row fails loudly instead of silently booking a dose the clinic does not
> have.*

✅ "Fail loudly" is the right instinct. ⚠️ But the blast radius is the **whole sync group** —
`check_violation` surfaces as HTTP 400, which `isTransientSyncError` treats as permanent, so
`classifyAndRecordError` marks every item in the group `FAILED`. The visit never reaches the
server and there is no automated reconciliation.

## 9. Inventory errors are recorded, not raised — for the expected case only

`InsufficientStockException.kt:7-11` states the contract:

> *a shortfall must be recorded as a FAILED `inventory_deductions` row and still save the
> visit, while any other failure has to propagate.*

✅ The clinical outcome is right: staff can record a vaccination and reconcile stock later.
⚠️ But `ClinicalVaccinationService.kt:120` catches too narrowly — see [[Known Bugs]] §1.2.

## 10. Client ids are reused for `vaccination_items` when they are free

`EditReconciler.kt:48-53`:

```kotlin
// Added row. Never reuse an id that still belongs to an old row.
val id = if (incoming.id.isBlank() || incoming.id in oldById) UUID.randomUUID().toString()
         else incoming.id
```

The `in oldById` check exists specifically so an edit cannot re-point an existing row.
⚠️ But the retry path in [[Known Bugs]] §1.3 reaches exactly this branch with `oldById`
empty, because the retry creates a *new* visit with the same UI row ids.

## 11. Restore is fail-closed

`data/backup/BackupRepositoryImpl.kt:144-156`:

> *A failed safety snapshot must not silently allow a destructive restore to proceed — fail
> closed.*

And the safety snapshot is committed **only after** a successful restore (`:175-176`), with a
failed rename there logged rather than surfaced — *"rather than reporting the already-applied
restore as failed."* ✅ Both halves are right.

## 12. Restore conflict resolution is mode-per-table-class

`BackupRestorer.kt:20-35`:

| Mode | Rule | Why |
|---|---|---|
| Last-write-wins | entities with a reliable `updatedAt` | strict `>` so **equal timestamps keep the local row** — the safe direction |
| Insert-if-absent | append-only ledgers (`auditLogs`, `financeTransactions`, `inventoryTransactions`, `inventoryDeductions`, `borrowRecords`, `borrowReturns`, `patientNotes`) | never overwrite an append-only row |
| Follow parent | `vaccination_items` | only re-apply for visits this merge actually wrote |

## 13. Backup passwords are stored so unattended runs work — with the cost called out

`BackupRepositoryImpl.kt:307-317` persists the backup password in `EncryptedSharedPreferences`
for WorkManager to use unattended. The unavoidable `String` boundary is documented at
`:309-311`. ✅ Manual export never persists it (`:74-79`).

## 14. `SafetyBackupStore` uses a device-bound key, not the portable one

`data/backup/SafetyBackupStore.kt:18-23` explains it: the safety snapshot is local-only, so
it uses a Keystore key that cannot leave the device — deliberately distinct from the
portable password cipher. ✅ Right separation.

## 15. `is_expired` exists because dropping the column broke sync

`20260925_business_soft_delete_columns.sql:3-8`:

> *Tables that do not have those columns reject the upsert with PGRST107… which fails the
> whole sync group.*

That is a real constraint discovered the hard way: the client upserts a fixed column set, so
the server schema must accept every column it sends. It is why `is_deleted` is on 18 tables
even though only `reminders` still filters it locally. See [[Database Schema]].

## 16. Roles live in `app_metadata`, not `user_metadata` or `profiles`

`20261001_app_metadata_roles.sql` backfills `auth.users.raw_app_meta_data.role` from
`profiles.role`, strips the role from `user_metadata`, and moves every policy check to
`auth.jwt() -> 'app_metadata' ->> 'role'`.

✅ `user_metadata` is user-writable; `app_metadata` is not. The client reads the same claim
(`CurrentUserProvider.kt:22-23`) with an exact match and no default.

⚠️ But legacy `"Admins can manage all profiles"` policies gated on
`public.get_my_role()` — which reads `profiles.role` — would defeat this entirely. See
[[Known Bugs]] §4.2.

## 17. `update_batch_stock()` is `SECURITY INVOKER`, deliberately

`20261013140000:23-65` has no `security definer`. It works only because
`20260825:106,125-129` grants `active staff update` on `vaccine_batches`.

⚠️ This is a latent coupling: tighten that policy and **every stock movement starts failing
at trigger time**. `recalculate_all_stocks()` from the original scratch file was never
versioned at all.

## 18. The RLS model is single-clinic, not role-scoped

`20260825_clinic_rls_and_accountability.sql:22-26`:

> *Access model: single-clinic app, matching the rest of the schema (is_admin() etc.) — any
> authenticated, active, non-deleted staff member can read/write the shared clinic dataset,
> regardless of role.*

16 of 26 tables grant full CRUD to any active staff member. A receptionist **can**
hard-delete `patient_visits` and `finance_transactions`. ✅ Intentional and documented —
recorded here so it is not re-discovered as a bug each audit. See [[Row Level Security]].

## 19. `AuthViewModel` blocks sign-out with unsynced work

`core/security/AuthViewModel.kt:296-312` — a bounded flush (20 s), then
`syncQueueDao.getUnacknowledgedCountSync() > 0` **blocks** sign-out and waits for explicit
consent. ✅ The best-engineered part of the codebase. See [[Offline-First Architecture]].

## 20. Account switching erases local data

`AuthViewModel.kt:329-345` — clear caches, `appDatabase.clearAllTables()`, clear
`accountPrefs`. ✅ Backed by a dedicated `PatientCacheTest` assertion.

## 21. Biometric success alone is never trusted

`core/security/BiometricAuthenticator.kt:26-27` — *"The biometric callback alone is never
trusted"*. `verifyProtectedSecret` (`:165-206`) decrypts a random 32-byte Keystore secret
and compares SHA-256 with `MessageDigest.isEqual` (constant time, `:193`) before
`onVerified()`. `setUserAuthenticationRequired(true)` (`:119`) and
`FLAG_SECURE` unconditionally (`MainActivity.kt:85`). ✅ Correct design.

## 22. `PatientHistoryUiState` exists because a null list is ambiguous

`feature/patient/presentation/PatientHistoryUiState.kt:8-18` documents it: an initial `null`
list means "still loading", an empty list means "loaded and empty". It is the **only** screen
in the app that models this. See [[MVVM]].

## 23. Shared pure engines exist so the rules are testable

`VaccinationEditEngine.inventoryDiff`, `EditReconciler.classifyItems`,
`InventoryStockMovement.deducted`, `ReminderDueListProcessor`, `AddVaccinationLogic`,
`BorrowBatchSelection` are all dependency-free. ✅ That is exactly why the pure functions are
tested and the orchestrators are not — see [[Testing]].

## Related

- [[Clean Architecture]] — the layering these decisions produce
- [[Known Bugs]] — where several of these decisions have been partly undone
- [[Technical Debt]] — the cost of these choices
