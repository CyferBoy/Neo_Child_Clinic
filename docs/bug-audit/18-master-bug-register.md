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
