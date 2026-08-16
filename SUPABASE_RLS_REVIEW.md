# Supabase / RLS review — 2026-08-16

## Applied in this revision

- `profiles`: RLS enabled; users can read/update their own non-deleted profile; admins can read all profiles.
- `profiles`: role, active state, delete state, email and identity fields cannot be escalated/changed by a normal user.
- Staff creation, role changes, activation/deactivation, profile edits and soft-delete are moved to the `manage-staff` Edge Function using the service role.
- `user_devices`: RLS enabled; users can only read/write their own device rows.
- `user_devices.fcm_token`: unique index added and client registration changed to explicit update-or-insert.
- `created_at` and `updated_at` are added to existing public tables where absent; `updated_at` is maintained by a trigger.
- `created_by` / `updated_by` are added as nullable audit fields to business tables (excluding local cache/sync tables).
- `profiles` soft-delete fields: `is_deleted`, `deleted_at`, `deleted_by`.

## Project tables requiring role-aware RLS review

The Android project accesses these public tables:

- profiles
- user_devices
- patients
- patient_visits
- consultations
- vaccination_items
- vaccines
- vaccine_batches
- inventory_transactions
- inventory_deductions
- waste_records
- borrow_records
- finance_transactions
- patient_notes
- reminders
- audit_logs

The application also references Supabase Storage bucket `patient-docs`.

## Recommended role policy matrix

- Admin: full business-data access.
- Doctor: patient/medical/vaccination/billing/settings access according to the application role matrix; inventory read-only.
- Receptionist: patient registration, vaccination workflow and billing; no inventory administration.
- Nurse: patient/vaccination workflow; no billing or inventory administration.
- Inventory Manager: inventory and inventory reports; no patient/medical/billing administration.

Before enabling write policies on these remaining tables in production, verify each table's exact workflow and foreign keys against the deployed Supabase schema. A permissive `authenticated` policy is intentionally NOT added as a shortcut because it would defeat the role model.

## Edge Function security

`manage-staff` now:
- validates the caller's Supabase access token;
- checks the caller's active, non-deleted profile;
- requires role `admin`;
- validates target staff IDs;
- prevents an admin from disabling/deleting their own account;
- uses the service role only server-side;
- soft-deletes staff instead of deleting the Auth account;
- deactivates the target's device tokens.

The Android APK must never contain `SUPABASE_SERVICE_ROLE_KEY`.

## FCM

`user_devices` is user-owned. Notification Edge Functions use the service role because they send notifications across users. Firebase service-account credentials remain Edge Function secrets.

## Biometric

The biometric path remains Android Keystore + CryptoObject + AES-GCM and supports strong biometrics and device credentials. Account-password fallback is implemented separately through Supabase Auth and is never persisted locally.
