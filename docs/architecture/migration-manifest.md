# Neo Child Clinic — Restructuring Migration Manifest

**Execution Date:** November 3, 2026  
**Scope:** Complete physical repository structure reorganization, Kotlin package alignment, architectural boundary enforcement, documentation consolidation, and build verification.

---

## 1. Summary of Changes

- **Source Code Physically Moved & Reorganized**:
  - `DeviceRepository.kt`: Moved from `feature/profile/domain/repository/` to `feature/auth/domain/repository/` (aligning with `DeviceRepositoryImpl` in `feature/auth/data/`).
  - `AppDrawer.kt`: Moved from `feature/dashboard/presentation/component/` to `app/` (elevating the global clinic navigation drawer to top-level app scope and removing the misplaced `component/` directory).
  - `AuditLogDialog.kt`: Extracted from `core/ui/Dialogs.kt` into `feature/audit/presentation/` (eliminating an illegal inverted dependency from `core` to `feature/audit`).
  - `DoctorDropdowns.kt`: Extracted from `core/ui/Dropdowns.kt` into `feature/doctor/presentation/` (`SlotsUiState`, `GetAvailableSlotsUseCase.loadUiState`, `AvailableSlotDropdown`, and `DoctorDropdown` relocated to `feature.doctor.presentation`, eliminating inverted dependency from `core` to `feature/doctor`).
- **Physical Documentation & Design Moves**:
  - `docs/Screenshot/*` (6 image files) physically moved via Git to `design/assets/screenshots/`. Legacy directory completely removed.
  - `docs/bug-audit/*` (34 files including SQL subfolder) physically moved via Git to `docs/quality/bug-audit/`. Legacy directory completely removed.
  - `docs/superpowers/*` (specs and implementation plans) physically moved via Git to `docs/architecture/specs/`. Legacy directory completely removed.
- **Root Clutter Cleaned**:
  - Deleted transient V8 heap dumps: `tui.heapsnapshot`, `server.heapsnapshot`.
  - Deleted 70MB non-repository binary: `docs/bug-audit/BitChord-1.8-linux-amd64.deb`.
- **Knowledge Base & Strict Verification**:
  - Updated all citations in `docs/Knowledge_Base/Error Handling.md` and `docs/Knowledge_Base/MVVM.md` to track relocated components.
  - `python3 scripts/validate_docs.py --strict` verified: 0 errors across 66 notes, 38 mermaid blocks, 1,064 citations.
  - `./gradlew :app:compileDebugKotlin` verified: BUILD SUCCESSFUL.
  - `./gradlew :app:testDebugUnitTest` verified: 344/344 tests passing.
  - `./gradlew :app:assembleDebug` verified: BUILD SUCCESSFUL.

---

## 2. Physical File Operations Manifest

