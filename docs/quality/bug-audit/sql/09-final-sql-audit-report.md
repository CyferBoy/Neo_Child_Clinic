# 09 — Final SQL Audit Report

**Repository:** Neo Child Clinic (`vaccine_manager_app`)
**Revision audited:** `b91142a` (worktree clean at start; only `docs/bug-audit/sql/**` added by this audit)
**Date:** 2026-10-25
**Mode:** source-first, read-only. No SQL, migration, policy, trigger, function, Room schema, or live database state was changed.
**Post-audit pull:** `93021c2` (2026-10-26) — an independent audit pass published `docs/bug-audit/sql/10`–`13` under a separate DBA/VER register and applied client-only fixes. The 37 rows below keep their `b91142a` verdicts; §4.3 records which rows that commit changed and which are untouched. No `supabase/` or `SyncUploader.kt` change came in, so every SQL-side and sync-side finding still stands.

---

## 1. Executive summary

The repository contains **29 migrations and 3 SQL tests**, but it is **not a complete schema**. Fifteen of the 23 tables in the catalogue — including every core clinical table — have no `CREATE TABLE` anywhere in the repo. The schema was built in the Supabase dashboard / hand-applied scratch files, and the migration chain is a set of deltas on top of that invisible base. A fresh `supabase db reset` cannot reproduce the database, and a fresh deployment silently gets **no RLS** on the missing tables because the baseline skips missing relations.

On top of that structural gap, the audit found **37 defects**: **4 Critical, 11 High, 20 Medium, 2 Low**. Four are material enough to stop a release:

1. **`SQL-008` — a batch CREATE retry resets server stock to 0.** `uploadBatch` sends `remaining_quantity = 0` on every CREATE; a lost-response retry after the purchase committed wipes the stock, and the paired PURCHASE retry is insert-only so it never restores it.
2. **`SQL-006` — moving a ledger row between batches bypasses the lock and negative guard on the old batch.** A positive purchase moved off an under-stocked batch drives it negative.
3. **`SQL-021` — patient hard delete is likely blocked by audit/ledger foreign keys.** The delete flow also writes a new audit row that references the patient being deleted.
4. **`SQL-001` — the schema cannot be rebuilt.** This is a recovery and staging risk, and it is the root cause of several smaller drifts.

The happy-path stock trigger is correct: insert, same-batch update, retry, delete-with-sufficient-stock, and the negative guard all behave as intended and are covered by a real assertion script. The failures are in **retry**, **batch reassignment**, and **delete/correction** paths.

RLS is coherent for a **single shared clinic**: active staff get full CRUD on 20 tables, audit logs are SELECT+INSERT only, and profiles have a guarded self-update. It is **not tenant-isolated**, deactivated staff can still write doctor slots (`SQL-013`), and the patient-documents bucket is readable by every active staff member (`SQL-015`).

The app now **hard-deletes**; the server still carries soft-delete columns on 18 tables and every pull filters `is_deleted = false`. That mismatch is `SQL-028` and is a live mixed-version hazard.

No SQL was executed. All runtime/RLS/trigger/concurrency findings that depend on live state are labelled **Requires Verification** or **Probable** and have a live check.

---

## 2. Scope and method

### 2.1 What was reviewed

- all 29 migrations, line by line;
- all 3 SQL test scripts and `verify_security.sql`;
- the full 374-row schema catalogue CSV;
- the two hand-applied scratch SQL files (as evidence, not authority);
- the Cloudflare D1 backup schema (metadata only, out of scope);
- the Android sync write path (`SyncUploader`, `SyncRepositoryImpl`, entity registry, conflict checks, delete paths);
- the Android pull paths for every synced table;
- Room version history and the 31→32 soft-delete purge;
- repository delete flows for patient, visit, consultation, batch, vaccine, borrow, expense, finance, reminder;
- prior audit notes in `docs/Knowledge_Base/Database-Audit/` and `docs/bug-audit/`, re-checked independently.

### 2.2 What was not reviewed or could not be verified

| Area | Why | Status |
|---|---|---|
| Live `pg_constraint`, `pg_policies`, `pg_trigger` | no `psql`, Docker, Supabase CLI, or database | not inspected |
| RLS behaviour under real JWTs | no Supabase instance | static only |
| Migration replay | 15 tables have no DDL; no disposable DB | not run |
| Trigger arithmetic | not executed | code-proven only |
| `EXPLAIN` / row counts | no server | not measured |
| Production data (duplicates, tombstones, orphans) | no access | unknown |
| Supabase Edge Functions (`manage-staff`, `notify-update`) | TypeScript; only cross-checked for role writes | partial |
| Cloudflare Worker runtime | D1 schema only | out of scope |
| pg_cron / pg_net availability | environment-dependent | unknown |
| Storage objects | no access | unknown |
| Backups / restore | no access | unknown |
| Android instrumented tests | not run | none executed |

