# Walkthrough - Idempotent Database Schema & Policies

I have updated the comprehensive SQL script to be **idempotent**, meaning it can be run multiple times on your Supabase database without errors or data duplication. It safely handles both fresh installations and updates to existing environments.

## Key Enhancements for Existing Databases

### 1. Incremental Table Updates
Instead of a simple `CREATE TABLE`, the script now uses:
- `CREATE TABLE IF NOT EXISTS` for all 14 tables.
- `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` for every single column. This ensures that if you already have the tables but they are missing new fields (like `visit_type` or `employee_id`), the script will add them without touching your existing data.

### 2. Force-Refresh Policies
To ensure your security rules are always the latest version:
- Every policy is preceded by `DROP POLICY IF EXISTS`.
- This wipes the old rule and replaces it with the new one defined in the script, ensuring no "Policy already exists" errors.

### 3. Reusable Logic
- **Triggers**: Uses `DROP TRIGGER IF EXISTS` and `CREATE OR REPLACE FUNCTION` to ensure the stock management logic is always current.
- **RBAC**: The `get_my_role()` helper is replaced if it exists, ensuring the role detection logic is up to date.

## Fix: Supabase Sync Schema Mismatch

The app was encountering errors during background synchronization because it was attempting to upload domain models (e.g., `Vaccination`) directly to Supabase. These models contained computed fields and nested lists (like `items` and `followUps`) that did not exist as columns in the `patient_visits` table.

- **Resolved**: Refactored `SyncRepositoryImpl` and `VaccinationRepositoryImpl` to use Room Entity classes (`VisitEntity`, `PatientEntity`, etc.) for cloud communication.
- **Benefit**: Ensures a strict 1:1 mapping between the app and the database schema, resolving "column not found" errors and improving sync stability.

## Fix: SQL Timestamp Syntax Error

The app was encountering an `invalid input syntax for type timestamp with time zone` error because it was sending user-friendly date strings (e.g., "6 Aug 2026") to PostgreSQL columns defined as `TIMESTAMPTZ` or `DATE`.

- **Resolved**: Updated the master SQL script to use `TEXT` for all clinical date fields (`dob`, `date_given`, `expiry_date`, etc.). This allows the app to continue using its preferred string format for dates while still benefitting from cloud synchronization.
- **Improved Null Handling**: Refactored Room entities to use `null` for empty timestamps instead of `""`, allowing Supabase to correctly apply its `now()` default values.

## Artifacts Generated

- [Idempotent Full Project SQL Script](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/.artifacts/f21d0dfd-5450-453c-b8f4-c37c7038ad45/full_project_schema.sql.artifact.md): The updated master script.

## Verification
- **Safety Check**: Verified that `ADD COLUMN IF NOT EXISTS` syntax is correct for PostgreSQL 9.4+.
- **Policy Check**: Verified that the policy names match between the `DROP` and `CREATE` statements.
- **Dependency Check**: Verified that foreign keys and indexes use the same idempotent pattern.
