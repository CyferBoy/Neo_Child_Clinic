# Audit Reconciliation Report

## 1. Executive Summary
This report reconciles the repository's internal bug audit reports with the external audit report (`Neo_Child_Clinic_Audit_2026-10-24.md`). The external audit confirms that presentation-layer status filtering is present for Statistics, but identifies important subtleties: status filtering is distinct from soft-delete detection, and a business-rule contradiction exists around `ACTIVE` visit counting. Furthermore, it highlights a critical SQL trigger defect regarding batch reassignments on UPDATE, and exposes documentation inconsistencies between the workflow tracker and the master Bug Register.

## 2. Integration of External Audit Findings
- **Statistics (All) Filter Nuance**: Confirmed that `StatisticsUtils.filterValidVaccinations()` checks `ReminderStatus.COMPLETED` or `EXTERNAL`, but does not detect true soft-deletes or resolve the KDoc vs. filter contradiction regarding `ACTIVE` visits.
- **Stock Trigger Batch Reassignment (NCC-AUDIT-001)**: Confirmed that `supabase/migrations/20261021001000_stock_trigger_upsert_fix.sql` handles same-batch quantity deltas correctly (`- OLD.quantity + NEW.quantity`), but fails to debit/credit two different batches if an `UPDATE` changes `batch_id`. **Resolved 2026-10-25:** `20261022_fix_stock_trigger_update_arithmetic.sql` reconciles both batches; `supabase/tests/stock_trigger_arithmetic.sql` runs in `.github/workflows/supabase-sql.yml`. Production deployment remains NCC-AUDIT-003.
- **Documentation Discrepancies**: The workflow tracker's claim that all phases and findings are fully closed conflicts with `Bug Register.md`, which still lists several open S4/S3 findings (NCC-027 through NCC-031, NCC-036, NCC-039).

## 3. Reconciled Findings & Corrections
1. **Statistics Filter Semantics**: Clarified that `filterValidVaccinations` is a lifecycle-status gate, not a deletion detector. `ACTIVE` visits should remain excluded from clinical-event totals unless explicitly intended as pending encounters.
2. **Stock Trigger SQL**: Added `NCC-AUDIT-001` to capture the batch-reassignment limitation in the stock trigger.
3. **Bug Register Alignment**: Re-established `Bug Register.md` as the authoritative open/closed tracker, noting that documentation must not claim 100% completion while lower-severity latent items remain registered.
