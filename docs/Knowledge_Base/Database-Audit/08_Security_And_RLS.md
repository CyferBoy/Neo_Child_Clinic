# Security and Supabase RLS

## 1. Authentication and role source

- Supabase Auth is the identity provider. The client reads the role from `app_metadata.role` (`CurrentUserProvider`), never from `user_metadata` (`AuthViewModel` comment).
- `profiles` is business/display data; `profiles.role` is not the authorization authority.
- RLS helper `public.is_active_staff()` checks an authenticated `auth.uid()` with `profiles.is_active = true` and `profiles.is_deleted = false`.
- Deactivating a profile therefore denies RLS immediately; the JWT remains valid but the helper fails.

## 2. RLS matrix (as written in migrations)

| Table group | SELECT | INSERT | UPDATE | DELETE | Notes |
| --- | --- | --- | --- | --- | --- |
| patients, patient_visits, patient_notes, vaccination_items, vaccines, vaccine_batches, inventory_transactions, waste_records, borrow_records, borrow_returns, reminders, consultations, consultation_todos, vaccination_todos, personal_vaccine_reminders, finance_transactions | any active staff | any active staff | any active staff | any active staff | `20260929000000_security_baseline.sql:40-88` |
| audit_logs | any active staff | any active staff | **denied** | **denied** | append-only; explicit policy rebuild at lines 91-104 |
| expenses | active staff | active staff | **admin/doctor only** | **admin/doctor only** | `20261013120000_role_policy_carveouts.sql:27-71`; blanket policies dropped |
| doctor_weekly_slots, doctor_slot_exceptions | active staff | owner doctor or admin | owner doctor or admin | owner doctor or admin | carveouts lines 73-119; blanket policies dropped |
| profiles | active staff | denied (normal client) | self only (trigger protects privileged fields) | denied | baseline + `20261013_profiles_privileged_fields_guard.sql` |
| user_devices | own rows | active staff own | active staff own (self-reactivation policies dropped) | own rows | carveouts lines 121-135 |
| receipt_sequences | denied | denied | denied | denied | only SECURITY DEFINER receipt function |
| api_write_rate_limits | denied | denied | denied | denied | only SECURITY DEFINER trigger |
| todo_notification_events | denied | denied | denied | denied | RLS forced, no client policy; SECURITY DEFINER trigger writes |
| storage `patient-docs` | any active staff | any active staff | any active staff | any active staff | private bucket, no per-patient path scoping (`20261016:50-62`) |

`FORCE ROW LEVEL SECURITY` is used for `todo_notification_events`; other tables use ordinary RLS.

## 3. Role carve-outs that do exist

- **Expenses:** update/delete require `app_metadata.role in ('admin','doctor')`.
- **Doctor schedules:** write requires `doctor_id = auth.uid()` or admin.
- **Profiles:** non-admin self-updates cannot change `id`, `role`, `is_active`, `is_deleted`, `deleted_by`, `deleted_at`, or `email` (trigger).
- **Audit logs:** client can insert and read, not alter/delete.
- **Write rate limit:** 600 client writes/minute/user; service-role bypass.
- **Storage:** bucket is private; access requires active staff.
- **Staff provisioning:** `manage-staff` verifies the caller's `app_metadata.role === 'admin'`, active profile, and uses the service role server-side.

## 4. What is not enforced in the database

The UI exposes role gates such as:

- `PatientDetailsScreen.kt:48-50` — edit/delete visible only to admin/doctor.
- `PatientListScreen.kt:58-59` — same.
- `SyncScreen.kt:292-317` — failed-item URL/header diagnostics are admin-only (retry itself is not).
- `AppDrawer.kt:128,138` — Doctor Timings / Manage Staff sections gated by role.
- `ExpenseListViewModel.kt:71` — expense management admin/doctor.
- `WeeklyDoctorSlotsViewModel.kt:83,115` — schedule management admin/own doctor.

Only expenses, doctor schedules, profiles and device rows have matching server restrictions. **Patients, visits, vaccination items, reminders, consultations, todos, personal reminders, finance transactions, vaccines, batches and the stock ledger remain writable by every active staff role**, including `receptionist`, `nurse` and `inventory_manager`.

Direct PostgREST examples that succeed for any active staff token:

```http
DELETE /rest/v1/patients?id=eq.<uuid>
DELETE /rest/v1/finance_transactions?id=eq.<uuid>
PATCH  /rest/v1/patient_visits?id=eq.<uuid>   {"total_paid": 0}
DELETE /rest/v1/vaccine_batches?id=eq.<batchId>
```

No app code is required; the anon key is in the APK and a normal login yields an authenticated token. This is DBA-08.

## 5. Sensitive-data exposure

- RLS filters rows by **active staff**, not by clinic or assigned patient. Every active staff member can read every patient, clinical note, finance row and staff profile. This matches the single-clinic product model but means “receptionist” and “nurse” can read financial and clinical data.
- `patient-docs` storage objects are all readable/writable by any active staff; there is no path convention enforced in the policy.
- Audit logs contain patient ids, entity ids, old/new values and staff labels; they are readable by all active staff.
- Backups are encrypted `.nccb` files. Cloud backups go through the Cloudflare Worker/R2 backend (`CloudBackupApi`), not Supabase Storage; Worker auth, retention and bucket policy were not verified here.

## 6. Session and local security

- Room is encrypted with SQLCipher and a Keystore-backed passphrase (`AppDatabase.kt:442-460`). If the Keystore key is unavailable, the app fails closed rather than opening plaintext.
- Logout is blocked while unacknowledged sync items exist; after confirmation the app clears memory caches and `clearAllTables()`.
- Biometric lock exists for app entry (`BiometricLockManager`); it is a UI gate, not a database key factor.
- Device tokens are stored/updated directly in `user_devices`; no queue.

## 7. Findings

| ID | Severity | Finding |
| --- | --- | --- |
| DBA-08 | S2 High | Clinical/finance tables grant full CRUD to every active staff; admin/doctor UI gates are not mirrored in RLS. |
| DBA-15 | S3 Medium | Audit history is remote-only; offline the screen cannot show even locally staged audit rows. |
| — | Positive | Audit append-only, profile privilege trigger, doctor/expense carve-outs, receipt sequence lockdown, write rate limit, private storage bucket, encrypted Room, role from `app_metadata`. |

## 8. Live verification required

The policy state above is reconstructed from migrations. It does not prove:

- that every migration was applied to the production project;
- that no out-of-band policies/grants exist;
- the Cloudflare backup Worker's auth/retention/R2 configuration;
- whether `profiles` rows all have correct `is_active`/`is_deleted` flags.

Read-only verification queries are in `15_Unknowns_And_Live_DB_Gaps.md`.
