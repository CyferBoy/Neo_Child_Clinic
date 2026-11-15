---
trigger: glob
globs: "**/data/local/**, **/schemas/**, **/AppDatabase.kt"
---

# Database & Schema Integrity Rules

When touching Room entities, DAOs, or database schema:

1. **Schema Versioning Discipline**:
   - The current Room schema version is **v34**.
   - Any schema modification requires:
     1. Incrementing `version = 35` in `AppDatabase.kt`.
     2. Exporting `35.json` to `app/schemas/com.neochildclinic.data.local.database.AppDatabase/`.
     3. Implementing an explicit `Migration(34, 35)` with strict SQL DDL.
     4. Adding the migration to `AppDatabase.MIGRATION_34_35`.
2. **Never Edit Released Migrations**:
   - Historical migrations (`MIGRATION_1_2` through `MIGRATION_33_34`) must NEVER be modified in place.
3. **Foreign Keys and Cascades**:
   - Enforce SQLite foreign keys with appropriate cascade or restrict rules.
   - Child records (e.g. `VaccinationItemEntity`) must reference their parent (`VisitEntity`).
4. **Soft-Delete Filtering**:
   - Queries retrieving active records must filter by `deleted_at IS NULL` or `is_deleted = 0`.