### 2.3 Evidence standard

- **Confirmed** — visible in source and independent of unknown live state.
- **Probable** — code path is clear but the effect depends on a live constraint, policy, or data shape.
- **Requires Verification** — a disposable database or live introspection is required before promotion.
- Every claim carries a `path:line` citation; comments are treated as claims, not proof.

---

## 3. Inventory

| Measure | Count |
|---|---:|
| `.sql` files (excluding `.git`) | 36 |
| SQL lines | 3,147 |
| Supabase migrations | 29 |
| Migration-created tables | 9 |
| Catalogue tables | 23 |
| Catalogue tables with **no** `CREATE TABLE` in repo | **15** |
| Migration-created tables absent from catalogue | 1 (`api_write_rate_limits`) |
| Tables created outside any repo file | 1 (`todo_notification_events`) |
| Missing referenced migration | 1 (`20260911_todays_patient_notifications.sql`) |
| `CREATE POLICY` / `DROP POLICY` | 56 / 68 |
| `CREATE FUNCTION` / `CREATE TRIGGER` | 21 / 14 |
| Migrations without `begin;` | 9 |
| SQL test scripts with assertions | 2 of 3 |
| Findings | 37 |

Full details: `00-sql-inventory.md`.

---

## 4. Severity and confidence

### 4.1 Counts (audit snapshot `b91142a`)

| Severity | Count | IDs |
|---|---:|---|
| **Critical** | **4** | SQL-001, SQL-006, SQL-008, SQL-021 |
| **High** | **11** | SQL-002, SQL-003, SQL-007, SQL-009, SQL-013, SQL-018, SQL-020, SQL-022, SQL-023, SQL-025, SQL-028 |
| **Medium** | **20** | SQL-004, SQL-005, SQL-010, SQL-011, SQL-012, SQL-014, SQL-015, SQL-016, SQL-017, SQL-019, SQL-024, SQL-026, SQL-029, SQL-030, SQL-031, SQL-032, SQL-033, SQL-034, SQL-035, SQL-036 |
| **Low** | **2** | SQL-027, SQL-037 |
| **Total** | **37** | |

| Confidence | Count | IDs |
|---|---:|---|
| Confirmed | 29 | 001–006, 008, 009, 011, 013–015, 017–020, 022, 024–030, 032, 033, 035–037 |
| Probable | 5 | 007, 012, 021, 023, 034 |
| Requires Verification | 3 | 010, 016, 031 |

### 4.2 Register

