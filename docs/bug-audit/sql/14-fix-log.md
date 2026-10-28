# 14 — Fix Log

**Date:** 2026-10-26
**Basis:** `93021c2` plus this working tree. Supersedes the status columns in `00`–`09` for the findings listed here; the analysis in those documents remains `b91142a`.
**Approval:** “if anything remains please fix it” (2026-10-26). No production database or live project was touched.

---

## 1. What was fixed

| Finding | Change | Files | Verification |
|---|---|---|---|
| **SQL-006** (Critical) | Moved-batch UPDATE now undoes the OLD batch through the same `FOR UPDATE` lock and negative guard as every other path. Forward migration; `20261022` is not edited. | `supabase/migrations/20261026_stock_moved_batch_guard.sql` | Applied to PostgreSQL 16.15. Test fails against `20261022` alone (`moved purchase did not hit the old-batch guard`) and passes after `20261026`; the full arithmetic test still passes. |
| **SQL-008** (Critical) | Batch CREATE is now `INSERT … ON CONFLICT DO NOTHING` (`ignoreDuplicates = true`). A lost-response retry cannot write `remaining_quantity = 0` over the stock the paired PURCHASE already applied. Payload construction extracted to a testable function. | `SyncUploader.kt`, `BatchUpsertTest.kt` | `BatchUpsertTest` (2 tests) passes: CREATE is insert-only and sends 0; UPDATE is a plain upsert and omits the column. |
| **SQL-013** (High) | Both doctor-schedule write policies now require `public.is_active_staff()` as well as ownership/JWT admin. | `20261026_rls_slot_writes_require_active_staff.sql` | Applied against stubbed `auth`/`profiles`; both policies exist and their `qual` contains `is_active_staff()`. Migration fails if either policy still ignores activation. |
| **SQL-018** (High) | Receipt trigger always allocates on INSERT; a client-supplied number is discarded. UPDATE still never changes an issued number. | `20261026_receipt_number_server_allocated.sql` | Inserted `FORGED-1`; stored value was `NEO-26/27-000001`, second insert `…-000002`. |
| **SQL-020** (High) | Prune compares the ISO date prefix as text instead of casting text to `timestamptz`; a malformed timestamp can no longer abort the delete. | `20261026_audit_prune_without_cast.sql` | Inserted `2025-13-45 99:99:99` alongside old/new valid rows; prune deleted the old row, kept the malformed row, and raised nothing. |
| **SQL-021** (Critical, live-dependent) | `audit_logs.patient_id` and `inventory_transactions.patient_id` FKs rebuilt as `ON DELETE SET NULL`; guarded, refuses to run if orphaned rows exist. | `20261026_child_refs_survive_parent_delete.sql` | Synthetic FKs with NO ACTION: patient delete now succeeds, audit/ledger rows survive with null links. |
| **SQL-023** (High) | `inventory_transactions.visit_id` FK rebuilt as `ON DELETE SET NULL`; the ledger row outlives the visit. | same migration | Visit delete succeeds; ledger row retained with `visit_id = null`. |
| **SQL-025** (High) | In `deletePatient`, consultations are deleted and enqueued **before** the visits they own, so the group no longer deletes a visit while its consultation still references it. | `PatientRepositoryImpl.kt` | Full unit suite compiles and passes; no live FK test. |
| **SQL-030** (Medium) | `isoToLong` parses Postgres text timestamps (`2026-08-22 03:48:08.105121+00`) as an offset instead of falling through to the IST wall-time parser. | `DateUtils.kt`, `PatientUtilsTest.kt` | New test asserts the UTC epoch equals `Instant.parse("…Z")`; passes. |
| **SQL-032** (Medium) | `cleanup_write_rate_limits()` scheduled hourly when `pg_cron` is available. | `20261026_schedule_rate_limit_cleanup.sql` | Applied without `pg_cron`: migrates with a notice instead of failing. |
| **SQL-033** (Medium) | Stock test extended with a real moved-purchase guard case, not the negative-row case that already passed. | `stock_trigger_arithmetic.sql` | Red/green verified (see SQL-006). |
| CI | The SQL workflow now applies the new stock, prune, receipt and FK migrations and asserts the behaviour above. | `.github/workflows/supabase-sql.yml` | The whole CI sequence was run locally against PostgreSQL 16.15 and passed. |

