# Database & Sync Audit — Executive Summary

- **Repository:** `Neo_Child_Clinic`
- **Audited commit:** `ca4bed406bfd246ad83878fff9a4e178ebc5670f` (`main`, clean tree at audit start)
- **Audit date:** 2026-10-22
- **Mode:** read-only source/schema audit. No app code, Gradle file, migration, Supabase policy, or production data was changed. The only writes were the six requested `.opencode/skills/*/SKILL.md` files and this audit directory.
- **Live Supabase state:** not reachable from this environment; deployment-dependent findings are labelled **Medium/Low confidence** and listed in `15_Unknowns_And_Live_DB_Gaps.md`.

> **Post-audit remediation (2026-10-22):** after explicit approval, the DBA-01 stock-trigger fix was prepared as `supabase/migrations/20261022_fix_stock_trigger_update_arithmetic.sql`, with a disposable-Postgres regression test at `supabase/tests/stock_trigger_arithmetic.sql`. Ledger CREATE uploads in `SyncUploader.kt` are now insert-only. A diagnostic `public.inventory_reconciliation` view was added. The SQL tests pass on the fixed function and fail on the old function; the Kotlin change is uncompiled (no Android SDK). No live database was accessed, no data was repaired, and nothing was deployed. Details and remaining gates: `16_Phase1_Stock_Trigger_Remediation.md` and `17_Inventory_Reconciliation_View.md`. The audit verdicts below are unchanged.

## Verdict

The app has a real offline-first core: Room is the UI source of truth, business writes and queue enqueues share a transaction, uploads are ordered by dependency, pulls are conflict-guarded, and the server has receipt, RLS and stock triggers. That is materially better than the older audit docs describe.

Three systemic gaps remain:

1. **A live accounting trigger is arithmetically wrong.** The UPDATE branch of `update_batch_stock()` subtracts the old movement and then applies `new - old`, so retries and many quantity edits change stock twice or in the wrong direction. This is the highest-risk defect in the repository and contradicts the Bug Register's “NCC-002 fixed” status.
2. **Deletes do not converge.** Every pull is upsert-only. A row deleted on device A is never removed from device B, so statistics and pickers keep counting it. Hard deletes leave no tombstone to reconcile.
3. **Several screens are online-only and several permissions exist only in the UI.** Stock History and Audit History read Supabase directly; any active staff session can write/delete clinical and finance rows through PostgREST regardless of the admin/doctor buttons the UI hides.

## Top findings

| ID | Severity | Confidence | Finding | Evidence |
| --- | --- | --- | --- | --- |
| DBA-01 | **S1 Critical** | High (code; reproduced on PG 18.2) | Stock UPDATE trigger undoes the old movement and then applies `new-old`; retries and many edits corrupt `remaining_quantity` | `supabase/migrations/20261017_stock_trigger_duplicate_and_update_arithmetic.sql:66-104`; fix in `20261022_fix_stock_trigger_update_arithmetic.sql` |
| DBA-02 | **S1/S2** | Medium (live FK unknown) | Visit deletion no longer unlinks `inventory_transactions.visit_id`, while the uploader still relies on that unlink; if the server FK exists, every such visit DELETE fails permanently | `VaccinationRepositoryImpl.kt:429-430`, `SyncUploader.kt:537-551` |
| DBA-03 | **S2 High** | High | Finance pull “self-heal” bypasses the pending-queue guard and overwrites unsynced local finance edits | `FinanceRepositoryImpl.kt:413-431` |
| DBA-04 | **S2 High** | High | Finance date normalization uses the device timezone, not IST; on a non-IST device it rewrites dates and re-uploads every transaction on every pull | `FinanceRepositoryImpl.kt:52-59, 394-431` |
| DBA-05 | **S2 High** | High | Patient deletion queues reminder DELETEs with the local Room id, not `serverId`; pulled reminders therefore survive remotely | `PatientRepositoryImpl.kt:215-223`, `ReminderEntity.kt:145,168-170` |
| DBA-06 | **S2 High** | High | No tombstone/reconciliation path: remote deletions never disappear locally (known open NCC-023) | all `refresh*()` loops, e.g. `PatientRepositoryImpl.kt:91-145` |
| DBA-07 | **S2 High** | High | Stock History is remote-only; the inventory ledger is never pulled into Room, so the screen fails offline | `StockHistoryViewModel.kt:129-138,167-176`, `InventoryRepositoryImpl.kt:277-292` |
| DBA-08 | **S2 High** | High | RLS grants full CRUD on clinical/finance tables to every active staff; admin/doctor restrictions exist only in Compose | `20260929000000_security_baseline.sql:40-88`, `PatientDetailsScreen.kt:48-50` |
| DBA-09 | **S2 High** | High | Queue dedupe is delete-then-insert, not atomic; concurrent enqueues can duplicate rows | `SyncRepositoryImpl.kt:68-83` |
| DBA-10 | **S2 High** | Medium | Partial borrow returns are capped only against the local return total; two devices can jointly return more than was borrowed and mint stock | `BorrowRepositoryImpl.kt:195-253` |
| DBA-11 | **S3 Medium** | High | Screen-specific refreshes bypass `RefreshDataUseCase`'s single-flight, so the same tables are fetched several times per launch/resume | `RefreshDataUseCase.kt`, `BorrowedViewModel.kt:82,174-175` |
| DBA-12 | **S3 Medium** | Medium | Consultation/personal-reminder pulls insert children without checking the parent exists; a dangling remote row fails the whole refresh step | `ConsultationRepositoryImpl.kt:205-215`, `PersonalReminderRepositoryImpl.kt:172-183` |