| ID | Severity | Confidence | Finding | Primary evidence |
|---|---|---|---|---|
| SQL-001 | Critical | Confirmed | Fresh schema cannot be reconstructed; 15/23 tables missing | `20261016…:13`; migration count |
| SQL-002 | High | Confirmed | Catalogue/migration drift; `api_write_rate_limits`, `todo_notification_events`, stale CSV | CSV; `20260929001000…:6` |
| SQL-003 | High | Confirmed | Conditional unique indexes silently skipped on duplicates | `20261016…:92-124` |
| SQL-004 | Medium | Confirmed | 9 migrations lack a transaction wrapper; partial state possible | listed in `04-migrations.md §2` |
| SQL-005 | Medium | Confirmed | `doctor_id` text/uuid and text timestamps | CSV; `20260911…:134,137` |
| SQL-006 | Critical | Confirmed | Moved-batch UPDATE bypasses lock/negative guard on old batch | `20261022…:49-56` |
| SQL-007 | High | Probable | DELETE branch blocks legitimate corrections | `20261022…:61-86` |
| SQL-008 | Critical | Confirmed | Batch CREATE retry resets stock to 0 | `SyncUploader.kt:630-641` |
| SQL-009 | High | Confirmed | Sync groups are not atomic | `SyncRepositoryImpl.kt:272-333` |
| SQL-010 | Medium | Requires Verification | Insert-only ledger retry can drop a changed CREATE | `SyncUploader.kt:564-568,665-667` |
| SQL-011 | Medium | Confirmed | Inventory pull omits the ledger; delete guard blind | `InventoryRepositoryImpl.kt:641-658` |
| SQL-012 | Medium | Probable | Remote batch DELETE can fail inside the stock trigger | `20261022…:61-79` |
| SQL-013 | High | Confirmed | Doctor-slot writes ignore deactivation | `20261013120000…:83-118` |
| SQL-014 | Medium | Confirmed | Active-staff-wide CRUD, no least privilege | `20260929000000…:72-87` |
| SQL-015 | Medium | Confirmed | `patient-docs` readable by every active staff member | `20261016…:19-24,67-78` |
| SQL-016 | Medium | Requires Verification | `DROP FUNCTION … CASCADE` removed 14 policies | `20261021000000…:17-27` |
| SQL-017 | Medium | Confirmed | Profile self-update allowlist is fragile | `20261013…:15-18` |
| SQL-018 | High | Confirmed | Client can choose/forge a receipt number | `20260824…:116-119` |
| SQL-019 | Medium | Confirmed | Audit content/timestamp client-authored | `20260825…:145-166`; `20261025…:13-19` |
| SQL-020 | High | Confirmed | `prune_audit_logs` can fail permanently on malformed text | `20261025…:13-20` |
| SQL-021 | Critical | Probable | Patient delete blocked by audit/ledger FKs; audit self-reference | `PatientRepositoryImpl.kt:291-300`; scratch `:26,67` |
| SQL-022 | High | Confirmed | Todo rows retain patient PII after delete | `20260825_patient_todos.sql:8,27` |
| SQL-023 | High | Probable | Visit delete leaves `inventory_transactions.visit_id` | `VaccinationRepositoryImpl.kt:452-453` |
| SQL-024 | Medium | Confirmed | Reminder DELETE uses local id, not `serverId` | `PatientRepositoryImpl.kt:259-261` |
| SQL-025 | High | Confirmed | Patient delete removes the same visit twice via two paths | `PatientRepositoryImpl.kt:252,279-287` |
| SQL-026 | Medium | Confirmed | Finance self-heal bypasses pending guard; device timezone | `FinanceRepositoryImpl.kt:418-419,443-446` |
| SQL-027 | Low | Confirmed | Nullable client date vs `date NOT NULL` | `FinanceEntity.kt:19`; CSV |
| SQL-028 | High | Confirmed | Hard-delete client vs soft-delete server | `20260925…:30-56`; `AppDatabase.kt:385+` |
| SQL-029 | Medium | Confirmed | Text timestamps break ordering/retention/parse | CSV; `AuditLogRepositoryImpl.kt:26` |
| SQL-030 | Medium | Confirmed | Postgres text timestamps parsed 5.5 h off | `DateUtils.kt:80-106,195-209` |
| SQL-031 | Medium | Requires Verification | Hot-path indexes/RLS plans unverified | index inventory |
| SQL-032 | Medium | Confirmed | Rate-limit cleanup never scheduled | `20260929001000…:77-92` |
| SQL-033 | Medium | Confirmed | Stock test misses moved-purchase negative case | `stock_trigger_arithmetic.sql:74-84` |
| SQL-034 | Medium | Probable | Reconciliation test assumes nullable `vaccine_id` | `inventory_reconciliation_test.sql:31-40` |
| SQL-035 | Medium | Confirmed | RLS test is a placeholder with no assertions | `rls_role_test.sql` |
| SQL-036 | Medium | Confirmed | No migration replay / drift test | no CI job; `SQL-001` |
| SQL-037 | Low | Confirmed | `verify_security.sql` is manual, not in CI | `supabase/verify_security.sql` |

### 4.3 Post-audit changes

**`93021c2`** applied client-only fixes and published a parallel register; no SQL file changed. **This working tree** fixes the remaining Critical/High items that did not need a product decision. Full detail and evidence: `14-fix-log.md`.

