# Master Bug Register (Reconciled)

| ID | Title | Severity | Confidence | Status | Area | Evidence / Notes |
|---|---|---|---|---|---|---|
| **NCC-002** | Postgres stock trigger batch reassignment flaw | S1 | Confirmed | ✅ **Fixed** | Supabase | `20261022_fix_stock_trigger_update_arithmetic.sql` applies same-batch deltas and reconciles both batches on reassignment; `supabase/tests/stock_trigger_arithmetic.sql` runs in `.github/workflows/supabase-sql.yml`. |
| **NCC-027** | `isLowStock` uses `<=`, flagging at-threshold stock | S4 | Confirmed | ⚠️ **Open** | Inventory | `InventoryRepositoryImpl.kt:78` |
| **NCC-028** | `consultations.visitId` defaults to `""` against FK | S4 | Confirmed | ⚠️ **Open** | Room | `ConsultationEntity.kt:33` |
| **NCC-029** | `AuditLogDao` comment claims missing composite index | S4 | Confirmed | ⚠️ **Open** | Room | `AuditLogDao.kt:29-33` |
| **NCC-030** | Audit-count guards query wrong column | S4 | Confirmed | ⚠️ **Open** | Inventory | `VaccineDao.kt` |
| **NCC-031** | `FinanceDao` sorts on unindexable `COALESCE` expression | S4 | Confirmed | ⚠️ **Open** | Room | `FinanceDao.kt` |
| **NCC-036** | `viewModelScope.launch` bodies lack try/catch | S3 | Confirmed | ⚠️ **Open** | UI | Multiple ViewModels |
| **NCC-039** | `AppDatabase` double singleton initialization | S4 | Confirmed | ⚠️ **Open** | DI | `AppDatabase.kt`, `DatabaseModule.kt` |
| **NCC-001 through NCC-042** | (Remaining items) | S1–S4 | Confirmed | ✅ **Fixed** | Various | Reconciled against `Bug Register.md` and repository `HEAD`. |

---

## SQL audit verification — 2026-10-26

The table above is preserved as history. It is **not** a current release-status record. The blanket `NCC-001 through NCC-042` row is contradicted by the canonical register (`docs/Knowledge_Base/Quality/Bug Register.md:81-98` lists NCC-027, 028, 031 and 036 as Open; NCC-038 needs a decision) and by the SQL audit verification. The table above also carries stale `Open` rows for NCC-029, NCC-030 and NCC-039; the canonical register records those as Fixed (`docs/Knowledge_Base/Quality/Bug Register.md:83-84`, `:93`).

Source snapshot for this reconciliation: `main` @ `b91142a`; audit evidence: `ca4bed4` plus the recorded dirty `SyncUploader.kt`. Full evidence is in `docs/bug-audit/sql/10-findings-verification.md`; fixes are in `11-remediation-plan.md`; migration/data repair in `12-migration-and-data-repair-plan.md`; tests/rollback in `13-test-and-rollback-plan.md`.

### Corrected prior claims

| Prior row | Prior status | Verified status | Why |
|---|---|---|---|
| NCC-002 (stock trigger) | ✅ Fixed | **Fixed in source; deployment and historical data unverified** | The final `20261022` function passes the local PostgreSQL 18.2 arithmetic test. The release note still names the older `20261021001000`, and the migration is not transaction-safe under a stray trigger (VER-01). Historical stock is not repaired. |
| NCC-023 (remote deletions) | ✅ Fixed (absence reconciliation) | **Fixed at HEAD; regression risk** | `RemotePrune` exists, but an offset pull is not a snapshot and can transiently delete a synced local row; the patient pending-set guard is outside a Room transaction (VER-04/VER-05). |
| NCC-001 through NCC-042 | ✅ Fixed | **Superseded — not supported** | 18 SQL findings remain confirmed open, 2 are partial, and 3 need live state. Do not use this row for release readiness. |
| NCC-029, NCC-030, NCC-039 | ⚠️ Open | **Fixed in canonical register** | The table above is stale for these three rows; `docs/Knowledge_Base/Quality/Bug Register.md:83-84`, `:93` records Fixed. |

