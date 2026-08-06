# Task: Fix Audit Log Crash and Supabase Sync Mapping

- [x] Update `PatientUtils.kt` with ISO timestamp format support
- [x] Fix UI crashes by replacing `Date(String)` with `PatientUtils.parseDate`
    - [x] `FullAuditLogScreen.kt`
    - [x] `Dialogs.kt`
    - [x] `PatientInfoComponents.kt`
- [x] Update Data Models with `@SerialName` for Supabase compatibility
    - [x] `Patient.kt`
    - [x] `PatientEntity.kt`
    - [x] `VaccinationEntity.kt` (VisitEntity)
- [x] Verification
    - [x] Add unit test for `PatientUtils.parseDate`
    - [x] Build and verify no regressions