| Row | Status now | Evidence |
|---|---|---|
| SQL-006 | **Fixed** | `20261026_stock_moved_batch_guard.sql` locks and guards the old batch. Test fails on `20261022` alone, passes after. |
| SQL-008 | **Fixed** | `batchUpsert()` returns `insertOnly = isCreate`; `uploadBatch` sends `ignoreDuplicates = true` on CREATE. `BatchUpsertTest` passes. |
| SQL-010 | **Closed — no reproduction** | No code path updates an `inventory_transactions` row after enqueue; the insert-only retry cannot drop a changed payload. |
| SQL-013 | **Fixed** | Both slot write policies now require `is_active_staff()` (`20261026_rls_slot_writes_require_active_staff.sql`). |
| SQL-018 | **Fixed** | Receipt trigger always allocates on INSERT (`20261026_receipt_number_server_allocated.sql`). Verified: a forged value is replaced. |
| SQL-020 | **Fixed** | Prune uses a text date comparison, no cast (`20261026_audit_prune_without_cast.sql`). Verified with a malformed timestamp. |
| SQL-021 | **Fixed in source; live FK dependent** | `audit_logs.patient_id` and `inventory_transactions.patient_id` rebuilt as `ON DELETE SET NULL` (guarded migration). |
| SQL-023 | **Fixed in source; live FK dependent** | `inventory_transactions.visit_id` rebuilt as `ON DELETE SET NULL`. The client still keeps the link, which is now safe. |
| SQL-024 | **Fixed** | `PatientRepositoryImpl.kt` enqueues `reminder.serverId ?: reminder.id` in the patient delete group. |
| SQL-025 | **Fixed** | Consultations are now deleted and enqueued before the visits they own (`PatientRepositoryImpl.kt`). |
| SQL-026 | **Fixed** | `FinanceRepositoryImpl.kt:408-452` gates both branches on `pendingFinanceIds`; dates convert through `IST_ZONE_ID`. |
| SQL-030 | **Fixed** | `DateUtils.isoToLong` parses the Postgres offset form as an instant; `PatientUtilsTest` asserts the UTC epoch. |
| SQL-032 | **Fixed** | `20261026_schedule_rate_limit_cleanup.sql` schedules the cleanup when `pg_cron` exists. |
| SQL-033 | **Fixed** | `stock_trigger_arithmetic.sql` gained the moved-purchase guard case. |
| SQL-022, 025 (todos), 028, 007, 014, 019 | **Open — decision** | Retention/soft-delete/least-privilege choices, not code defects. See `14-fix-log.md §3`. |
| SQL-001–005, 009, 011, 012, 016, 027, 029, 031, 034–037 | **Open** | Blocked on live schema, data, or a larger design change; see `14-fix-log.md §3`. |

**Citation caveat:** the register above cites `b91142a` line numbers. `PatientRepositoryImpl.kt`, `VaccinationRepositoryImpl.kt`, `FinanceRepositoryImpl.kt`, `BorrowRepositoryImpl.kt`, `VaccinationEntity.kt`, `DateUtils.kt` and `SyncUploader.kt` have all shifted since; re-grep before quoting a line.

**Two registers now coexist.** This document uses `SQL-001`–`SQL-037`; the pulled `10`–`13` use `DBA-01`–`DBA-26` plus `VER-01`–`VER-06`. They overlap but are not 1:1 (`DBA-02` ≈ `SQL-023`; `DBA-03`/`DBA-04` ≈ `SQL-026`/`SQL-027`; `DBA-05` ≈ `SQL-024`/`SQL-025`; `DBA-08` ≈ `SQL-014`; `DBA-24` ≈ `SQL-006`). `14-fix-log.md` is the current implementation record; reconcile the two registers before the next audit pass.

---

## 5. Top security and data-integrity risks

### 5.1 Stock can be silently destroyed by a retry (`SQL-008`)

This is the highest-confidence materialized data-loss path. The trigger is innocent; the client deliberately sends `remaining_quantity = 0` on CREATE (`SyncUploader.kt:635-636`) and relies on the paired PURCHASE to restore it. A lost response after both commit, followed by a retry, overwrites the stock with 0; the PURCHASE retry is `ignoreDuplicates = true` (`:665-667`) and does nothing. There is no server guard against a batch with zero stock and a non-zero ledger, and no pull of the ledger to detect it.

**Repair now:** change the CREATE path to an insert-only operation; add the reconciliation check to the release gate.

### 5.2 Batch reassignment can drive stock negative (`SQL-006`)

The final trigger locks and guards the **destination** batch but not the **old** batch. A move of a positive purchase off a batch that no longer holds those doses computes a negative old balance and commits it. The existing test moves only a negative vaccination row, so it passes. The bug is in the exact branch the last two stock migrations were written to fix.

**Repair now:** route the old-batch undo through the same lock/guard, and add the missing test.

### 5.3 Patient delete is likely non-functional (`SQL-021`–`SQL-025`)

Four independent problems compound:

- FK blockers on `audit_logs.patient_id` and `inventory_transactions.patient_id` (`SQL-021`);
- a new audit row that references the patient being deleted (`SQL-021`);
- todos left behind with name/mobile/address (`SQL-022`);
- visits deleted twice through the vaccination and consultation paths (`SQL-025`);
- ledger rows still referencing the visit (`SQL-023`).

