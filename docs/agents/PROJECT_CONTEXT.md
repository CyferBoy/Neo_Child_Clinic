# Project Context

**Application Name**: Neo Child Clinic — Vaccine Manager  
**Repository**: `CyferBoy/Neo_Child_Clinic`  
**License**: Apache License 2.0  
**Target Domain**: Pediatric Clinical Practice, Child Immunization, and Inventory Management  

---

## 1. Domain Background & Clinical Purpose

Pediatric clinics face unique operational challenges that differ substantially from adult general medicine:
1. **Dynamic Immunization Schedules**: Vaccine schedules vary strictly based on child age in days/months/years, previous dose intervals, vaccine brand compatibilities, and catch-up schedules (e.g., IAP - Indian Academy of Pediatrics and WHO immunization guidelines).
2. **Batch and Vial Expiry Tracking**: Vaccines are temperature-sensitive, multi-vial biologics with specific batch numbers, manufacturer expiries, and wastage risks.
3. **Inter-Clinic Vaccine Borrowing**: Clinics routinely borrow or lend vaccine vials to neighboring pediatric practitioners during stockouts, requiring rigorous double-entry tracking of lent/returned inventory.
4. **Offline Resilience**: Pediatricians in consultation rooms or mobile vaccination camps often experience erratic internet connectivity. The application must never block patient triage, vaccine administration, or billing due to network drops.

---

## 2. Core Business Domains (20 Business Modules)

The application organizes functionality into 20 modular business features:

| Business Module | Feature Directory | Primary Responsibility |
|---|---|---|
| **Patient Registry** | `feature/patient/` | Demographics, guardian contact, date of birth, blood group, medical history |
| **Vaccination Engine** | `feature/vaccination/` | Clinical vaccine administration, multi-table atomic transactions, batch deductions |
| **Vial Inventory** | `feature/inventory/` | Batch tracking, stock entries, expiration alerts, stock movement logs |
| **Borrowed Stock** | `feature/borrowed/` | Inter-clinic vial loans, borrower/lender accountability, returns reconciliation |
| **Wastage Ledger** | `feature/waste/` | Vial breakage, cold-chain failure, expired vial write-offs |
| **Consultation & Rx** | `feature/consultation/` | Doctor clinical consultation, vital signs, prescriptions, symptoms |
| **Reminders (Due/Overdue)**| `feature/reminder/` | Automated calculation of upcoming, due, and missed vaccine milestones |
| **Personal Reminders** | `feature/personalreminder/` | Custom follow-up alerts, patient-specific tasks, and notifications |
| **Daily Clinic Dashboard** | `feature/dashboard/` | Today's patient queue, appointments, vaccination counts, clinic metrics |
| **Doctor Availability** | `feature/doctor/` | Weekly schedule templates, holiday and exception slots |
| **Financial Ledger** | `feature/finance/` | Fee collection, daily income, clinic expenses, server-allocated receipts |
| **Clinic Profile** | `feature/profile/` | Clinic identity, registration numbers, header/footer for printable receipts |
| **Staff Administration** | `feature/staff/` | Role-based staff access (Doctor, Nurse, Receptionist, Admin), PIN auth |
| **Audit Logs** | `feature/audit/` | Tamper-evident mutation logging, who edited what, retention pruning |
| **Search Engine** | `feature/search/` | Unified search across patients, guardians, phones, and vaccines |
| **Clinic Analytics** | `feature/statistics/` | Vaccination uptake trends, revenue summaries, PDF export |
| **Sync Manager** | `feature/sync/` | Sync queue triage, conflict resolution, offline queue status |
| **Security & Auth** | `feature/auth/`, `core/security/` | Device binding, biometric lock, Supabase JWT sessions, RLS validation |
| **Backup & Disaster Recovery** | `feature/settings/`, `data/backup/` | AES-256-GCM `.nccb` containers, Cloudflare R2 cloud sync |
| **App Updates & Widgets** | `feature/update/`, `feature/widget/` | In-app APK updates from GitHub Releases, Android home-screen glance widget |

---

## 3. Data Integrity & Trust Boundaries

The clinic stores Protected Health Information (PHI) and financial records. The following principles govern all data operations:
- **Zero Data Loss**: Offline changes are staged in an encrypted local database (`AppDatabase`, SQLCipher) before syncing.
- **Atomic Multi-Table Transactions**: Administering a vaccine atomically creates a `VisitEntity`, updates `ReminderEntity`, inserts `VaccinationItemEntity`, decrements `VaccineBatchEntity`, logs `InventoryTransactionEntity`, and queues `SyncQueueEntity`.
- **Soft-Delete with Retention**: Clinical and patient records are soft-deleted with business audit trails to preserve historical immunization records.
- **Idempotent Synchronization**: Remote mutations carry client-generated UUIDs and version timestamps to prevent duplicate rows during network retries.