Full fields (reproduction, root cause, impact, fix, verification) are in `12_Prioritized_Issue_Register.md`.

## Verdict on the 13 requested risks

| # | Risk | Verdict |
| --- | --- | --- |
| 1 | UI loading directly from Supabase | **Partly confirmed.** Clinical screens read Room. Stock History (`remoteOnly=true`) and Audit History are remote-only; profile/bootstrap, devices and documents also use online calls. |
| 2 | Stale Room after remote update | **Confirmed for deletes**, not for ordinary updates. Pulls upsert rows but never remove missing IDs (DBA-06). |
| 3 | Failed remote write shown as success | **Partly confirmed by design.** Save success means “committed locally”; the dashboard shows a pending count and repeated failures notify, but the editor never waits for a server ack. |
| 4 | Retried writes creating duplicates | **Confirmed for stock.** PostgREST upserts are otherwise idempotent, but `inventory_transactions` retries hit DBA-01 and re-apply movement. Queue duplicates are also possible (DBA-09). |
| 5 | Vaccination edit double-deducting inventory | **Not reproduced at HEAD for one device.** The edit is transactional and uses a per-batch diff. Server-side retry arithmetic can still corrupt stock (DBA-01). |
| 6 | Borrow/partial-return wrong quantities | **Correct on one device** (repository re-checks outstanding). Multi-device over-return is unguarded (DBA-10). |
| 7 | Soft-deleted records in statistics | **No confirmed leak.** Deletes are hard-local and remote pulls filter `is_deleted=false`. The stale-row problem is deletion non-convergence, not soft-delete counting. |
| 8 | Reminder/next-vaccination inconsistency | **Partly confirmed.** Visit-delete cleanup prefers `serverId`, but patient-delete cleanup does not (DBA-05); edit reconciliation is otherwise delete-then-create. |
| 9 | Timezone wrong dates in app/widgets | **Confirmed.** An IST date layer exists but finance normalization, stock filters, dashboard “today”, notifications and several formatters still use the device zone (DBA-04, DBA-14). |
| 10 | Duplicate DB requests from multiple screens | **Confirmed.** Repository-level refreshes are not coalesced with the global pipeline (DBA-11). |
| 11 | Slow queries | **Confirmed risks, not measured.** Unbounded full-table pulls, remote-only paginated history, missing `sync_queue.entityId` index, missing ledger timestamp index. No `EXPLAIN` was run. |
| 12 | Cache invalidation stale results | **Mostly sound.** `PatientCache` is observer-invalidated and generation-guarded; date-parse and widget caches are bounded. Risk is from partial pulls, not the cache itself. |
| 13 | Security relying on client filters without DB authorization | **Confirmed.** Any active staff can CRUD patients, visits, finance, inventory and staff schedules via PostgREST; admin-only UI actions are not admin-only in SQL (DBA-08). |

## Positive controls verified

- Business write + queue enqueue are inside one Room transaction in the main repositories.
- Upload ordering is dependency-aware, including DELETE reversal (`SyncOrderingTest`).
- Receipt numbers are assigned once by a `SECURITY DEFINER` trigger and are immutable on update.
- The stock trigger has a negative-stock guard and the 2026-10-18 migration installs it loudly rather than silently skipping.
- Audit logs are append-only server-side; the client uses duplicate-tolerant INSERT.
- Room is SQLCipher-encrypted with a Keystore passphrase.
- `SyncReconciler` pushes before pulling on startup and reconnect; both legs are single-flight.
- `PatientCache` invalidates from Room observers, with a generation guard against stale reloads.

## Evidence limits

- No Android SDK/emulator is installed in this environment, so **no Gradle or instrumentation tests were executed**; test results are reported as “not run”, never as passes.
- The live Supabase schema, trigger installation, FK definitions and deployed RLS were not queried. DBA-02 and DBA-21 depend on that state.
- Query cost statements are static estimates from schema/index definitions; no `EXPLAIN QUERY PLAN` or server `EXPLAIN` was executed.

## Deliverables

1. `01_Executive_Summary.md` — this file
2. `02_Architecture_Inventory.md`
3. `03_Read_Path_Inventory_And_Matrix.md`
4. `04_Write_Path_Inventory_And_Matrix.md`
5. `05_Data_Flow_Diagrams.md`
6. `06_Room_Supabase_Sync_Model.md`
7. `07_Cache_And_Refresh_Analysis.md`
8. `08_Security_And_RLS.md`
9. `09_Performance.md`
10. `10_Data_Integrity_And_Business_Logic.md`
11. `11_Test_Coverage_And_Plan.md`
12. `12_Prioritized_Issue_Register.md`
13. `13_Target_Architecture.md`
14. `14_Phased_Implementation_Roadmap.md`
15. `15_Unknowns_And_Live_DB_Gaps.md`
16. `16_Phase1_Stock_Trigger_Remediation.md`
17. `17_Inventory_Reconciliation_View.md`

## Approval gate

The audit itself made no changes. After explicit approval, the DBA-01 fix and diagnostic view were prepared locally; see `16_Phase1_Stock_Trigger_Remediation.md` and `17_Inventory_Reconciliation_View.md`. Deployment and historical data repair remain gated on Phase 0 live verification, a restorable backup, and a separate repair approval.