Depending on the live FK actions, either the patient DELETE, a visit DELETE, or the audit INSERT fails. The device has already deleted locally, so the failure manifests as a resurrected patient/visit on the next pull.

**Repair order:** decide tombstone vs hard delete; then fix FKs, audit target, todos, and delete order together. Do not patch one path at a time.

### 5.4 Deactivated staff can still write schedules (`SQL-013`)

Every other table gates on `is_active_staff()`. The two doctor-slot policies gate only on ownership or a JWT admin claim. A deactivated doctor can change their availability until the access token expires; a deactivated admin can change anyone’s. The fix is a two-line policy change.

### 5.5 Receipt numbers are client-chosen (`SQL-018`)

The trigger accepts any non-blank unique value. A staff token can insert an out-of-series or backdated receipt number. The sequence is still correct for trigger-allocated numbers, but the audit trail can no longer prove that every receipt came from the sequence.

### 5.6 Audit retention can stop forever (`SQL-020`)

`timestamp` is text, the regex filter admits impossible dates, and the cast can raise before the filter. One bad row stops the nightly prune. The table then grows unbounded, which also makes the audit screen slower every month.

### 5.7 Soft-delete drift (`SQL-028`)

Current clients hard-delete, the server still has the columns, and old clients can re-create rows that current clients cannot see. This is the most likely source of “deleted record came back” reports during a mixed-version rollout.

---

## 6. Inventory transaction safety

### 6.1 Verdict

**The happy path is correct; retry, reassignment, and correction paths are not safe.** Do not treat the inventory ledger as an auditable source of truth until `SQL-006` and `SQL-008` are fixed and `inventory_reconciliation` is clean.

### 6.2 What works

- INSERT applies `new.quantity`; same-batch UPDATE applies the delta; retry with identical values is a no-op (`20261022…:40-48,66-68`).
- The destination batch is locked and guarded (`:70-86`).
- The single-trigger guard fails the migration if a second stock trigger survives (`:103-118`).
- Ledger CREATE retries are insert-only, so a lost response does not double-apply (`SyncUploader.kt:559-568`).
- `inventory_reconciliation` exposes drift without repairing it (`20261022_inventory_reconciliation_view.sql`).

### 6.3 What fails

| Path | Failure | Finding |
|---|---|---|
| Batch CREATE retry | writes 0 over restored stock | SQL-008 |
| Move a positive row | old batch unguarded, can go negative | SQL-006 |
| Delete a positive row after consumption | legitimate correction rejected | SQL-007 |
| Partial group | batch committed, ledger missing → stock 0 | SQL-009 |
| Batch delete with server-only ledger | guard blind; trigger raises | SQL-011, SQL-012 |
| Visit delete | ledger `visit_id` left; FK 23503 | SQL-023 |
| Two devices | text timestamps + 5.5 h skew → wrong winner | SQL-029, SQL-030 |

### 6.4 Required release gate

```sql
select count(*) from public.inventory_reconciliation where drift <> 0;
select count(*) from public.vaccine_batches where remaining_quantity < 0;
select count(*) from public.inventory_transactions t
left join public.patient_visits v on v.id = t.visit_id
where t.visit_id is not null and v.id is null;
```

All three must be 0 before and after the fix. Historical repair is explicitly out of scope until the ledger is reconciled against physical stock (`20261022…:129-131`).

---

## 7. RLS correctness

### 7.1 Model

- Single clinic, shared dataset.
- `is_active_staff()` gates 20 tables (four policies each).
- `is_admin()` / JWT `app_metadata.role` gate the carve-outs.
- `profiles`: SELECT own-or-admin; UPDATE own, guarded by a trigger.
- `audit_logs`: SELECT + INSERT only (the baseline correctly drops UPDATE/DELETE at `20260929000000…:96-98`).
- `receipt_sequences`, `api_write_rate_limits`, `todo_notification_events`: revoked from clients.
- `user_devices`: owner-only.
- `patient-docs`: active-staff-wide.

### 7.2 What is correct

- RLS is enabled on every table the migrations can see, and there is a deny-by-default sweep (`20260825…:168-189`).
- The audit append-only intent is implemented correctly despite the loop creating and then dropping the extra policies.
- `profiles` privileged fields are guarded, including the `deleted_at` gap fixed in `20261013`.
- The receipt sequence table is writable only by the definer function.
- The rate limit is database-backed, so a bypassing client cannot escape it.