**SQL-010** is not reachable: no code path updates an `inventory_transactions` row after enqueue, so the insert-only CREATE retry cannot drop a changed payload. It is closed as “no reproduction”, not fixed.

---

## 2. Verification actually run

**Android unit tests** (Linux, JDK 21, Android SDK, Gradle 9.7.1, offline):

```bash
./gradlew :app:testDebugUnitTest --offline
# BUILD SUCCESSFUL — 328 tests, 0 skipped, 0 failures, 0 errors
# BatchUpsertTest: 2/2; PatientUtilsTest: 5/5
```

**PostgreSQL 16.15** (disposable cluster, `/tmp/opencode`, port 55432, no production access):

```bash
# stock: red on the old function, green on the new one
psql -f supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql
psql -f supabase/tests/stock_trigger_arithmetic.sql        # FAIL: moved purchase did not hit the old-batch guard
psql -f supabase/migrations/20261026_stock_moved_batch_guard.sql
psql -f supabase/tests/stock_trigger_arithmetic.sql        # PASS x2

# retention, receipt, parent-delete FK: each applied and asserted in its own database
```

The full `.github/workflows/supabase-sql.yml` step sequence was executed locally in one database and finished with `PASS: full CI sequence`.

---

## 3. What is still open, and why

| Finding | Why it was not fixed |
|---|---|
| SQL-001, SQL-002, SQL-005 | Need the live schema: 15 tables have no DDL, and `doctor_id`/timestamp types must be read from `pg_catalog`. Writing a baseline from the stale scratch file would enshrine the drift. |
| SQL-003 | Making the conditional unique indexes unconditional fails deployment while duplicates exist. Needs a read-only duplicate count first. |
| SQL-004 | Editing already-applied migrations is riskier than the missing wrapper; the new migrations are wrapped. A CI lint needs the nine legacy files handled deliberately. |
| SQL-007 | Whether a correction may drive stock negative is a stock-policy decision. The guard is deliberate. |
| SQL-009 | Group atomicity needs a server-side transaction boundary (RPC per operation), not a one-file change. |
| SQL-011 | Pulling the ledger is a sync-surface change; it needs pagination, pruning and a refresh test. |
| SQL-012 | Depends on the live `inventory_transactions.batch_id` FK action and the delete policy for history-bearing batches. |
| SQL-014, SQL-015, SQL-017, SQL-019 | RLS least privilege, storage scoping, profile allowlist and server-stamped audit identity are owner decisions, not defects with one correct answer. |
| SQL-016 | Which 14 policies the `CASCADE` actually removed is only knowable from the live `pg_policies` history. |
| SQL-022, SQL-028 | Retention model: tombstone vs hard delete, and soft delete end-to-end vs hard delete end-to-end. |
| SQL-027 | A null transaction date cannot sync, but defaulting it to “today” would silently misdate a historical row; the caller needs a real date. |
| SQL-029, SQL-031 | Schema type change and index work need live data and `EXPLAIN`. |
| SQL-034–SQL-037 | Test/CI work: the reconciliation test needs live nullability, the RLS script needs seeded staging users, and the baseline is SQL-001. |

Two product decisions still block the delete/soft-delete group:

1. **Patient deletion:** tombstone or hard delete. The FK change makes hard delete *work*; it does not decide whether history should be retained.
2. **Soft delete:** hard end-to-end (matches the app) or soft end-to-end (matches the server columns).

---

## 4. Deployment notes

- The new migrations are ordered after `20261025` and wrapped in `begin; … commit;`.
- `20261026_child_refs_survive_parent_delete.sql` takes an `ACCESS EXCLUSIVE`-class lock while rebuilding three FKs. Run it outside clinic hours; it is guarded and aborts before changing anything if orphaned rows exist.
- `20261026_receipt_number_server_allocated.sql` changes only future INSERTs; existing numbers are untouched.
- `20261026_stock_moved_batch_guard.sql` refuses to run if more than one stock trigger is installed. Resolve the stray trigger first (VER-01).
- The client fix for SQL-008 must ship with the app; the server trigger change is independent.
- No historical stock, finance or audit row was repaired.
