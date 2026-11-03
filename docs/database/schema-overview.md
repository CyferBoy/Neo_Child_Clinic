# Neo Child Clinic — Database Architecture & Schema Overview

This document describes the persistence architecture, Room database schema (v33), foreign keys, indexes, and Supabase Row Level Security (RLS) rules.

---

## 1. Local Database Architecture (Room v33)

- **Engine**: SQLite encrypted via SQLCipher with keys in Android Keystore.
- **Master Database Definition**: `com.neochildclinic.data.local.database.AppDatabase`
- **Schema Snapshot**: Stored in `app/schemas/com.neochildclinic.data.local.database.AppDatabase/33.json`.
- **Foreign Keys**: Enabled with `ON DELETE CASCADE` where applicable, with triggers preventing orphan rows.

---

## 2. Table Catalog & Entities

| SQLite Table | Entity Class | Primary Key | Description |
|---|---|---|---|
| `patients` | `PatientEntity` | `id` (UUID) | Master patient registry, clinic ID, DOB, guardian info |
| `vaccines` | `VaccineEntity` | `id` (UUID) | Vaccine definitions, target ages, disease categories |
| `vaccine_batches` | `VaccineEntity` (Batch) | `batchId` (UUID) | Physical vials, batch numbers, remaining quantities, expiry |
| `patient_visits` | `VaccinationEntity` | `id` (UUID) | Clinical vaccination visits, total cost, payment status |
| `vaccination_items` | `VaccinationItemEntity` | `id` (UUID) | Line items for a visit: vaccine ID, batch ID, dosage, price |
| `consultations` | `ConsultationEntity` | `id` (UUID) | Clinical consultations, vitals, prescriptions, charges |
| `reminders` | `ReminderEntity` | `id` (UUID) | Scheduled vaccination reminders, due dates, statuses |
| `personal_vaccine_reminders` | `PersonalReminderEntity` | `id` (UUID) | Ad-hoc patient alerts and reminders |
| `inventory_transactions` | `InventoryTransactionEntity` | `id` (UUID) | Stock movements: purchases, usage, adjustments, transfers |
| `inventory_deductions` | `InventoryDeductionEntity` | `id` (UUID) | Linking vaccination visits to specific batch stock reductions |
| `borrow_records` | `BorrowEntity` | `id` (UUID) | Inter-clinic vaccine borrowings |
| `borrow_returns` | `BorrowReturnEntity` | `id` (UUID) | Batch return records for borrowed vaccines |
| `waste_records` | `WasteEntity` | `id` (UUID) | Spoiled, broken, or expired vial wastage logs |
| `expenses` | `ExpenseEntity` | `id` (UUID) | Clinic operational expenses |
| `finance_transactions` | `FinanceEntity` | `id` (UUID) | Consolidated financial ledger |
| `doctor_weekly_slots` | `DoctorAvailabilityEntities` | `id` (UUID) | Weekly recurring doctor consultation availability |
| `doctor_slot_exceptions` | `DoctorAvailabilityEntities` | `id` (UUID) | Unavailability exceptions and modified hours |
| `patient_notes` | `PatientNotesEntity` | `id` (UUID) | Ongoing clinical notes on patient profiles |
| `consultation_todos` | `PatientTodoEntities` | `id` (UUID) | Follow-up action items from consultations |
| `vaccination_todos` | `PatientTodoEntities` | `id` (UUID) | Follow-up action items from vaccination visits |
| `audit_logs` | `AuditLogEntity` | `id` (UUID) | Immutable audit trails of clinical operations |
| `sync_queue` | `SyncQueueEntity` | `id` (UUID) | Pending offline mutations awaiting Supabase push |
| `widget_due_cache` | `WidgetDueEntity` | `id` (UUID) | Denormalized snapshot for home screen widget |
| `backup_history` | `BackupHistoryEntity` | `id` (UUID) | Local and cloud backup execution logs |
| `profiles` | `ProfileEntity` | `id` (UUID) | Staff user profiles, clinic roles, display names |

---

## 3. Cloud Database (Supabase PostgreSQL) & RLS

1. **Authentication**: All client connections authenticate with Supabase Auth (JWT).
2. **Role Authorization**: Role claims (`admin`, `doctor`, `nurse`, `staff`) reside securely inside `app_metadata.role`, verified by `is_active_staff()` PostgreSQL functions.
3. **Trigger Invariants**:
   - `stock_trigger`: Enforces that batch updates apply net quantity deltas without duplicate decrements on retried upserts.
   - `rate_limit`: Protects the backend by limiting mutating queries to 600 per user per minute.
   - `audit_prune`: Automatically cleans up historical audit rows older than clinic retention policy windows.
4. **Row Level Security (RLS)**: Enforced on 100% of public tables. Non-authenticated or inactive staff have zero read or write access.