### 7.3 What is wrong or unverified

| Issue | Finding |
|---|---|
| Deactivated doctor/admin can write slots | SQL-013 |
| Every active staff member can delete every business row | SQL-014 |
| Every active staff member can read every patient document | SQL-015 |
| 14 policies dropped with `CASCADE`, not enumerated | SQL-016 |
| Profile self-update allowlist does not cover future columns | SQL-017 |
| Audit content/timestamp is client-authored | SQL-019 |
| No tenant/clinic isolation by design | SQL-014 |
| `rls_role_test.sql` proves nothing | SQL-035 |

### 7.4 Verdict

RLS is **internally consistent for a trusted single clinic**, but it is **not a least-privilege or multi-tenant boundary**. If the product is sold to more than one clinic, `SQL-014` becomes Critical, not Medium. If all staff are trusted, document that decision and keep the audit trail and backups as the compensating controls.

---

## 8. Soft-delete and statistics consistency

### 8.1 Current state

| Layer | Behaviour |
|---|---|
| Room v33 | hard delete for every entity except `ReminderEntity` |
| Room 31→32 | deletes rows marked deleted, drops `is_deleted`/`deleted_at`/`deleted_by` from 18 tables |
| Server migrations | `20260925` adds the trio to 18 tables; `20260919` had just dropped one |
| Pull paths | filter `is_deleted = false` (13 paths) |
| Remote DELETE | hard delete |
| Old v31 clients | can still send soft-delete UPDATEs |
| Statistics | local counts over non-deleted rows; no server report |

### 8.2 Consequences

- A tombstoned server row is invisible to current clients but still present. It is never hard-deleted, so it accumulates.
- An old client can push an UPDATE after a current client’s hard delete, re-creating the row with `is_deleted = false`. Current clients then pull it back.
- `ReminderEntity` is the lone entity that still declares the trio (`ReminderEntity.kt:78-80`), so its delete path and the rest diverge.
- Statistics are computed from the local table. A resurrected row is counted; a tombstoned row is not pulled, so it is not counted. Different devices can show different totals.
- `20260919` drops `doctor_slot_exceptions.is_deleted`, then `20260925` re-adds it. The final state is neither “removed” nor intentional.

### 8.3 Decision required

Pick **one**:

- **Hard delete end-to-end (matches current app):** stop filtering `is_deleted` on pull, hard-delete tombstones, then drop the columns after the deprecation window. Smaller server change, irreversible.
- **Soft delete end-to-end (matches server columns):** restore the trio to all entities, make every pull/statistics path filter it, and remove the 31→32 purge. Larger client change, preserves history.

Do not leave the hybrid. `SQL-028` is the register entry.

---

## 9. Migration/application agreement

| Contract | Repo state | Verdict |
|---|---|---|
| 23 catalogue tables exist | 15 have no DDL | **broken** (`SQL-001`) |
| Migrations replay on an empty DB | cannot | **broken** (`SQL-001`, `SQL-036`) |
| Every migration is atomic | 9 lack `begin` | **broken** (`SQL-004`) |
| Unique keys exist after migration | 3 conditional indexes can be skipped | **broken** (`SQL-003`) |
| `waste_records` uses snake_case | migration renames; CSV still camelCase | **drift** (`SQL-002`) |
| Receipt numbers are server-generated | trigger accepts client value | **broken** (`SQL-018`) |
| Audit is append-only | correct | agreed |
| `finance_transactions.transaction_date` is non-null | client nullable | **latent** (`SQL-027`) |
| Delete is hard everywhere except reminders | server keeps 18 soft-delete tables | **broken** (`SQL-028`) |
| `inventory_transactions.visit_id` is cleared before visit delete | client explicitly keeps it | **broken** (`SQL-023`) |
| Sync group is atomic | one request per item | **broken** (`SQL-009`) |
| Deactivated staff cannot write | slot policies ignore activation | **broken** (`SQL-013`) |
| Doctor IDs are UUIDs | two columns are text | **broken** (`SQL-005`) |

---

## 10. Confirmed vs live-dependent

### 10.1 Confirmed without a database (29)

`SQL-001`–`SQL-006`, `SQL-008`, `SQL-009`, `SQL-011`, `SQL-013`–`SQL-015`, `SQL-017`–`SQL-020`, `SQL-022`, `SQL-024`–`SQL-030`, `SQL-032`, `SQL-033`, `SQL-035`–`SQL-037`.

