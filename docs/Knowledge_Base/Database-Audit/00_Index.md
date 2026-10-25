# Database Audit Index

> Archive of the 2026-10-20 database/data-access audit. The findings are historical; the
> canonical current status is [[Quality/Bug Register]] and [[Quality/Audit Reconciliation]].
> Start with the executive summary, then use the section that matches the question.

## Contents

- [[Database-Audit/01_Executive_Summary]] — scope, method, headline findings
- [[Database-Audit/02_Architecture_Inventory]] — tables, entities, sync surfaces
- [[Database-Audit/03_Read_Path_Inventory_And_Matrix]] — who reads what
- [[Database-Audit/04_Write_Path_Inventory_And_Matrix]] — who writes what
- [[Database-Audit/05_Data_Flow_Diagrams]] — read, write and sync flows
- [[Database-Audit/06_Room_Supabase_Sync_Model]] — local/remote identity and conflict rules
- [[Database-Audit/07_Cache_And_Refresh_Analysis]] — cache layers and invalidation
- [[Database-Audit/08_Security_And_RLS]] — policies, grants and trust boundaries
- [[Database-Audit/09_Performance]] — query shapes, indexes and payload sizes
- [[Database-Audit/10_Data_Integrity_And_Business_Logic]] — invariants and drift
- [[Database-Audit/11_Test_Coverage_And_Plan]] — what the audit could and could not prove
- [[Database-Audit/12_Prioritized_Issue_Register]] — ranked findings
- [[Database-Audit/13_Target_Architecture]] — the proposed end state
- [[Database-Audit/14_Phased_Implementation_Roadmap]] — sequencing
- [[Database-Audit/15_Unknowns_And_Live_DB_Gaps]] — what needs the live database
- [[Database-Audit/16_Phase1_Stock_Trigger_Remediation]] — the stock-trigger batch
- [[Database-Audit/17_Inventory_Reconciliation_View]] — reconciliation view

## Related

- [[Quality/Data Access Architecture Audit 2026-10-20]]
- [[Quality/Data Access Findings Register 2026-10-20]]
- [[Quality/Data Access Audit Summary 2026-10-20]]
- [[Quality/Phase 2 Implementation Plan 2026-10-20]]
