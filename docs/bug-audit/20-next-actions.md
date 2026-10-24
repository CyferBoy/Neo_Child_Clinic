# Next Actions & Recommendations

## Priority Order

1. **Address Stock Trigger Batch Reassignment (NCC-AUDIT-001)**:
   - Update `20261021001000_stock_trigger_upsert_fix.sql` to explicitly debit `OLD.batch_id` and credit `NEW.batch_id` if an UPDATE statement reassigns the transaction to a different batch.
2. **Resolve Documentation Contradictions**:
   - Reconcile `workflow-status.md` and `16-release-readiness.md` to acknowledge that while core functional bugs and the Statistics filter are resolved, secondary S4 items (NCC-027 to NCC-031, NCC-039) remain registered as Open.
3. **Clarify Statistics Active-Visit Semantics**:
   - Resolve the KDoc comment in `StatisticsUtils.kt` regarding `ACTIVE` visits to match the actual behavior of `filterValidVaccinations()`.
4. **Environment Verification**:
   - Run full unit tests and Room migration tests in a network-connected environment with Gradle available.
5. **Staging Deployment**:
   - Apply Supabase migrations (`20261021000000` and `20261021001000`) to a non-production Supabase instance and run integration test scenarios.