These can be fixed from source alone, though several fixes still need a product decision (soft delete, patient retention, storage scoping).

### 10.2 Probable — live FK/data shape decides (5)

| ID | What to check |
|---|---|
| SQL-007 | how often legitimate deletes currently fail (PostgREST logs) |
| SQL-012 | `confdeltype` for `inventory_transactions.batch_id` |
| SQL-021 | `confdeltype` for every `patient_id`/`visit_id` FK; presence of `audit_logs.patient_id` FK |
| SQL-023 | `confdeltype` for `inventory_transactions.visit_id` |
| SQL-034 | live nullability of `vaccine_batches.vaccine_id` and `inventory_transactions.vaccine_id` |

### 10.3 Requires Verification — disposable/live database (3)

| ID | What to check |
|---|---|
| SQL-010 | whether any code path edits an `inventory_transactions` row after enqueue |
| SQL-016 | which 14 policies the `CASCADE` actually dropped, and whether the baseline replaced them |
| SQL-031 | `EXPLAIN`, `pg_stat_user_tables`, missing FK indexes, RLS per-row evaluation |

### 10.4 Exact live-check queries

See:
- `01-schema-and-relationships.md §7` (columns, constraints, RLS);
- `03-rls-and-security.md §6` (policies, definer functions, grants);
- `06-data-integrity-and-concurrency.md §8` (drift, negative stock, orphans, FK actions);
- `05-performance.md §5` (plans, unused indexes).

---

## 11. Tests actually run

### 11.1 Executed

| Check | Result |
|---|---|
| `find` / `wc` SQL inventory | 36 files, 3,147 lines |
| `grep` object counts | 9 tables, 56/68 policies, 21 functions, 14 triggers |
| Python CSV parse (columns, types, nullability, defaults) | 23 tables, 374 rows |
| Python table/DDL comparison | 15 missing, 1 extra |
| `grep`/`awk` source reads of every migration, test, and sync path | completed |
| Graphify graph queries (callers, blast radius) | completed; graph refreshed after pulls |
| `git log`/`git grep` history (`.gitignore`, `clearVisitLink`) | completed |

### 11.2 Read but not executed

- `supabase/tests/stock_trigger_arithmetic.sql`
- `supabase/tests/inventory_reconciliation_test.sql`
- `supabase/tests/rls_role_test.sql`
- `supabase/verify_security.sql`

### 11.3 Not run

- No `psql`, `pg_ctl`, `initdb`, Docker, or Supabase CLI is installed.
- No migration replay, RLS exercise, trigger execution, `EXPLAIN`, or concurrency test.
- No Android unit or instrumented test.
- No Edge Function or Worker invocation.

**Therefore:** “Confirmed” means **proven by source reading**, not by execution. The distinction is kept in every finding.

---

## 12. Prioritized remediation plan

### P0 — before the next release (data loss / broken core flows)

| # | Action | Findings | Effort | Dependency |
|---:|---|---|---|---|
| 1 | Make batch CREATE insert-only; never send `remaining_quantity` | SQL-008 | S | none |
| 2 | Route the moved-batch old-batch undo through the lock + negative guard | SQL-006 | S | none |
| 3 | Unlink ledger `visit_id` before visit DELETE; stop creating reversal rows with the dead visit id | SQL-023 | M | live FK check |
| 4 | Fix patient delete: tombstone vs hard delete, FK actions, audit target, todos, delete order | SQL-021, 022, 025 | L | product + live FK |
| 5 | Add the two missing stock tests and the timestamp parse test | SQL-006, 007, 030, 033 | S | none |
| 6 | Run a reconciliation report and freeze inventory writes if drift is large | SQL-008, 011 | S | live DB |

### P1 — next security/integrity batch

| # | Action | Findings | Effort |
|---:|---|---|---|
| 7 | Add `is_active_staff()` to both doctor-slot write policies | SQL-013 | S |
| 8 | Ignore client receipt numbers; always allocate in the trigger | SQL-018 | S |
| 9 | Replace the regex+cast prune with a safe typed column or per-row exception | SQL-020 | M |
| 10 | Fail the migration (or record a warning) when a conditional unique index is skipped | SQL-003 | S |
| 11 | Decide and implement one soft-delete model | SQL-028 | M–L |
| 12 | Replace `DROP FUNCTION … CASCADE` with explicit policy drops and assert the policy set | SQL-016 | M |
| 13 | Server-stamp `audit_logs.timestamp` and `user` | SQL-019 | S |
| 14 | Scope the `patient-docs` policy (or document clinic-wide access) | SQL-015 | M |
| 15 | Fix reminder DELETE identity, finance pending guard, IST date | SQL-024, 026 | S |
| 16 | Make `finance_transactions.transaction_date` resolution non-null | SQL-027 | S |