### DBA findings status

| ID | Status | Short reason |
|---|---|---|
| DBA-01 | **Fixed in source; deployment/data unverified** | Final trigger passes arithmetic test; env not proven; no historical repair |
| DBA-02 | **Open — live FK required** | Code keeps `visit_id`; uploader contract still describes nulling |
| DBA-03 | **Open** | Finance heal overwrites pending edit and re-enqueues |
| DBA-04 | **Open** | Device timezone used for finance dates; null fallback |
| DBA-05 | **Open** | Patient cleanup uses local reminder id, not `serverId`; no group |
| DBA-06 | **Fixed at HEAD** | `RemotePrune` added after audit; residual pagination/transaction risk |
| DBA-07 | **Open** | Stock History is remote-only; ledger never pulled |
| DBA-08 | **Open — needs decision** | Blanket active-staff CRUD in baseline RLS |
| DBA-09 | **Open** | Non-atomic queue dedupe; no pending unique index |
| DBA-10 | **Open** | Borrow status uses caller’s stale remaining quantity |
| DBA-11 | **Open** | Repository refreshes bypass single-flight |
| DBA-12 | **Partially fixed** | Consultation orphan guard fixed; personal reminders open |
| DBA-13 | **Open** | Vaccination edit rewrites `created_at` |
| DBA-14 | **Open** | Device-default timezone in clinic-day logic |
| DBA-15 | **Open — needs decision** | Audit history remote-only; no offline label/cache |
| DBA-16 | **Partially fixed** | `recordLog` exists; `log()` still used inside a transaction |
| DBA-17 | **Open** | Missing `(entityName, entityId, status)` queue index |
| DBA-18 | **Open** | No-op downloader treated as successful conflict refresh |
| DBA-19 | **Open — live index unverified** | Local/server reminder unique keys differ |
| DBA-20 | **Open — performance** | Full-table offset pulls; aggregate Flows load all history |
| DBA-21 | **Open** | Finance ordering expression has no matching index |
| DBA-22 | **Open** | Leading-wildcard patient search |
| DBA-23 | **Open** | Patient conflict branch bypasses pending guard; deletes ungrouped |
| DBA-24 | **Open — live state required** | Trigger installation/ledger not verifiable in repo |
| DBA-25 | **Reported defect fixed; residual** | Per-install suffix present; no `23505` retry |
| DBA-26 | **Open** | Merge moves only three tables, then deletes duplicate |

**Counts:** 3 already fixed at HEAD (DBA-01, DBA-06, DBA-25), 2 partially fixed (DBA-12, DBA-16), 18 confirmed open, and 3 requiring live state (DBA-02, DBA-19, DBA-24). DBA-01’s fix is not a release closure until deployment and data repair are verified.

### Implementation progress (uncommitted, 2026-10-26)

Approval was received. These code-only edits exist in the working tree but are **not compiled or tested** here; the statuses above stay provisional until evidence exists:

- **DBA-03/04:** finance pull no longer overwrites a pending edit or enqueues work from a pull; finance/expense date conversion uses the clinic IST date.
- **DBA-05/23 (partial):** patient delete now uses reminder `serverId` and one sync group; visit deletes accept the caller's group. The DBA-23 patient-conflict branch and DBA-26 merge remain open.
- **DBA-10:** borrow return status uses the freshly computed outstanding quantity; the stale caller parameter was removed.
- **DBA-13:** visit edits preserve the existing `createdAt`.
- **DBA-16:** the borrow audit write joins the caller's transaction.

No migration, data repair, RLS change or production write was applied. A Gradle compile/test attempt failed before compilation with `Service 'SystemInfo' is not available` (Gradle 9.7.1 on this Android/Termux host), and no Android SDK/device is available. No test is claimed passing.

**Approval received 2026-10-26 for the plan.** Only the unverified code-only subset above is in progress. The live preflight/backup in `12-migration-and-data-repair-plan.md` is still gated by read-only database access; no production write is approved by this register.