| Original Path | Actual New Path | Operation | Verification |
|---|---|---|---|
| `app/.../feature/profile/domain/repository/DeviceRepository.kt` | `app/.../feature/auth/domain/repository/DeviceRepository.kt` | Physically moved; package updated to `com.neochildclinic.feature.auth.domain.repository` | Verified in `RepositoryModule`, `MainActivity`, `AuthViewModel`, `NeoChildFirebaseMessagingService`, `DeviceRepositoryImpl` |
| `app/.../feature/dashboard/presentation/component/AppDrawer.kt` | `app/.../app/AppDrawer.kt` | Physically moved; package updated to `com.neochildclinic.app`; legacy `component/` dir removed | Verified in `DashboardScreen`, `Navigation`, `MainActivity` |
| `app/.../core/ui/Dialogs.kt` (AuditLogDialog) | `app/.../feature/audit/presentation/AuditLogDialog.kt` | Extracted & moved to `com.neochildclinic.feature.audit.presentation`; cleaned `Dialogs.kt` | Inverted dependency eliminated; compiled clean |
| `app/.../core/ui/Dropdowns.kt` (Doctor components) | `app/.../feature/doctor/presentation/DoctorDropdowns.kt` | Extracted & moved `SlotsUiState`, `AvailableSlotDropdown`, `DoctorDropdown` to `feature.doctor.presentation` | Inverted dependency eliminated; verified in Consultation, Dashboard, Vaccination VMs & Screens |
| `docs/Screenshot/app drawer.png` | `design/assets/screenshots/app drawer.png` | Git moved | File integrity confirmed |
| `docs/Screenshot/borrow vaccine.png` | `design/assets/screenshots/borrow vaccine.png` | Git moved | File integrity confirmed |
| `docs/Screenshot/dashboard.png` | `design/assets/screenshots/dashboard.png` | Git moved | File integrity confirmed |
| `docs/Screenshot/due vaccination.png` | `design/assets/screenshots/due vaccination.png` | Git moved | File integrity confirmed |
| `docs/Screenshot/inventory.png` | `design/assets/screenshots/inventory.png` | Git moved | File integrity confirmed |
| `docs/Screenshot/patient details.png` | `design/assets/screenshots/patient details.png` | Git moved | File integrity confirmed |
| `docs/bug-audit/**` (34 files) | `docs/quality/bug-audit/**` | Git moved; legacy `docs/bug-audit/` directory removed | Git tree verified; zero duplicates remaining |
| `docs/superpowers/**` (3 files) | `docs/architecture/specs/**` | Git moved; legacy `docs/superpowers/` directory removed | Git tree verified; zero duplicates remaining |
| `tui.heapsnapshot` | N/A | Deleted | Removed transient heap dump |
| `server.heapsnapshot` | N/A | Deleted | Removed transient heap dump |
| `docs/bug-audit/BitChord-1.8-linux-amd64.deb` | N/A | Deleted | Removed 70MB misplaced deb package |
| `CONTRIBUTING.md` | `CONTRIBUTING.md` | Created | Contribution guide added |
| `docs/architecture/repository-inventory.md` | `docs/architecture/repository-inventory.md` | Created | Repository inventory reference |
| `docs/architecture/architecture-assessment.md` | `docs/architecture/architecture-assessment.md` | Created | Architecture assessment document |
| `docs/architecture/folder-migration-plan.md` | `docs/architecture/folder-migration-plan.md` | Created | Migration plan specification |
| `docs/architecture/where-things-belong.md` | `docs/architecture/where-things-belong.md` | Created | Directory placement guidelines |
| `docs/architecture/offline-first-sync.md` | `docs/architecture/offline-first-sync.md` | Created | Offline sync architecture documentation |
| `docs/architecture/cache-architecture.md` | `docs/architecture/cache-architecture.md` | Created | Cache architecture documentation |
| `docs/architecture/verification-report.md` | `docs/architecture/verification-report.md` | Created | Build & test verification report |
| `docs/getting-started/onboarding.md` | `docs/getting-started/onboarding.md` | Created | Developer onboarding guide |
| `docs/features/overview.md` | `docs/features/overview.md` | Created | 20-feature architecture matrix |
| `docs/database/schema-overview.md` | `docs/database/schema-overview.md` | Created | Database schema overview |
| `docs/operations/backup-restore.md` | `docs/operations/backup-restore.md` | Created | Backup & restore runbook |
| `docs/operations/release-management.md` | `docs/operations/release-management.md` | Created | Release management runbook |
| `docs/quality/security-audit-2026-10-13.md` | `docs/quality/security-audit-2026-10-13.md` | Created | Historical security audit |
| `docs/quality/audit-report-2026-10-16.md` | `docs/quality/audit-report-2026-10-16.md` | Created | Historical code audit |

---

## 3. Quantitative Summary

- **Number of files physically moved / renamed**: 45 files (43 docs/design/specs via git rename + 2 Kotlin source files physically relocated with package realignment)
- **Number of files created**: 16 files (14 architecture/operations/getting-started docs + 2 extracted Kotlin presentation files eliminating inverted architectural dependencies)
- **Number of files modified**: 23 files (13 Kotlin callers/DI bindings updated + 10 root/KB documentation files updated for citation accuracy and backward compatibility)
- **Number of files deleted**: 5 files (`tui.heapsnapshot`, `server.heapsnapshot`, `BitChord-1.8-linux-amd64.deb`, `DeviceRepository.kt` at old path, `AppDrawer.kt` at old path)