### P2 — structural and hygiene

| # | Action | Findings | Effort |
|---:|---|---|---|
| 17 | Generate and commit a baseline schema for the 15 missing tables + `todo_notification_events` + the missing notification migration | SQL-001, 002, 005 | L |
| 18 | Add migration replay + schema diff to CI | SQL-036 | M |
| 19 | Add `begin`/`commit` to the 9 migrations; move the stock guard before the drop | SQL-004 | S |
| 20 | Schedule `cleanup_write_rate_limits()` | SQL-032 | S |
| 21 | Run the index/plan audit; add only proven indexes; wrap RLS helpers in scalar subqueries | SQL-031 | M |
| 22 | Replace `rls_role_test.sql` with assertion-based RLS matrix; wire `verify_security.sql` into CI | SQL-035, 037 | M |
| 23 | Extend the reconciliation test for live nullability | SQL-034 | S |
| 24 | Document the single-clinic RLS model as an accepted risk, or narrow destructive permissions | SQL-014 | S–M |
| 25 | Convert money `Double` to minor units where finance is touched | contract hazard | M |

**Effort:** S ≤ 1 day, M 2–5 days, L > 1 week (including verification).

### 12.1 Suggested order of landing

1. **P0-1, P0-2, P0-5** — smallest, highest-confidence stock fixes + tests.
2. **P0-3** — visit delete unlink.
3. **P0-4** — patient delete, after the live FK query and the tombstone decision.
4. **P1-7, P1-8, P1-13** — small security wins.
5. **P1-9, P1-10, P1-11** — retention, constraints, soft delete.
6. **P2-17, P2-18** — baseline + CI; this unblocks safe replay for everything else.

### 12.2 What not to do

- Do not “repair” stock by running `update vaccine_batches set remaining_quantity = …` from the ledger. The ledger is missing rows rejected by the old negative guard (`20261022…:129-131`); reconcile against physical stock first.
- Do not add a second stock trigger; the final migration explicitly guards against that.
- Do not drop the soft-delete columns until no v31 client is active.
- Do not recreate `get_my_role()`.
- Do not add indexes from `SQL-031` without an `EXPLAIN` on a clone.

---

## 13. Stop condition and approval gate

This audit is complete. **No implementation has been performed.** The next step is a decision, not a code change:

1. approve **P0 only**, or
2. approve **P0 + selected P1**, or
3. request a deeper live audit first (the queries in §10.4).

Before any fix, two product decisions are required:

- **Patient deletion:** tombstone or hard delete? (`SQL-021`, `SQL-028`)
- **Soft delete:** hard end-to-end or soft end-to-end? (`SQL-028`)

Two live checks are required before the delete fixes:

- `confdeltype` for every `patient_id`/`visit_id` FK;
- whether `audit_logs.patient_id` and `inventory_transactions.visit_id` FKs exist at all.

Until those are answered, the delete fixes are `Probable`, not shippable.

---

## 14. Document index

| File | Contents |
|---|---|
| `00-sql-inventory.md` | file/object inventory, table provenance, coverage limits |
| `01-schema-and-relationships.md` | tables, FKs, type/nullability drift, SQL-001/002/005/027/029 |
| `02-functions-and-triggers.md` | every function/trigger, stock arithmetic, receipts, retention, SQL-006/007/018/020 |
| `03-rls-and-security.md` | policy model, role sources, storage, SQL-013–017/019 |
| `04-migrations.md` | ordering, transaction wrappers, conditional DDL, replay, SQL-003/004/036 |
| `05-performance.md` | indexes, query shapes, RLS cost, SQL-031/032 |
| `06-data-integrity-and-concurrency.md` | stock retry, sync atomicity, delete flows, soft delete, time, SQL-008–012/021–026/028/030 |
| `07-android-database-contracts.md` | entity↔table contract, types, nullability, identity, tests |
| `08-regression-test-plan.md` | current tests, seven test layers, finding→test map, minimal first batch |
| `09-final-sql-audit-report.md` | this report |

**Audit artefacts:** Graphify graph refreshed at `b91142a` (6,693 nodes / 16,363 edges / 333 communities); no SQL executed; no files outside `docs/bug-audit/sql/` modified.
