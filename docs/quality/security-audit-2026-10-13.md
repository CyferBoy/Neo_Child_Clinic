# 🔐 Security Audit Report (2026-10-13)

**Target:** Neo Child Clinic / vaccine_manager_app  
**Date:** October 13, 2026  
**Stack Detected:** Android (Kotlin 379 files, Compose, SQLCipher, supabase-kt) + Supabase (Postgres RLS, 2 Edge Functions) + Cloudflare Worker (R2 + D1) + GitHub Actions CI  

---

## Executive Summary

Posture is good for a project of this size: server-side RLS baseline with a defense-in-depth "enable RLS on every table" loop, service-role credentials kept server-side, SQLCipher + Android Keystore for local data, AES-256-GCM backups with PBKDF2-SHA256 (210k), `allowBackup=false`, and ownership-checked backup APIs. **No critical finding.**

---

## 🟠 High Findings

### H1: `notify-update` fails open if the secret env var is unset
- **Location:** `supabase/functions/notify-update/index.ts:17-24`
- **Issue:** `const secret = Deno.env.get('UPDATE_NOTIFIER_SECRET')` — if the secret is missing/renamed in the deployed function, `secret` is `undefined`, the comparison becomes `authHeader !== "Bearer undefined"`, and sending literally `Authorization: Bearer undefined` passes.
- **Resolution (2026-10-13):** `notify-update/index.ts` rejects any caller if `UPDATE_NOTIFIER_SECRET` is unset.

---

## 🟡 Medium Findings

### M1: `profiles_update_own` policy survives security baseline
- **Resolution (2026-10-13):** `20261013_profiles_privileged_fields_guard.sql` guards `deleted_at` and sensitive fields with trigger-based allowlists.

### M2: Staff password strength enforcement
- **Resolution (2026-10-13):** `manage-staff/index.ts` enforces 12-character minimum passwords before invoking `createUser`.

### M3: Cloudflare Worker confirm cross-account overwrite
- **Resolution (2026-10-13):** `backup-worker/src/index.ts` verifies ownership before confirm upserts, returning 404 for foreign account IDs.

### M4: PBKDF2 iteration count bounding
- **Resolution (2026-10-13):** `BackupCrypto.kt` clamps iterations between 10,000 and 1,000,000, preventing denial-of-service on malicious container input.

---

## ✅ Verified Security Controls

- **Row Level Security**: Comprehensive policies across all 20+ tables gated on `is_active_staff()`.
- **Append-Only Auditing**: Client-side cannot update or delete `audit_logs`; server-side trigger stamps `created_by` authoritatively.
- **Role Authority**: Claims read strictly from `app_metadata.role`, preventing client manipulation via `user_metadata`.
- **Database Write Rate Limiting**: Enforced via server-side trigger (600 mutations/user/min).
- **Zero Cloud Credentials in Client**: Service role keys reside exclusively in server environments.
